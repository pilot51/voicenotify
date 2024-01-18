/*
 * Copyright 2011-2024 Mark Injerd
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
import android.content.Context.SENSOR_SERVICE
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.pilot51.voicenotify.PreferenceHelper.DEFAULT_SHAKE_THRESHOLD
import com.pilot51.voicenotify.PreferenceHelper.KEY_SHAKE_THRESHOLD
import com.pilot51.voicenotify.PreferenceHelper.prefs
import kotlin.math.abs
import kotlin.math.sqrt

class Shake(context: Context) : SensorEventListener {
	private val manager = context.getSystemService(SENSOR_SERVICE) as SensorManager
	private val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
	var onShake: (() -> Unit)? = null
	private var threshold = 0.0
	private var overThresholdCount = 0
	private var accelCurrent = 0f
	private var accelLast = 0f

	fun enable() {
		if (onShake == null) return
		threshold = prefs.getString(KEY_SHAKE_THRESHOLD, DEFAULT_SHAKE_THRESHOLD.toString())
				?.toDoubleOrNull() ?: return
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
