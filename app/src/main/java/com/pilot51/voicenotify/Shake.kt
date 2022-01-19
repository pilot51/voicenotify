/*
 * Copyright 2012 Mark Injerd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pilot51.voicenotify

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.pilot51.voicenotify.Common.getPrefs
import kotlin.math.abs
import kotlin.math.sqrt

class Shake(
	private val context: Context
) : SensorEventListener {
	private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
	private val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
	private var listener: OnShakeListener? = null
	private var threshold = 0
	private var overThresholdCount = 0
	private var accelCurrent = 0f
	private var accelLast = 0f

	fun enable() {
		if (listener == null) return
		threshold = try {
			getPrefs(context).getString(context.getString(R.string.key_shake_threshold), null)!!.toInt()
		} catch (e: NumberFormatException) {
			// Don't enable if threshold setting is blank
			return
		}
		manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
	}

	fun disable() {
		manager.unregisterListener(this)
		accelCurrent = 0f
		accelLast = 0f
		overThresholdCount = 0
	}

	fun setOnShakeListener(listener: OnShakeListener?) {
		this.listener = listener
	}

	override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

	override fun onSensorChanged(event: SensorEvent) {
		val x = event.values[0]
		val y = event.values[1]
		val z = event.values[2]
		accelCurrent = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
		val accel = accelCurrent - accelLast
		if (accelLast != 0f && abs(accel) > threshold / 10) {
			overThresholdCount++
			if (overThresholdCount >= 2) {
				listener!!.onShake()
			}
		} else {
			overThresholdCount = 0
		}
		accelLast = accelCurrent
	}

	interface OnShakeListener {
		fun onShake()
	}
}
