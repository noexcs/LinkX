package com.noexcs.indolent.agent.tools.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

object SensorUtils {

    private const val HIGH_SAMPLE_SUMMARY_THRESHOLD = 50

    // Non-standard Android sensor types used by some devices
    const val TYPE_DEVICE_ORIENTATION = 27
    const val TYPE_TILT_DETECTOR = 22

    private val NAME_ALIASES = mapOf(
        setOf("light", "ambient_light", "brightness") to Sensor.TYPE_LIGHT,
        setOf("pressure", "barometer", "atmospheric") to Sensor.TYPE_PRESSURE,
        setOf("temperature", "ambient_temp", "thermometer") to Sensor.TYPE_AMBIENT_TEMPERATURE,
        setOf("humidity", "relative_humidity") to Sensor.TYPE_RELATIVE_HUMIDITY,
        setOf("accelerometer", "accel", "acceleration") to Sensor.TYPE_ACCELEROMETER,
        setOf("gyroscope", "gyro", "angular") to Sensor.TYPE_GYROSCOPE,
        setOf("magnetometer", "magnetic", "magnetic_field", "compass") to Sensor.TYPE_MAGNETIC_FIELD,
        setOf("proximity", "prox", "distance") to Sensor.TYPE_PROXIMITY,
        setOf("step_counter", "steps", "pedometer") to Sensor.TYPE_STEP_COUNTER,
        setOf("step_detector", "step_detect") to Sensor.TYPE_STEP_DETECTOR,
        setOf("rotation", "orientation", "rotation_vector", "attitude") to Sensor.TYPE_ROTATION_VECTOR,
        setOf("game_rotation", "game_rotation_vector") to Sensor.TYPE_GAME_ROTATION_VECTOR,
        setOf("gravity") to Sensor.TYPE_GRAVITY,
        setOf("linear_acceleration", "linear_accel") to Sensor.TYPE_LINEAR_ACCELERATION,
        setOf("device_orientation") to TYPE_DEVICE_ORIENTATION,
        setOf("tilt_detector") to TYPE_TILT_DETECTOR,
        setOf("significant_motion") to Sensor.TYPE_SIGNIFICANT_MOTION,
    )

    private val ALIAS_TO_TYPE: Map<String, Int> = run {
        val map = mutableMapOf<String, Int>()
        NAME_ALIASES.forEach { (aliases, type) ->
            aliases.forEach { alias -> map[alias] = type }
        }
        map
    }

    fun resolveSensorType(name: String): Int? = ALIAS_TO_TYPE[name.lowercase().trim()]

    fun getAllAliases(): List<String> = ALIAS_TO_TYPE.keys.toList().sorted()

    fun getTypeName(type: Int): String = when (type) {
        Sensor.TYPE_ACCELEROMETER -> "Accelerometer"
        Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> "Accelerometer (Uncalibrated)"
        Sensor.TYPE_MAGNETIC_FIELD -> "Magnetometer"
        Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "Magnetometer (Uncalibrated)"
        Sensor.TYPE_GYROSCOPE -> "Gyroscope"
        Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "Gyroscope (Uncalibrated)"
        Sensor.TYPE_LIGHT -> "Ambient Light"
        Sensor.TYPE_PRESSURE -> "Pressure"
        Sensor.TYPE_PROXIMITY -> "Proximity"
        Sensor.TYPE_GRAVITY -> "Gravity"
        Sensor.TYPE_LINEAR_ACCELERATION -> "Linear Acceleration"
        Sensor.TYPE_ROTATION_VECTOR -> "Rotation Vector"
        Sensor.TYPE_RELATIVE_HUMIDITY -> "Relative Humidity"
        Sensor.TYPE_AMBIENT_TEMPERATURE -> "Ambient Temperature"
        Sensor.TYPE_STEP_COUNTER -> "Step Counter"
        Sensor.TYPE_STEP_DETECTOR -> "Step Detector"
        Sensor.TYPE_GAME_ROTATION_VECTOR -> "Game Rotation Vector"
        Sensor.TYPE_SIGNIFICANT_MOTION -> "Significant Motion"
        TYPE_DEVICE_ORIENTATION -> "Device Orientation"
        TYPE_TILT_DETECTOR -> "Tilt Detector"
        else -> "Sensor (type=$type)"
    }

    fun getAccuracyLabel(accuracy: Int): String = when (accuracy) {
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "high"
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "medium"
        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "low"
        SensorManager.SENSOR_STATUS_UNRELIABLE -> "unreliable"
        else -> "unknown"
    }

    enum class SensorCategory(val label: String) {
        ENVIRONMENT("Environment"),
        MOTION("Motion"),
        POSITION("Position"),
        ACTIVITY("Activity"),
        OTHER("Other")
    }

    fun getCategory(type: Int): SensorCategory = when (type) {
        Sensor.TYPE_LIGHT, Sensor.TYPE_PRESSURE,
        Sensor.TYPE_AMBIENT_TEMPERATURE, Sensor.TYPE_RELATIVE_HUMIDITY -> SensorCategory.ENVIRONMENT
        Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_ACCELEROMETER_UNCALIBRATED,
        Sensor.TYPE_GYROSCOPE, Sensor.TYPE_GYROSCOPE_UNCALIBRATED,
        Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED,
        Sensor.TYPE_GRAVITY, Sensor.TYPE_LINEAR_ACCELERATION -> SensorCategory.MOTION
        Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR,
        Sensor.TYPE_PROXIMITY, TYPE_DEVICE_ORIENTATION,
        TYPE_TILT_DETECTOR -> SensorCategory.POSITION
        Sensor.TYPE_STEP_COUNTER, Sensor.TYPE_STEP_DETECTOR,
        Sensor.TYPE_SIGNIFICANT_MOTION -> SensorCategory.ACTIVITY
        else -> SensorCategory.OTHER
    }

    fun getSensorUnit(type: Int): String = when (type) {
        Sensor.TYPE_LIGHT -> "lux"
        Sensor.TYPE_PRESSURE -> "hPa"
        Sensor.TYPE_AMBIENT_TEMPERATURE -> "°C"
        Sensor.TYPE_RELATIVE_HUMIDITY -> "%"
        Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_ACCELEROMETER_UNCALIBRATED,
        Sensor.TYPE_GRAVITY, Sensor.TYPE_LINEAR_ACCELERATION -> "m/s²"
        Sensor.TYPE_GYROSCOPE, Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "rad/s"
        Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "µT"
        Sensor.TYPE_PROXIMITY -> "cm"
        Sensor.TYPE_STEP_COUNTER -> "steps"
        Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR -> "(unitless quaternion)"
        else -> ""
    }

    fun getDefaultDurationMs(type: Int): Long = when (type) {
        Sensor.TYPE_STEP_COUNTER, Sensor.TYPE_STEP_DETECTOR -> 0L
        Sensor.TYPE_LIGHT, Sensor.TYPE_PROXIMITY -> 300L
        else -> 500L
    }

    // #8: Enum value annotations for sensors that return coded integers
    private val DEVICE_ORIENTATION_LABELS = mapOf(
        0 to "unknown", 1 to "face_up", 2 to "face_down",
        3 to "vertical", 4 to "vertical_upside_down",
        5 to "left_side", 6 to "right_side"
    )

    private fun annotateEnumValue(type: Int, values: FloatArray): String {
        return when (type) {
            TYPE_DEVICE_ORIENTATION -> {
                val v = values[0].toInt()
                "${v.toFloat().toLong()} (${DEVICE_ORIENTATION_LABELS[v] ?: "unknown"})"
            }
            TYPE_TILT_DETECTOR -> {
                val v = values[0].toInt()
                if (v == 1) "1 (tilted)" else "${v.toFloat().toLong()} (not tilted)"
            }
            Sensor.TYPE_STEP_DETECTOR -> {
                val v = values[0].toInt()
                if (v == 1) "1 (step detected)" else "${v.toFloat().toLong()}"
            }
            else -> values.joinToString(", ") { formatValue(it) }
        }
    }

    // #6: Label the 6 values of uncalibrated sensors (first 3 = measurement, last 3 = bias/drift)
    private fun formatUncalibratedValues(type: Int, values: FloatArray): String {
        val f = { v: Float -> formatValue(v) }
        return when (type) {
            Sensor.TYPE_ACCELEROMETER_UNCALIBRATED ->
                "[x: ${f(values[0])}, y: ${f(values[1])}, z: ${f(values[2])} | bias_x: ${f(values[3])}, bias_y: ${f(values[4])}, bias_z: ${f(values[5])}]"
            Sensor.TYPE_GYROSCOPE_UNCALIBRATED ->
                "[x: ${f(values[0])}, y: ${f(values[1])}, z: ${f(values[2])} | drift_x: ${f(values[3])}, drift_y: ${f(values[4])}, drift_z: ${f(values[5])}]"
            Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED ->
                "[x: ${f(values[0])}, y: ${f(values[1])}, z: ${f(values[2])} | bias_x: ${f(values[3])}, bias_y: ${f(values[4])}, bias_z: ${f(values[5])}]"
            else -> values.joinToString(", ") { f(it) }
        }
    }

    private fun isUncalibratedType(type: Int): Boolean = type == Sensor.TYPE_ACCELEROMETER_UNCALIBRATED ||
            type == Sensor.TYPE_GYROSCOPE_UNCALIBRATED ||
            type == Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED

    // #7: Quaternion norm instead of RMS for rotation vectors
    private fun isQuaternionType(type: Int): Boolean = type == Sensor.TYPE_ROTATION_VECTOR ||
            type == Sensor.TYPE_GAME_ROTATION_VECTOR

    private fun quaternionNorm(values: FloatArray): Float {
        return sqrt(values.map { (it * it).toDouble() }.sum()).toFloat()
    }

    private fun isEnumType(type: Int): Boolean = type == TYPE_DEVICE_ORIENTATION ||
            type == TYPE_TILT_DETECTOR || type == Sensor.TYPE_STEP_DETECTOR

    enum class SensorState {
        NOT_AVAILABLE,
        NO_PERMISSION,
        IDLE,
        READY
    }

    fun getSensorState(sensor: Sensor?, readings: List<SensorReading>, hadSecurityException: Boolean = false): SensorState {
        if (hadSecurityException) return SensorState.NO_PERMISSION
        if (sensor == null) return SensorState.NOT_AVAILABLE
        return if (readings.isEmpty()) SensorState.IDLE else SensorState.READY
    }

    fun getStateLabel(state: SensorState): String = when (state) {
        SensorState.NOT_AVAILABLE -> "NOT_AVAILABLE — hardware not present on this device"
        SensorState.NO_PERMISSION -> "NO_PERMISSION — requires additional Android permission"
        SensorState.IDLE -> "IDLE — sensor present but no events received (try longer duration_ms, or sensor may be asleep)"
        SensorState.READY -> "READY"
    }

    data class SensorReading(
        val timestamp: Long,
        val accuracy: Int,
        val values: FloatArray
    ) {
        fun valuesCopy(): FloatArray = values.copyOf()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SensorReading) return false
            return timestamp == other.timestamp && accuracy == other.accuracy && values.contentEquals(other.values)
        }

        override fun hashCode(): Int = timestamp.hashCode() * 31 + accuracy.hashCode() * 31 + values.contentHashCode()
    }

    suspend fun collectSensorReadings(
        context: Context,
        sensor: Sensor,
        durationMs: Long
    ): List<SensorReading> = withContext(Dispatchers.IO) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val handlerThread = HandlerThread("sensor-reader")
        handlerThread.start()
        val handler = Handler(handlerThread.looper)
        val readings = mutableListOf<SensorReading>()

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == sensor.type) {
                    readings.add(SensorReading(event.timestamp, event.accuracy, event.values.copyOf()))
                }
            }
            override fun onAccuracyChanged(s: Sensor, accuracy: Int) {}
        }

        try {
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI, handler)
            if (durationMs > 0) {
                delay(durationMs)
            } else {
                delay(200)
            }
        } catch (e: Exception) {
            Lumberjack.e("SensorUtils", "Error collecting sensor data for ${sensor.name}", e)
        } finally {
            sensorManager.unregisterListener(listener)
            handlerThread.quitSafely()
        }

        readings.toList()
    }

    fun formatSensorInfo(sensor: Sensor): String = buildString {
        appendLine("Sensor: ${getTypeName(sensor.type)} (TYPE_${sensor.stringType})")
        appendLine("Vendor: ${sensor.vendor}")
        append("Power: ${sensor.power} mA")
        append(" | Max Range: ${sensor.maximumRange}")
        val unit = getSensorUnit(sensor.type)
        if (unit.isNotBlank()) append(" $unit")
        append(" | Resolution: ${sensor.resolution}")
        if (unit.isNotBlank()) append(" $unit")
        appendLine()
    }

    fun formatReadings(sensor: Sensor, readings: List<SensorReading>, forceSummary: Boolean = false): String {
        val state = getSensorState(sensor, readings)
        val stateTag = getStateLabel(state).split(" — ").first()

        return buildString {
            appendLine("Sensor: ${getTypeName(sensor.type)} (TYPE_${sensor.stringType}) — $stateTag")
            appendLine("Vendor: ${sensor.vendor}")
            append("Power: ${sensor.power} mA")
            append(" | Max Range: ${sensor.maximumRange}")
            val unit = getSensorUnit(sensor.type)
            if (unit.isNotBlank()) append(" $unit")
            append(" | Resolution: ${sensor.resolution}")
            if (unit.isNotBlank()) append(" $unit")
            // #11: warn if actual reading exceeds max range
            if (state == SensorState.READY && readings.first().values.size == 1) {
                val maxVal = readings.maxOf { it.values[0] }
                if (sensor.maximumRange > 0 && maxVal > sensor.maximumRange) {
                    append(" [note: reading ($maxVal) exceeds max range — metadata may be incorrect]")
                }
            }
            appendLine()

            when (state) {
                SensorState.NOT_AVAILABLE -> appendLine("Status: ${getStateLabel(SensorState.NOT_AVAILABLE)}")
                SensorState.NO_PERMISSION -> appendLine("Status: ${getStateLabel(SensorState.NO_PERMISSION)}")
                SensorState.IDLE -> appendLine("Status: ${getStateLabel(SensorState.IDLE)}")
                SensorState.READY -> {
                    val useSummary = forceSummary || readings.size > HIGH_SAMPLE_SUMMARY_THRESHOLD
                    if (useSummary && readings.size > HIGH_SAMPLE_SUMMARY_THRESHOLD) {
                        appendLine("Summary: ${readings.size} samples (auto-summarized)")
                    } else if (forceSummary) {
                        appendLine("Summary: ${readings.size} samples")
                    } else if (readings.size == 1) {
                        appendLine("Reading: ${readings.size} sample")
                    } else {
                        appendLine("Readings (${readings.size} samples):")
                    }
                    appendSensorBody(sensor.type, readings, unit, useSummary)
                }
            }
        }
    }

    private fun StringBuilder.appendSensorBody(type: Int, readings: List<SensorReading>, unit: String, useSummary: Boolean) {
        val unitStr = if (unit.isNotBlank()) " $unit" else ""

        if (useSummary || readings.size > 5) {
            val axisCount = readings.first().values.size

            val averages = FloatArray(axisCount) { axis ->
                readings.map { it.values[axis] }.average().toFloat()
            }
            val mins = FloatArray(axisCount) { axis ->
                readings.map { it.values[axis] }.minOrNull() ?: 0f
            }
            val maxs = FloatArray(axisCount) { axis ->
                readings.map { it.values[axis] }.maxOrNull() ?: 0f
            }

            // #6: Labeled output for uncalibrated sensors
            if (isUncalibratedType(type)) {
                appendLine("Average: ${formatUncalibratedValues(type, averages)}$unitStr")
                if (readings.size > 1) {
                    appendLine("Min:     ${formatUncalibratedValues(type, mins)}$unitStr")
                    appendLine("Max:     ${formatUncalibratedValues(type, maxs)}$unitStr")
                }
            } else if (isEnumType(type)) {
                // #8: Enum annotation
                appendLine("Average: ${annotateEnumValue(type, averages)}")
                if (readings.size > 1) {
                    appendLine("Min:     ${annotateEnumValue(type, mins)}")
                    appendLine("Max:     ${annotateEnumValue(type, maxs)}")
                }
            } else {
                appendLine("Average: ${averages.joinToString(", ") { formatValue(it) }}$unitStr")
                if (readings.size > 1) {
                    appendLine("Min:     ${mins.joinToString(", ") { formatValue(it) }}$unitStr")
                    appendLine("Max:     ${maxs.joinToString(", ") { formatValue(it) }}$unitStr")
                }
            }

            // #7: Quaternion norm for rotation vectors, RMS for others
            if (axisCount > 1 && !isEnumType(type)) {
                if (isQuaternionType(type)) {
                    val norm = quaternionNorm(averages)
                    appendLine("Quaternion norm: ${formatValue(norm)} (should be ~1.0 for valid rotation)")
                } else {
                    val rms = sqrt(averages.map { (it * it).toDouble() }.sum())
                    appendLine("Magnitude (RMS): ${formatValue(rms.toFloat())}$unitStr")
                }
            }

            val bestAccuracy = readings.maxOf { it.accuracy }
            appendLine("Accuracy: ${getAccuracyLabel(bestAccuracy)}")
        } else {
            // Individual readings
            readings.forEachIndexed { i, r ->
                val valuesStr = if (isUncalibratedType(type)) {
                    formatUncalibratedValues(type, r.values)
                } else if (isEnumType(type)) {
                    annotateEnumValue(type, r.values)
                } else {
                    r.values.joinToString(", ") { formatValue(it) }
                }
                appendLine("  #${i + 1}: $valuesStr$unitStr [accuracy: ${getAccuracyLabel(r.accuracy)}]")
            }

            if (readings.size > 1) {
                val axisCount = readings.first().values.size
                val averages = FloatArray(axisCount) { axis ->
                    readings.map { it.values[axis] }.average().toFloat()
                }
                val avgStr = if (isUncalibratedType(type)) {
                    formatUncalibratedValues(type, averages)
                } else if (isEnumType(type)) {
                    annotateEnumValue(type, averages)
                } else {
                    averages.joinToString(", ") { formatValue(it) }
                }
                appendLine("Average: $avgStr$unitStr")

                if (axisCount > 1 && !isEnumType(type)) {
                    if (isQuaternionType(type)) {
                        val norm = quaternionNorm(averages)
                        appendLine("Quaternion norm: ${formatValue(norm)}")
                    } else {
                        val rms = sqrt(averages.map { (it * it).toDouble() }.sum())
                        appendLine("Magnitude (RMS): ${formatValue(rms.toFloat())}$unitStr")
                    }
                }
            }
        }
    }

    // #5: Complete summary line for all sensor types, including previously-missing ones
    fun formatSummaryLine(sensor: Sensor, readings: List<SensorReading>): String {
        val type = sensor.type
        val unit = getSensorUnit(type)
        val name = getTypeName(type).lowercase().replace(" ", "_")

        if (readings.isEmpty()) return "$name: (idle)"

        val avgValues = FloatArray(readings.first().values.size) { axis ->
            readings.map { it.values[axis] }.average().toFloat()
        }

        return when (type) {
            Sensor.TYPE_LIGHT -> "$name: ${formatValue(avgValues[0])} $unit"
            Sensor.TYPE_PRESSURE -> "$name: ${formatValue(avgValues[0])} $unit"
            Sensor.TYPE_AMBIENT_TEMPERATURE -> "$name: ${formatValue(avgValues[0])}$unit"
            Sensor.TYPE_RELATIVE_HUMIDITY -> "$name: ${formatValue(avgValues[0])}$unit"
            Sensor.TYPE_PROXIMITY -> {
                val d = avgValues[0]
                val hint = when { d < 1 -> "(near)"; d < 5 -> "(close)"; else -> "(far)" }
                "$name: ${formatValue(d)} $unit $hint"
            }
            Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_MAGNETIC_FIELD,
            Sensor.TYPE_GRAVITY, Sensor.TYPE_LINEAR_ACCELERATION -> {
                val rms = sqrt(avgValues.map { (it * it).toDouble() }.sum())
                "$name: ${formatValue(rms.toFloat())} $unit RMS"
            }
            // #6: Uncalibrated — show measurement values + bias in compact form
            Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> {
                val rms = sqrt(avgValues.take(3).map { (it * it).toDouble() }.sum())
                "accel_uncalib: ${formatValue(rms.toFloat())} m/s² (bias RMS ${formatValue(
                    sqrt(avgValues.drop(3).map { (it * it).toDouble() }.sum()).toFloat())})"
            }
            Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> {
                val rms = sqrt(avgValues.take(3).map { (it * it).toDouble() }.sum())
                "gyro_uncalib: ${formatValue(rms.toFloat())} rad/s (drift RMS ${formatValue(
                    sqrt(avgValues.drop(3).map { (it * it).toDouble() }.sum()).toFloat())})"
            }
            Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> {
                val rms = sqrt(avgValues.take(3).map { (it * it).toDouble() }.sum())
                "mag_uncalib: ${formatValue(rms.toFloat())} µT (bias RMS ${formatValue(
                    sqrt(avgValues.drop(3).map { (it * it).toDouble() }.sum()).toFloat())})"
            }
            Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                val norm = quaternionNorm(avgValues)
                val orient = rotationHint(avgValues)
                "rotation: $orient (norm=${formatValue(norm)})"
            }
            Sensor.TYPE_STEP_COUNTER -> "$name: ${avgValues[0].toLong()} $unit"
            Sensor.TYPE_STEP_DETECTOR -> "step_detector: ${if (avgValues[0] > 0.5f) "triggered" else "idle"}"
            Sensor.TYPE_SIGNIFICANT_MOTION -> "significant_motion: ${if (avgValues[0] > 0.5f) "triggered" else "idle"}"
            TYPE_DEVICE_ORIENTATION -> {
                val v = avgValues[0].toInt()
                "device_orientation: ${DEVICE_ORIENTATION_LABELS[v] ?: v.toString()}"
            }
            TYPE_TILT_DETECTOR -> {
                "tilt_detector: ${if (avgValues[0] > 0.5f) "tilted" else "idle"}"
            }
            else -> "$name: ${avgValues.size} axes, ${readings.size} samples"
        }
    }

    private fun rotationHint(values: FloatArray): String {
        if (values.size < 3) return "unknown"
        val x = values[0]; val y = values[1]
        val z = if (values.size >= 3) values[2] else 0f
        val w = if (values.size >= 4) values[3] else 1f
        val pitch = Math.toDegrees(Math.asin((2 * (w * x - y * z).toDouble()).coerceIn(-1.0, 1.0)))
        val roll = Math.toDegrees(Math.atan2((2 * (w * y + x * z)).toDouble(), (1 - 2 * (x * x + y * y)).toDouble()))

        return when {
            Math.abs(pitch) < 30 && Math.abs(roll) < 30 -> "flat"
            Math.abs(roll) > 60 -> if (roll > 0) "portrait" else "reverse_portrait"
            Math.abs(pitch) > 60 -> if (pitch > 0) "landscape_left" else "landscape_right"
            else -> "tilted(${pitch.toInt()}°,${roll.toInt()}°)"
        }
    }

    private fun formatValue(value: Float): String {
        return if (value == value.toLong().toFloat() && value < 1e6f) {
            value.toLong().toString()
        } else {
            "%.4f".format(value).trimEnd('0').trimEnd('.')
        }
    }

    fun findSensorByName(sensorManager: SensorManager, name: String): Sensor? {
        val all = sensorManager.getSensorList(Sensor.TYPE_ALL)
        val lower = name.lowercase().trim()
        return all.find {
            it.name.equals(name, ignoreCase = true) ||
            it.stringType.equals(name, ignoreCase = true) ||
            it.stringType.lowercase().contains(lower) ||
            it.name.lowercase().contains(lower) ||
            it.type.toString() == name
        }
    }
}
