package com.noexcs.indolent.task.conditional.conditionProvider

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import com.noexcs.indolent.agent.tools.sensor.SensorUtils
import com.noexcs.indolent.logging.Lumberjack
import kotlin.math.sqrt

class SensorConditionProvider(private val context: Context) {

    private val monitoredTypes = listOf(
        Sensor.TYPE_LIGHT,
        Sensor.TYPE_PROXIMITY,
        Sensor.TYPE_AMBIENT_TEMPERATURE,
        Sensor.TYPE_RELATIVE_HUMIDITY,
        Sensor.TYPE_PRESSURE,
        Sensor.TYPE_ACCELEROMETER,
        Sensor.TYPE_GYROSCOPE,
        Sensor.TYPE_MAGNETIC_FIELD,
        Sensor.TYPE_GRAVITY,
        Sensor.TYPE_LINEAR_ACCELERATION,
        Sensor.TYPE_ROTATION_VECTOR,
        Sensor.TYPE_STEP_COUNTER,
        Sensor.TYPE_STEP_DETECTOR
    )

    private val fieldToType = mapOf(
        "light" to Sensor.TYPE_LIGHT,
        "ambient_light" to Sensor.TYPE_LIGHT,
        "proximity" to Sensor.TYPE_PROXIMITY,
        "temperature" to Sensor.TYPE_AMBIENT_TEMPERATURE,
        "ambient_temperature" to Sensor.TYPE_AMBIENT_TEMPERATURE,
        "humidity" to Sensor.TYPE_RELATIVE_HUMIDITY,
        "relative_humidity" to Sensor.TYPE_RELATIVE_HUMIDITY,
        "pressure" to Sensor.TYPE_PRESSURE,
        "accelerometer" to Sensor.TYPE_ACCELEROMETER,
        "gyroscope" to Sensor.TYPE_GYROSCOPE,
        "magnetometer" to Sensor.TYPE_MAGNETIC_FIELD,
        "gravity" to Sensor.TYPE_GRAVITY,
        "linear_acceleration" to Sensor.TYPE_LINEAR_ACCELERATION,
        "rotation_vector" to Sensor.TYPE_ROTATION_VECTOR,
        "step_counter" to Sensor.TYPE_STEP_COUNTER
    )

    suspend fun sampleAll(): Map<String, String> {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val result = mutableMapOf<String, String>()

        for (type in monitoredTypes) {
            val sensor = sensorManager.getDefaultSensor(type) ?: continue

            val dur = SensorUtils.getDefaultDurationMs(type)
            val readings = SensorUtils.collectSensorReadings(context, sensor, dur)

            if (readings.isEmpty()) continue

            val avg = FloatArray(readings.first().values.size) { axis ->
                readings.map { it.values[axis] }.average().toFloat()
            }

            val label = when (type) {
                Sensor.TYPE_LIGHT -> "light"
                Sensor.TYPE_PROXIMITY -> "proximity"
                Sensor.TYPE_AMBIENT_TEMPERATURE -> "temperature"
                Sensor.TYPE_RELATIVE_HUMIDITY -> "humidity"
                Sensor.TYPE_PRESSURE -> "pressure"
                Sensor.TYPE_ACCELEROMETER -> "accelerometer"
                Sensor.TYPE_GYROSCOPE -> "gyroscope"
                Sensor.TYPE_MAGNETIC_FIELD -> "magnetometer"
                Sensor.TYPE_GRAVITY -> "gravity"
                Sensor.TYPE_LINEAR_ACCELERATION -> "linear_acceleration"
                Sensor.TYPE_ROTATION_VECTOR -> "rotation_vector"
                Sensor.TYPE_STEP_COUNTER -> "step_counter"
                Sensor.TYPE_STEP_DETECTOR -> "step_detector"
                else -> "sensor_$type"
            }

            when (type) {
                Sensor.TYPE_LIGHT, Sensor.TYPE_PROXIMITY,
                Sensor.TYPE_AMBIENT_TEMPERATURE, Sensor.TYPE_RELATIVE_HUMIDITY,
                Sensor.TYPE_PRESSURE, Sensor.TYPE_STEP_COUNTER,
                Sensor.TYPE_STEP_DETECTOR -> {
                    result[label] = formatValue(avg[0])
                }
                else -> {
                    // For multi-axis sensors, store RMS magnitude
                    val rms = sqrt(avg.map { (it * it).toDouble() }.sum())
                    result["${label}_rms"] = formatValue(rms.toFloat())
                    // Also store individual axes
                    if (avg.size >= 1) result["${label}_x"] = formatValue(avg[0])
                    if (avg.size >= 2) result["${label}_y"] = formatValue(avg[1])
                    if (avg.size >= 3) result["${label}_z"] = formatValue(avg[2])
                }
            }
        }

        Lumberjack.d(TAG, "Sampled ${result.size} sensor fields from ${result.size / 3} sensors")
        return result
    }

    fun resolveType(field: String): Int? = fieldToType[field.lowercase().trim()]

    companion object {
        private const val TAG = "SensorConditionProvider"

        fun formatValue(value: Float): String {
            return if (value == value.toLong().toFloat() && value < 1e6f) {
                value.toLong().toString()
            } else {
                "%.4f".format(value).trimEnd('0').trimEnd('.')
            }
        }
    }
}
