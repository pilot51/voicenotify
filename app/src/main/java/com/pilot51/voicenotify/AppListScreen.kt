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
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pilot51.voicenotify.AppListViewModel.IgnoreType
import com.pilot51.voicenotify.db.App
import com.pilot51.voicenotify.ui.Layout
import com.pilot51.voicenotify.ui.LazyAlphabetIndexRow
import com.pilot51.voicenotify.ui.ListBox
import com.pilot51.voicenotify.ui.ListItem
import com.pilot51.voicenotify.ui.SearchBar
import com.pilot51.voicenotify.ui.Switch
import com.pilot51.voicenotify.ui.alphabetItemSize
import com.pilot51.voicenotify.ui.bottomBorder
import com.pilot51.voicenotify.ui.theme.VoiceNotifyTheme

private lateinit var vmStoreOwner: ViewModelStoreOwner



@RequiresApi(Build.VERSION_CODES.N)
@Composable
fun AppListActions(modifier: Modifier = Modifier) {
	val vm: AppListViewModel = viewModel(vmStoreOwner)
	SearchBar(
		modifier = modifier,
		text = vm.searchQuery ?: "",
		onValueChange = {
			vm.searchQuery = it
			vm.filterApps(it)
		},
		placeholderText = "Search"
	)

}


@RequiresApi(Build.VERSION_CODES.N)
@Composable
fun AppListScreen(
	list:  List<App> = emptyList(),
	onConfigureApp: (app: App) -> Unit
) {
	vmStoreOwner = LocalViewModelStoreOwner.current!!
	val vm: AppListViewModel = viewModel(vmStoreOwner)
	val packagesWithOverride by vm.packagesWithOverride
	var showConfirmDialog by remember { mutableStateOf<IgnoreType?>(null) }

	var filteredApps = if (list.isNotEmpty()) list else vm.filteredApps

	val lazyListState = rememberLazyListState()

	Layout(
		modifier = Modifier
	) {
		AppListActions(
			modifier = Modifier
				.fillMaxWidth()
				.padding(12.dp, 4.dp, 16.dp, 8.dp)
		)
		AppList(
			lazyListState = lazyListState,
			filteredApps = filteredApps,
			showList = vm.showList,
			stickyHeader = {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.background(VoiceNotifyTheme.colors.boxItem)

				) {
					Row(
						modifier = Modifier
							.weight(1f)
							.padding(start = 8.dp, end = 8.dp)
							.bottomBorder(2f, VoiceNotifyTheme.colors.divider),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically
					) {
						Text(
							text = stringResource(R.string.ignore_none),
						)
						Switch(
							checked = vm.appEnable,
							onCheckedChange = {
								showConfirmDialog = if (it) IgnoreType.IGNORE_NONE else IgnoreType.IGNORE_ALL
								// vm.massIgnore(if (it) IgnoreType.IGNORE_NONE else IgnoreType.IGNORE_ALL)
							}
						)
						showConfirmDialog?.let {
							val isEnableAll = it == IgnoreType.IGNORE_NONE
							ConfirmDialog(
								text = stringResource(
									R.string.ignore_enable_apps_confirm,
									stringResource(if (isEnableAll) R.string.enable else R.string.ignore).lowercase()
								),
								onConfirm = { vm.massIgnore(it) },
								onDismiss = { showConfirmDialog = null }
							)
						}
					}
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


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppList(
	lazyListState: LazyListState = rememberLazyListState(),
	filteredApps: List<App>,
	showList: Boolean,
	packagesWithOverride: List<String>,
	toggleIgnore: (app: App) -> Unit,
	onConfigureApp: (app: App) -> Unit,
	onRemoveOverrides: (app: App) -> Unit,
	stickyHeader: @Composable () -> Unit = {}
) {
	if (!showList) return
	val context = LocalDensity.current
	val alphabetHeightInPixels = remember { with(context) { alphabetItemSize.toPx() } }
	var alphabetRelativeDragYOffset: Float? by remember { mutableStateOf(null) }
	var alphabetDistanceFromTopOfScreen: Float by remember { mutableStateOf(0F) }
	LazyAlphabetIndexRow(
		items = filteredApps,
		keySelector = { AlphabeticIndexHelper.computeSectionName(it.label) },
		lazyListState = lazyListState,
		alphabetModifier = Modifier
			.padding(0.dp, 0.dp, 0.dp, 0.dp)
			.fillMaxHeight()
			.width(16.dp)
			.clip(RoundedCornerShape(8.dp)),
		onAlphabetListDrag = { relativeDragYOffset, containerDistance ->
			alphabetRelativeDragYOffset = relativeDragYOffset
			alphabetDistanceFromTopOfScreen = containerDistance
		}
	) {
		Box(
			modifier = Modifier
				.padding(12.dp, 0.dp, 0.dp, 0.dp)
				.clip(RoundedCornerShape(12.dp))
				.background(VoiceNotifyTheme.colors.boxItem),
		) {
			LazyColumn(
				modifier = Modifier.fillMaxSize(),
				state = lazyListState,
				contentPadding = PaddingValues(end = 0.dp),
			) {
				stickyHeader {
					stickyHeader()
				}
				itemsIndexed(
					items = filteredApps,
				) { index, it ->
					val hasOverride = packagesWithOverride.contains(it.packageName)
					AppListItem(
						it,
						hasOverride,
						toggleIgnore,
						onConfigureApp,
						onRemoveOverrides
					)
				}
			}

		}
	}
}

@Composable
fun PackageImage(context: Context, packageName: String,  modifier: Modifier = Modifier) {
	val packageManager: PackageManager = context.packageManager
	val result = runCatching {
		val appInfo = packageManager.getApplicationInfo(packageName, 0)
		val iconBitmap = appInfo.loadIcon(packageManager).toBitmap().asImageBitmap()
		Image(
			painter = BitmapPainter(iconBitmap),
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
private fun AppListItem(
	app: App,
	hasOverride: Boolean,
	toggleIgnore: (app: App) -> Unit,
	onConfigureApp: (app: App) -> Unit,
	onRemoveOverrides: (app: App) -> Unit
) {
	var showRemoveOverridesDialog by remember { mutableStateOf(false) }
	var context = LocalContext.current

	ListItem(
		modifier = Modifier
			.toggleable(
				value = app.enabled,
				role = Role.Checkbox,
				onValueChange = { toggleIgnore(app) }
			)
			.fillMaxWidth(),
		leadingContent = {
			PackageImage(
				context = context,
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
				Switch(
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
	var lazyListState = rememberLazyListState()
	AppTheme {
		AppList(
			lazyListState = lazyListState,
			filteredApps = apps,
			showList = true,
			packagesWithOverride = listOf("package.name.one"),
			toggleIgnore = {},
			onConfigureApp = {},
			onRemoveOverrides = {}
		)
	}
}





