package com.sil.others

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.pow

class SensorHelper(private val context: Context) : SensorEventListener {
    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var lastUpdate: Long = 0
    private var lastX: Float = 0.0f
    private var lastY: Float = 0.0f
    private var lastZ: Float = 0.0f

    init {
        accelerometer?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val sensor = event.sensor
        if (sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val currentTime = System.currentTimeMillis()
            if ((currentTime - lastUpdate) > 100) {
                val diffTime = (currentTime - lastUpdate)
                lastUpdate = currentTime

                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                val speed = kotlin.math.sqrt(
                    (x - lastX).pow(2) + (y - lastY).pow(2) + (z - lastZ).pow(2)
                ) / diffTime * 10000

                lastX = x
                lastY = y
                lastZ = z

                // Process the speed to determine the device status
                val deviceStatus = when {
                    speed < 200 -> "IDLE"
                    speed >= 200 && speed < 400 -> "WALKING"
                    else -> "UNKNOWN"
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Handle accuracy changes if needed
    }

    fun unregister() {
        sensorManager.unregisterListener(this)
    }
}