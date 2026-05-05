package com.noexcs.indolent.agent.tools.systeminfo

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NetworkStatusTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext

    override val name = "get_network_status"
    override val description = """
        Read current network connectivity status of this Android device.

        Returns:
        - Whether the device is online (connected to internet)
        - Transport type: WiFi, cellular (with generation 2G-5G), ethernet, VPN
        - WiFi SSID, BSSID, signal strength (RSSI in dBm), link speed (Mbps), frequency band
        - Cellular network type (LTE/NR/UMTS/etc), operator name, signal level
        - Captive portal detection (whether WiFi login page is blocking)
        - Whether metered connection (data limit / hotspot)
        - Upstream/downstream bandwidth estimate (when available)
        - All available networks and their capabilities

        Use this before making network-heavy API calls or to report connectivity to the user.
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val cm =
                appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            // Active network
            val activeNetwork: Network? = cm.activeNetwork
            val caps: NetworkCapabilities? = if (activeNetwork != null) {
                cm.getNetworkCapabilities(activeNetwork)
            } else null

            val isOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            val isValidated =
                caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

            buildString {
                appendLine("online: $isOnline")
                appendLine("validated: $isValidated")

                if (caps == null) {
                    appendLine("network: none (disconnected)")
                    return@buildString
                }

                // Transport type
                val transports = mutableListOf<String>()
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) transports += "WiFi"
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) transports += "cellular"
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) transports += "ethernet"
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) transports += "VPN"
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) transports += "bluetooth"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_LOWPAN)) transports += "lowpan"
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_USB)) transports += "USB"
                }
                appendLine("transport: ${transports.joinToString(", ")}")

                // Metered
                val metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                appendLine("metered: $metered")

                // Bandwidth estimate
                val downBw = caps.linkDownstreamBandwidthKbps
                val upBw = caps.linkUpstreamBandwidthKbps
                if (downBw > 0) appendLine("downstream: ${formatBw(downBw)}")
                if (upBw > 0) appendLine("upstream: ${formatBw(upBw)}")

                // Captive portal
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)) {
                    appendLine("captivePortal: true (needs login)")
                }

                // WiFi details (requires ACCESS_WIFI_STATE permission)
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    try {
                        val wm =
                            appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                        val info: WifiInfo? = wm?.connectionInfo
                        if (info != null && info.ssid != "<unknown ssid>") {
                            appendLine("wifiSSID: ${info.ssid}")
                            appendLine("wifiBSSID: ${info.bssid}")
                            appendLine("wifiRSSI: ${info.rssi}dBm")
                            appendLine(
                                "wifiSignal: ${
                                    WifiManager.calculateSignalLevel(
                                        info.rssi,
                                        5
                                    )
                                } / 5"
                            )
                            if (info.linkSpeed > 0) appendLine("wifiSpeed: ${info.linkSpeed}Mbps")
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                val freq = info.frequency
                                if (freq > 0) {
                                    val band =
                                        if (freq > 5000) "5GHz" else if (freq > 2400) "2.4GHz" else if (freq >= 5925) "6GHz" else "${freq}MHz"
                                    appendLine("wifiBand: $band (${freq}MHz)")
                                }
                            }
                        }
                    } catch (e: SecurityException) {
                        Lumberjack.e(
                            "NetworkStatusTool",
                            "Missing nearby devices permission for WiFi details",
                            e
                        )
                        appendLine("wifiNote: WiFi 详情不可用（缺少「附近的设备」权限，不影响基础网络状态）")
                    }
                }

                // Cellular details
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    val tm =
                        appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                    if (tm != null) {
                        val networkType = when {
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> tm.dataNetworkType
                            else -> @Suppress("DEPRECATION") tm.networkType
                        }
                        val gen = cellularGeneration(networkType)
                        if (gen.isNotEmpty()) appendLine("cellularType: $gen")
                        val operator = tm.networkOperatorName
                        if (operator.isNotBlank()) appendLine("operator: $operator")
                    }
                }

                // All networks summary
                val allNetworks = cm.allNetworks
                if (allNetworks != null && allNetworks.size > 1) {
                    appendLine("otherNetworks: ${allNetworks.size - 1}")
                }
            }
        } catch (e: Exception) {
            Lumberjack.e("NetworkStatusTool", "Error reading network status", e)
            "Error reading network status: ${e.message}"
        }
    }

    private fun formatBw(kbps: Int): String = when {
        kbps >= 1_000_000 -> "${kbps / 1_000_000}Gbps"
        kbps >= 1_000 -> "${kbps / 1_000}Mbps"
        else -> "${kbps}Kbps"
    }

    private fun cellularGeneration(type: Int): String = when (type) {
        TelephonyManager.NETWORK_TYPE_NR -> "5G (NR)"
        TelephonyManager.NETWORK_TYPE_LTE -> "4G (LTE)"
        TelephonyManager.NETWORK_TYPE_HSPAP -> "3.5G (HSPA+)"
        TelephonyManager.NETWORK_TYPE_HSDPA -> "3G (HSDPA)"
        TelephonyManager.NETWORK_TYPE_HSUPA -> "3G (HSUPA)"
        TelephonyManager.NETWORK_TYPE_HSPA -> "3G (HSPA)"
        TelephonyManager.NETWORK_TYPE_UMTS -> "3G (UMTS)"
        TelephonyManager.NETWORK_TYPE_EVDO_0 -> "3G (EV-DO Rev0)"
        TelephonyManager.NETWORK_TYPE_EVDO_A -> "3G (EV-DO RevA)"
        TelephonyManager.NETWORK_TYPE_EVDO_B -> "3G (EV-DO RevB)"
        TelephonyManager.NETWORK_TYPE_EHRPD -> "3G (eHRPD)"
        TelephonyManager.NETWORK_TYPE_1xRTT -> "2G (CDMA 1xRTT)"
        TelephonyManager.NETWORK_TYPE_CDMA -> "2G (CDMA)"
        TelephonyManager.NETWORK_TYPE_EDGE -> "2G (EDGE)"
        TelephonyManager.NETWORK_TYPE_GPRS -> "2G (GPRS)"
        TelephonyManager.NETWORK_TYPE_GSM -> "2G (GSM)"
        TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G (TD-SCDMA)"
        TelephonyManager.NETWORK_TYPE_IWLAN -> "WiFi-calling (IWLAN)"
        else -> "unknown ($type)"
    }
}