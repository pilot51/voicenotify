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
package com.pilot51.voicenotify.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pilot51.voicenotify.AppTheme
import com.pilot51.voicenotify.R
import com.pilot51.voicenotify.db.App
import com.pilot51.voicenotify.ui.dialog.ConfirmDialog
import kotlinx.coroutines.delay

private val vmStoreOwner = mutableStateOf<ViewModelStoreOwner?>(null)

@Composable
fun AppListActions() {
	val vmOwner by vmStoreOwner
	val vm: AppListViewModel = viewModel(vmOwner ?: return)
	var showSearchBar by remember { mutableStateOf(false) }
	if (showSearchBar) {
		val focusRequester = remember { FocusRequester() }
		val keyboard = LocalSoftwareKeyboardController.current
		val searchQuery by vm.searchQuery.collectAsState()
		TextField(
			value = searchQuery ?: "",
			onValueChange = {
				vm.filterApps(it)
			},
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 4.dp)
				.focusRequester(focusRequester),
			maxLines = 1,
			singleLine = true,
			leadingIcon = {
				Icon(
					imageVector = Icons.Filled.Search,
					contentDescription = null
				)
			},
			trailingIcon = {
				IconButton(onClick = {
					showSearchBar = false
					vm.filterApps(null)
				}) {
					Icon(
						imageVector = Icons.Filled.Close,
						contentDescription = stringResource(R.string.close)
					)
				}
			}
		)
		LaunchedEffect(focusRequester) {
			focusRequester.requestFocus()
			delay(100)
			keyboard?.show()
		}
	} else {
		/** `true` for enable all, `false` for ignore all, `null` to not show dialog. */
		var confirmDialogEnableAll by remember { mutableStateOf<Boolean?>(null) }
		IconButton(
			onClick = { showSearchBar = true }
		) {
			Icon(
				imageVector = Icons.Filled.Search,
				contentDescription = stringResource(R.string.filter)
			)
		}
		IconButton(
			onClick = { confirmDialogEnableAll = false }
		) {
			Icon(
				imageVector = Icons.Filled.CheckBoxOutlineBlank,
				contentDescription = stringResource(R.string.ignore_all)
			)
		}
		IconButton(
			onClick = { confirmDialogEnableAll = true }
		) {
			Icon(
				imageVector = Icons.Filled.CheckBox,
				contentDescription = stringResource(R.string.ignore_none)
			)
		}
		confirmDialogEnableAll?.let {
			ConfirmDialog(
				text = stringResource(
					R.string.ignore_enable_apps_confirm,
					stringResource(if (it) R.string.enable else R.string.ignore).lowercase()
				),
				onConfirm = { vm.massIgnore(it) },
				onDismiss = { confirmDialogEnableAll = null }
			)
		}
	}
}

@Composable
fun AppListScreen(
	onConfigureApp: (app: App) -> Unit
) {
	val vmOwner = LocalViewModelStoreOwner.current!!.also { vmStoreOwner.value = it }
	DisposableEffect(vmOwner) { onDispose { vmStoreOwner.value = null } }
	val vm: AppListViewModel = viewModel(vmOwner)
	val packagesWithOverride by vm.packagesWithOverride
	val filteredApps by vm.filteredApps.collectAsState()
	val isLoading by vm.isLoading.collectAsState()
	Box {
		AppList(
			filteredApps,
			packagesWithOverride,
			toggleIgnore = { app ->
				vm.toggleIgnore(app)
			},
			onConfigureApp = onConfigureApp,
			onRemoveOverrides = vm::removeOverrides
		)
		if (isLoading) {
			CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
		}
	}
}

@Composable
private fun AppList(
	filteredApps: List<App>,
	packagesWithOverride: List<String>,
	toggleIgnore: (app: App) -> Unit,
	onConfigureApp: (app: App) -> Unit,
	onRemoveOverrides: (app: App) -> Unit
) {
	LazyColumn(modifier = Modifier.fillMaxSize()) {
		items(filteredApps) {
			val hasOverride = packagesWithOverride.contains(it.packageName)
			AppListItem(it, hasOverride, toggleIgnore, onConfigureApp, onRemoveOverrides)
		}
	}
}

@NonSkippableComposable
@Composable
private fun AppListItem(
	app: App,
	hasOverride: Boolean,
	toggleIgnore: (app: App) -> Unit,
	onConfigureApp: (app: App) -> Unit,
	onRemoveOverrides: (app: App) -> Unit
) {
	var showRemoveOverridesDialog by remember { mutableStateOf(false) }
	ListItem(
		modifier = Modifier.clickable {
			onConfigureApp(app)
		},
		headlineContent = {
			Text(
				text = app.label,
				fontSize = 24.sp
			)
		},
		supportingContent = {
			Text(app.packageName)
		},
		trailingContent = {
			Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
				if (hasOverride) {
					IconButton(onClick = { showRemoveOverridesDialog = true }) {
						Icon(
							imageVector = Icons.Outlined.Cancel,
							contentDescription = stringResource(R.string.remove_app_overrides)
						)
					}
				}
				Checkbox(
					checked = app.isEnabled,
					modifier = Modifier.focusable(false),
					onCheckedChange = { toggleIgnore(app) }
				)
			}
		}
	)
	if (showRemoveOverridesDialog) {
		ConfirmDialog(
			text = stringResource(R.string.remove_app_overrides_confirm, app.label),
			onConfirm = { onRemoveOverrides(app) },
			onDismiss = { showRemoveOverridesDialog = false }
		)
	}
}

@VNPreview
@Composable
private fun AppListPreview() {
	val apps = listOf(
		App("package.name.one", "App Name 1", true),
		App("package.name.two", "App Name 2", false)
	)
	AppTheme {
		AppList(apps, listOf("package.name.one"), {}, {}, {})
	}
}
