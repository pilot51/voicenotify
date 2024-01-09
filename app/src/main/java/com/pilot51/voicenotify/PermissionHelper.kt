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

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import com.google.accompanist.permissions.*
import com.pilot51.voicenotify.PermissionHelper.RationaleDialog

object PermissionHelper {
	fun Context.isPermissionGranted(permission: String) =
		ActivityCompat.checkSelfPermission(this, permission) == PERMISSION_GRANTED

	/**
	 * Checks if this permission is granted. If not, checks if the rationale
	 * should be shown and if not, launches the permission request.
	 * @param onShowRationale Called when the rationale should be shown.
	 * @return `true` if permission is already granted,
	 * `false` if the user needs to approve it first.
	 */
	@OptIn(ExperimentalPermissionsApi::class)
	fun PermissionState.requestPermission(
		onShowRationale: () -> Unit
	): Boolean {
		if (status.isGranted) {
			return true
		} else if (status.shouldShowRationale) {
			onShowRationale()
		} else {
			launchPermissionRequest()
		}
		return false
	}

	/**
	 * @param permissionState The [PermissionState] to use.
	 * @param rationaleMsgId The permission rationale string id if it should be shown.
	 * @param onDismiss Called when the dialog should be dismissed.
	 */
	@OptIn(ExperimentalPermissionsApi::class)
	@Composable
	fun RationaleDialog(
		permissionState: PermissionState,
		@StringRes rationaleMsgId: Int,
		onDismiss: () -> Unit
	) {
		AlertDialog(
			onDismissRequest = onDismiss,
			confirmButton = {
				TextButton(
					onClick = {
						permissionState.launchPermissionRequest()
						onDismiss()
					}
				) {
					Text(stringResource(android.R.string.ok))
				}
			},
			dismissButton = {
				TextButton(onClick = onDismiss) {
					Text(stringResource(android.R.string.cancel))
				}
			},
			text = {
				Text(stringResource(rationaleMsgId))
			}
		)
	}
}

@OptIn(ExperimentalPermissionsApi::class)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun RationaleDialogPreview() {
	val permissionState = object : PermissionState {
		override val permission = Manifest.permission.READ_PHONE_STATE
		override val status = PermissionStatus.Granted
		override fun launchPermissionRequest() {}
	}
	AppTheme {
		RationaleDialog(
			permissionState = permissionState,
			rationaleMsgId = R.string.permission_rationale_read_phone_state
		) {}
	}
}
