/*
 * Copyright 2023 Mark Injerd
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

import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment

object PermissionHelper {
	/** Convenience to create a result launcher for requesting a permission. */
	fun Fragment.requestPermissionLauncher(onResult: ((isGranted: Boolean) -> Unit) = {}) =
		registerForActivityResult(ActivityResultContracts.RequestPermission(), onResult)

	fun Context.isPermissionGranted(permission: String) =
		ActivityCompat.checkSelfPermission(this, permission) == PERMISSION_GRANTED

	/**
	 * @param permission The permission from `Manifest.permission.*`.
	 * @param rationaleMsgId The permission rationale string id if it should be shown.
	 * @param launcher The result launcher used to request the permission.
	 * @return `true` if permission is already granted,
	 * `false` if the user needs to approve it first and
	 * the [launcher] callback will be used to proceed.
	 */
	fun Fragment.requestPermission(
		permission: String,
		@StringRes rationaleMsgId: Int,
		launcher: ActivityResultLauncher<String>
	): Boolean {
		val context = requireContext()
		if (context.isPermissionGranted(permission)) return true
		else if (shouldShowRequestPermissionRationale(permission)) {
			AlertDialog.Builder(context)
				.setMessage(rationaleMsgId)
				.setPositiveButton(android.R.string.ok) { _, _ ->
					launcher.launch(permission)
				}
				.setNegativeButton(android.R.string.cancel, null)
				.show()
		} else launcher.launch(permission)
		return false
	}
}
