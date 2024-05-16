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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pilot51.voicenotify.AppListViewModel.IgnoreType
import kotlinx.coroutines.delay

private lateinit var vmStoreOwner: ViewModelStoreOwner


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppListActions(modifier: Modifier = Modifier) {
	val vm: AppListViewModel = viewModel(vmStoreOwner)
	// var showSearchBar by remember { mutableStateOf(false) }
	// val focusRequester = remember { FocusRequester() }
	// val keyboard = LocalSoftwareKeyboardController.current
//	Row(
//		modifier = modifier
//	) {
//		TextField(
//			value = vm.searchQuery ?: "",
//			onValueChange = {
//				vm.searchQuery = it
//				vm.filterApps(it)
//			},
//			modifier = Modifier
//				.fillMaxWidth()
//				.padding(horizontal = 4.dp)
//				.focusRequester(focusRequester),
//			maxLines = 1,
//			singleLine = true,
//			leadingIcon = {
//				Icon(
//					imageVector = Icons.Filled.Search,
//					contentDescription = null
//				)
//			},
//			trailingIcon = {
//				IconButton(onClick = {
//					showSearchBar = false
//					vm.searchQuery = null
//					vm.filterApps(null)
//				}) {
//					Icon(
//						imageVector = Icons.Filled.Close,
//						contentDescription = stringResource(R.string.close)
//					)
//				}
//			}
//		)

//	}

//	LaunchedEffect(focusRequester) {
//		focusRequester.requestFocus()
//		delay(100)
//		keyboard?.show()
//	}
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
fun AppListScreen() {
	vmStoreOwner = LocalViewModelStoreOwner.current!!
	val vm: AppListViewModel = viewModel(vmStoreOwner)
	Column(
		modifier = Modifier.fillMaxSize()
	) {

		AppListActions(
			modifier = Modifier.fillMaxWidth()
							 .padding(8.dp, 4.dp)
							 
		)
		Box(
			modifier = Modifier
				.weight(1f)
				.fillMaxWidth()
		) {
			AppList(
				filteredApps = vm.filteredApps,
				showList = vm.showList,
				stickyHeader = {
					val modifier = Modifier
						.fillMaxWidth()
						.background(
							color = MaterialTheme.colorScheme.surface,
							shape = MaterialTheme.shapes.medium
						).
						padding(8.dp, 4.dp)
					Row(
						modifier = modifier,
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically
						) {
						Text(
							text = stringResource(R.string.ignore_all),
							modifier = Modifier.padding(8.dp)
						)
						Switch(
							checked = vm.appEnable,
							onCheckedChange = {
								vm.massIgnore(if (it) IgnoreType.IGNORE_NONE else IgnoreType.IGNORE_ALL)
							},
							modifier = Modifier.padding(8.dp)
						)
					}
				}
			) { app ->
				vm.setIgnore(app, IgnoreType.IGNORE_TOGGLE)
			}
		}
	}


}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppList(
	filteredApps: List<App>,
	showList: Boolean,
	stickyHeader: @Composable () -> Unit = {},
	toggleIgnore: (app: App) -> Unit,
) {
	if (!showList) return
	Column(
		modifier = Modifier.fillMaxHeight(),
		verticalArrangement = Arrangement.Top
	) {
		LazyColumn(

		) {
			stickyHeader {
				stickyHeader()
			}
			items(filteredApps) {
				AppListItem(it, toggleIgnore)
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
private fun AppListItem(app: App, toggleIgnore: (app: App) -> Unit) {
	ListItem(
		modifier = Modifier.toggleable(
			value = app.enabled,
			role = Role.Checkbox,
			onValueChange = { toggleIgnore(app) }
		),
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
			Text(app.packageName)
		},
		trailingContent = {
			Switch(
				checked = app.enabled,
				onCheckedChange = { toggleIgnore(app) },
				modifier = Modifier.focusable(false)
			)
//			Checkbox(
//				checked = app.enabled,
//				modifier = Modifier.focusable(false),
//				onCheckedChange = { toggleIgnore(app) }
//			)
		}
	)
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun AppListPreview() {
	val apps = listOf(
		App(1, "package.name.one", "App Name 1", true),
		App(2, "package.name.two", "App Name 2", false)
	)
	AppTheme {
		AppList(apps, true) {}
	}
}
