/*
 * Copyright 2012-2023 Mark Injerd
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

import android.content.Context.SENSOR_SERVICE
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.pilot51.voicenotify.Common.prefs
import com.pilot51.voicenotify.VNApplication.Companion.appContext
import kotlin.math.abs
import kotlin.math.sqrt

class Shake : SensorEventListener {
	private val manager = appContext.getSystemService(SENSOR_SERVICE) as SensorManager
	private val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
	var onShake: (() -> Unit)? = null
	private var threshold = 0.0
	private var overThresholdCount = 0
	private var accelCurrent = 0f
	private var accelLast = 0f

	fun enable() {
		if (onShake == null) return
		threshold = try {
			prefs.getString(appContext.getString(R.string.key_shake_threshold), null)
				?.takeIf { it.isNotEmpty() }?.toDouble() ?: return
		} catch (e: NumberFormatException) {
			Log.w(TAG, "Failed to parse shake threshold: ${e.message}")
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
				onShake!!.invoke()
			}
		} else {
			overThresholdCount = 0
		}
		accelLast = accelCurrent
	}

	companion object {
		private val TAG = Shake::class.simpleName
	}
}
