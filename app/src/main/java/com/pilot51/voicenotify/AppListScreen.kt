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
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pilot51.voicenotify.AppListViewModel.IgnoreType
import com.pilot51.voicenotify.db.App
import com.pilot51.voicenotify.ui.SealSearchBar
import com.pilot51.voicenotify.ui.SwitchCustom
import com.pilot51.voicenotify.ui.theme.VoicenotifyTheme
private lateinit var vmStoreOwner: ViewModelStoreOwner


@Composable
fun AppListActions(modifier: Modifier = Modifier) {
	val vm: AppListViewModel = viewModel(vmStoreOwner)
	SealSearchBar(
		modifier = modifier,
		text = vm.searchQuery ?: "",
		onValueChange = {
			vm.searchQuery = it
			vm.filterApps(it)
		},
		placeholderText = "Search"
	)

}

@Composable
fun AppListScreen(
	list:  List<App> = emptyList(),
	onConfigureApp: (app: App) -> Unit
) {
	vmStoreOwner = LocalViewModelStoreOwner.current!!
	val vm: AppListViewModel = viewModel(vmStoreOwner)
	val packagesWithOverride by vm.packagesWithOverride

	Column(
		modifier = Modifier.fillMaxSize()
			.background(VoicenotifyTheme.colors.background)
			.padding(12.dp , 0.dp)
	) {

		AppListActions(
			modifier = Modifier
				.fillMaxWidth()
				.padding(0.dp, 4.dp, 0.dp, 8.dp)
		)
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.clip(RoundedCornerShape(12.dp))
				.background(VoicenotifyTheme.colors.boxItem)
		) {
			AppList(
				filteredApps = if (list.isNotEmpty()) list else vm.filteredApps,
				showList = vm.showList,
				stickyHeader = {
					val modifier = Modifier
						.fillMaxWidth()
					Row(
						modifier = modifier,
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically
						) {
						Text(
							text = stringResource(R.string.ignore_none),
							modifier = Modifier.padding(8.dp)
						)
						SwitchCustom(
							checked = vm.appEnable,
							onCheckedChange = {
								vm.massIgnore(if (it) IgnoreType.IGNORE_NONE else IgnoreType.IGNORE_ALL)
							},
							modifier = Modifier.padding(8.dp)
						)
					}
				},
				packagesWithOverride = packagesWithOverride,
				toggleIgnore = { app ->
					vm.setIgnore(app, IgnoreType.IGNORE_TOGGLE)
				},
				onConfigureApp = onConfigureApp,
				onRemoveOverrides = vm::removeOverrides
			)
		}
	}


}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppList(
	filteredApps: List<App>,
	showList: Boolean,
	packagesWithOverride: List<String>,
	toggleIgnore: (app: App) -> Unit,
	onConfigureApp: (app: App) -> Unit,
	onRemoveOverrides: (app: App) -> Unit,
	stickyHeader: @Composable () -> Unit = {}
) {
	if (!showList) return
	Column(
		modifier = Modifier.fillMaxSize()
			.background(MaterialTheme.colorScheme.surface),
		verticalArrangement = Arrangement.Top
	) {
		LazyColumn(
			contentPadding = PaddingValues(0.dp)
		) {
			stickyHeader {
				stickyHeader()
			}
			items(filteredApps) {
				val hasOverride = packagesWithOverride.contains(it.packageName)
				AppListItem(it, hasOverride, toggleIgnore, onConfigureApp, onRemoveOverrides)
			}
		}
	}
}

@Composable
fun PackageImage(context: Context, packageName: String, modifier: Modifier = Modifier) {
	val packageManager: PackageManager = context.packageManager
	val result = runCatching {
		val appInfo = packageManager.getApplicationInfo(packageName, 0)
		val icon = appInfo.loadIcon(packageManager).toBitmap().asImageBitmap()
		Image(
			painter = BitmapPainter(icon),
			contentDescription = null,
			modifier = modifier
		)
	}
	result.onFailure {
		// If there's an exception, use the default image resource
		Image(
			painter = painterResource(R.drawable.ic_launcher_foreground),
			contentDescription = null,
			modifier = modifier
		)
	}
}

 @Composable
 fun ListItem(
 	modifier: Modifier = Modifier,
 	leadingContent: @Composable (() -> Unit)? = null,
 	headlineContent: @Composable (() -> Unit)? = null,
 	supportingContent: @Composable (() -> Unit)? = null,
 	trailingContent: @Composable (() -> Unit)? = null
 ) {
 	Row(
 		modifier = modifier
 			.padding(8.dp)
 			.fillMaxWidth(),
 		horizontalArrangement = Arrangement.Start,
 		verticalAlignment = Alignment.CenterVertically
 	) {
 		leadingContent?.invoke()
 		Column(
 			modifier = Modifier
 				.weight(1f)
 				.padding(start = 8.dp)
 		) {
 			headlineContent?.invoke()
 			supportingContent?.invoke()
 		}
 		trailingContent?.invoke()
 	}
 }

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
		}.fillMaxWidth(),
		// modifier = Modifier.toggleable(
		// 	value = app.enabled,
		// 	role = Role.Checkbox,
		// 	onValueChange = { toggleIgnore(app) }
		// 	).fillMaxWidth(),
		leadingContent = {
			PackageImage(
				context = LocalContext.current,
				packageName = app.packageName,
				modifier = Modifier.size(48.dp)
			)
		},
		headlineContent = {
			Text(
				text = app.label,
				fontSize = 18.sp
			)
		},
		supportingContent = {
			Text(
				text = app.packageName,
				fontSize = 12.sp
			)
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
				SwitchCustom(
					checked = app.enabled,
					onCheckedChange = { toggleIgnore(app) },
					modifier = Modifier.focusable(false)
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

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun AppListPreview() {
	val apps = listOf(
		App("package.name.one", "App Name 1", true),
		App("package.name.two", "App Name 2", false)
	)
	AppTheme {
		AppList(apps, true, listOf("package.name.one"), {}, {}, {})
	}
}





