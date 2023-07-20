/*
 * Copyright 2011-2023 Mark Injerd
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pilot51.voicenotify.AppListViewModel.IgnoreType
import kotlinx.coroutines.delay

private lateinit var vmStoreOwner: ViewModelStoreOwner

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppListActions() {
	val vm: AppListViewModel = viewModel(vmStoreOwner)
	var showSearchBar by remember { mutableStateOf(false) }
	if (showSearchBar) {
		val focusRequester = remember { FocusRequester() }
		val keyboard = LocalSoftwareKeyboardController.current
		TextField(
			value = vm.searchQuery ?: "",
			onValueChange = {
				vm.searchQuery = it
				vm.filterApps(it)
			},
			modifier = Modifier
				.fillMaxWidth()
				.focusRequester(focusRequester),
			maxLines = 1,
			singleLine = true,
			trailingIcon = {
				IconButton(onClick = {
					showSearchBar = false
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
		IconButton(onClick = {
			showSearchBar = true
		}) {
			Icon(
				imageVector = Icons.Filled.Search,
				contentDescription = stringResource(R.string.filter)
			)
		}
		IconButton(onClick = {
			vm.massIgnore(IgnoreType.IGNORE_ALL)
		}) {
			Icon(
				imageVector = Icons.Filled.CheckBoxOutlineBlank,
				contentDescription = stringResource(R.string.ignore_all)
			)
		}
		IconButton(onClick = {
			vm.massIgnore(IgnoreType.IGNORE_NONE)
		}) {
			Icon(
				imageVector = Icons.Filled.CheckBox,
				contentDescription = stringResource(R.string.ignore_none)
			)
		}
	}
}

@Composable
fun AppListScreen() {
	vmStoreOwner = LocalViewModelStoreOwner.current!!
	val vm: AppListViewModel = viewModel(vmStoreOwner)
	AppList(vm.filteredApps, vm.showList) { app ->
		vm.setIgnore(app, IgnoreType.IGNORE_TOGGLE)
	}
}

@Composable
private fun AppList(
	filteredApps: List<App>,
	showList: Boolean,
	toggleIgnore: (app: App) -> Unit
) {
	if (!showList) return
	LazyColumn(modifier = Modifier.fillMaxSize()) {
		items(filteredApps) {
			AppListItem(it, toggleIgnore)
		}
	}
}

@Composable
private fun AppListItem(app: App, toggleIgnore: (app: App) -> Unit) {
	Row(modifier = Modifier
		.fillMaxWidth()
		.clickable { toggleIgnore(app) }
		.padding(
			horizontal = 20.dp,
			vertical = 10.dp
		)
	) {
		Column(modifier = Modifier.weight(1f)) {
			Text(
				text = app.label,
				modifier = Modifier.fillMaxWidth(),
				color = Color.White,
				fontSize = 24.sp
			)
			Text(
				text = app.packageName,
				modifier = Modifier.fillMaxWidth(),
				color = Color.White
			)
		}
		Checkbox(
			checked = app.enabled,
			modifier = Modifier
				.size(40.dp)
				.align(Alignment.CenterVertically)
				.focusable(false),
			onCheckedChange = null
		)
	}
}

@Preview
@Composable
private fun AppListPreview() {
	val apps = listOf(
		App(1, "package.name.one", "App Name 1", true),
		App(2, "package.name.two", "App Name 2", false)
	)
	MaterialTheme(colorScheme = darkColorScheme()) {
		AppList(apps, true) {}
	}
}
