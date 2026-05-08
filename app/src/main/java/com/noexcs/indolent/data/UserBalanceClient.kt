package com.noexcs.indolent.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class BalanceInfo(
    val currency: String,
    val totalBalance: String,
    val grantedBalance: String,
    val toppedUpBalance: String
)

data class UserBalanceResponse(
    val isAvailable: Boolean,
    val balanceInfos: List<BalanceInfo>
)

suspend fun fetchUserBalance(baseUrl: String, apiKey: String): UserBalanceResponse = withContext(Dispatchers.IO) {
    val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val request = Request.Builder()
        .url("$baseUrl/user/balance")
        .addHeader("Authorization", "Bearer $apiKey")
        .addHeader("Accept", "application/json")
        .build()

    val response = http.newCall(request).execute()
    if (!response.isSuccessful) {
        throw IOException("HTTP ${response.code}: ${response.message}")
    }

    val json = JSONObject(response.body?.string() ?: throw IOException("Empty response"))
    val infos = json.getJSONArray("balance_infos")
    val balanceInfos = (0 until infos.length()).map { i ->
        val info = infos.getJSONObject(i)
        BalanceInfo(
            currency = info.getString("currency"),
            totalBalance = info.getString("total_balance"),
            grantedBalance = info.getString("granted_balance"),
            toppedUpBalance = info.getString("topped_up_balance")
        )
    }

    UserBalanceResponse(
        isAvailable = json.getBoolean("is_available"),
        balanceInfos = balanceInfos
    )
}
