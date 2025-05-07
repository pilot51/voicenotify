/*
 * Copyright 2011-2025 Mark Injerd
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
import android.util.Log
import com.pilot51.voicenotify.prefs.DataStoreManager.getPrefStateFlow
import com.pilot51.voicenotify.prefs.PreferenceHelper.KEY_SHAKE_THRESHOLD
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.sqrt

class Shake(context: Context) : SensorEventListener {
	private val manager = context.getSystemService(SENSOR_SERVICE) as SensorManager
	private val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
	var onShake: (() -> Unit)? = null
	private val _threshold = getPrefStateFlow(KEY_SHAKE_THRESHOLD, 0)
	private val threshold get() = _threshold.value
	private var isListening = false
	private var accelCurrent = 0f
	private var accelLast = 0f
	private val _jerk = MutableStateFlow(0f)
	val jerk: StateFlow<Float> = _jerk

	/** @param force `true` to enable the shake listener even if shake-to-silence is disabled in settings. */
	fun enable(force: Boolean = false) {
		if (!force && threshold == 0) return
		isListening = manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
		Log.i(TAG, "Shake listener ${if (isListening) "enabled" else "failed to enable"}")
	}

	fun disable() {
		if (!isListening) return
		manager.unregisterListener(this)
		isListening = false
		accelCurrent = 0f
		accelLast = 0f
		Log.i(TAG, "Shake listener disabled")
	}

	override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

	override fun onSensorChanged(event: SensorEvent) {
		val x = event.values[0]
		val y = event.values[1]
		val z = event.values[2]
		accelCurrent = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
		_jerk.value = if (accelLast != 0f) abs(accelCurrent - accelLast) * 10 else 0f
		if (threshold > 0 && jerk.value >= threshold) {
			onShake?.invoke()
		}
		accelLast = accelCurrent
	}

	companion object {
		private val TAG = Shake::class.simpleName
	}
}
