package com.saeid.aidirectorcamera.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

data class MotionState(
    val horizonDegrees: Float = 0f,
    val smoothness: Float = 100f,
    val rotationSpeed: Float = 0f,
    val movementLabel: String = "STEADY"
)

class MotionAnalyzer(context: Context) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotation = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val gyro = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _state = MutableStateFlow(MotionState())
    val state: StateFlow<MotionState> = _state

    fun start() {
        rotation?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyro?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        accelerometer?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() = manager.unregisterListener(this)

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                val matrix = FloatArray(9)
                val orientation = FloatArray(3)
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                SensorManager.getOrientation(matrix, orientation)
                val roll = (orientation[2] * 180f / PI.toFloat())
                val normalized = when {
                    roll > 90f -> roll - 180f
                    roll < -90f -> roll + 180f
                    else -> roll
                }
                _state.value = _state.value.copy(horizonDegrees = normalized)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]
                val magnitude = sqrt(ax*ax + ay*ay + az*az)
                val dynamicAcceleration = abs(magnitude - SensorManager.GRAVITY_EARTH)
                val accelSmoothness = (100f - dynamicAcceleration * 18f).coerceIn(0f, 100f)
                val blended = _state.value.smoothness * 0.90f + accelSmoothness * 0.10f
                _state.value = _state.value.copy(
                    smoothness = blended,
                    movementLabel = if (dynamicAcceleration > 1.8f) "SHAKE" else _state.value.movementLabel
                )
            }
            Sensor.TYPE_GYROSCOPE -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val magnitude = sqrt(x*x + y*y + z*z)
                val rawSmoothness = (100f - magnitude * 34f).coerceIn(0f, 100f)
                val smoothed = _state.value.smoothness * 0.86f + rawSmoothness * 0.14f
                val label = when {
                    magnitude < 0.12f -> "STEADY"
                    abs(y) > abs(x) && abs(y) > abs(z) -> "PAN"
                    abs(x) > abs(z) -> "TILT"
                    else -> "ROLL"
                }
                _state.value = _state.value.copy(
                    smoothness = smoothed,
                    rotationSpeed = magnitude,
                    movementLabel = label
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
