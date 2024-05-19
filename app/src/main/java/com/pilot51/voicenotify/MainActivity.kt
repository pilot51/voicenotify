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

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pilot51.voicenotify.db.App
import com.pilot51.voicenotify.db.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val vm: PreferencesViewModel by viewModels()
		lifecycleScope.launch(Dispatchers.IO) {
			vm.configuringSettingsComboState.collect {
				volumeControlStream = it.ttsStream ?: Settings.DEFAULT_TTS_STREAM
			}
		}
		setContent {
			AppTheme {
				AppMain()
			}
		}
	}
}

private enum class Screen(@StringRes val title: Int) {
	MAIN(R.string.app_name),
	APP_LIST(R.string.app_list),
	TTS(R.string.tts)
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
	MaterialTheme(
		colorScheme = if (isSystemInDarkTheme()) {
			darkColorScheme(primary = Color(0xFF1CB7D5), primaryContainer = Color(0xFF1E4696))
		} else {
			lightColorScheme(primary = Color(0xFF2A54A5), primaryContainer = Color(0xFF64F0FF))
		},
		typography = MaterialTheme.typography.copy(
			// Increased font size for dialog buttons
			labelLarge = TextStyle(
				fontFamily = FontFamily.SansSerif,
				fontWeight = FontWeight.Medium,
				fontSize = 20.sp,
				lineHeight = 20.sp,
				letterSpacing = 0.1.sp,
			)
		),
		content = content
	)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppBar(
	currentScreen: Screen,
	configApp: App?,
	canNavigateBack: Boolean,
	navigateUp: () -> Unit,
	modifier: Modifier = Modifier
) {
	TopAppBar(
		title = {
			Text(configApp?.run {
				stringResource(R.string.app_overrides, label)
			} ?: stringResource(currentScreen.title))
		},
		modifier = modifier,
		navigationIcon = {
			if (canNavigateBack) {
				IconButton(onClick = navigateUp) {
					Icon(
						imageVector = Icons.AutoMirrored.Filled.ArrowBack,
						contentDescription = stringResource(R.string.back)
					)
				}
			}
		},
		actions = {
			if (currentScreen == Screen.APP_LIST) {
				AppListActions()
			}
		},
		colors = TopAppBarDefaults.mediumTopAppBarColors(
			containerColor = MaterialTheme.colorScheme.primaryContainer
		)
	)
}

@Composable
fun AppMain(
	vm: IPreferencesViewModel = viewModel<PreferencesViewModel>()
) {
	val navController = rememberNavController()
	val backStackEntry by navController.currentBackStackEntryAsState()
	val currentScreen = Screen.valueOf(
		backStackEntry?.destination?.route?.substringBefore("?") ?: Screen.MAIN.name
	)
	val appPkg = backStackEntry?.arguments?.getString("appPkg")
	val configApp = remember(appPkg) { appPkg?.let { vm.getApp(it) } }
	LaunchedEffect(appPkg) {
		vm.setCurrentConfigApp(configApp)
	}
	val argsAppPkg = listOf(
		navArgument("appPkg") {
			type = NavType.StringType
			nullable = true
		}
	)
	Scaffold(
		topBar = {
			AppBar(
				currentScreen = currentScreen,
				configApp = configApp,
				canNavigateBack = navController.previousBackStackEntry != null,
				navigateUp = navController::navigateUp
			)
		}
	) { innerPadding ->
		NavHost(
			navController = navController,
			startDestination = Screen.MAIN.name,
			modifier = Modifier.padding(innerPadding)
		) {
			composable(
				route = "${Screen.MAIN.name}?appPkg={appPkg}",
				arguments = argsAppPkg
			) {
				MainScreen(
					vm = vm,
					configApp = configApp,
					onClickAppList = { navController.navigate(Screen.APP_LIST.name) },
					onClickTtsConfig = {
						navController.navigate("${Screen.TTS.name}?appPkg=$appPkg")
					}
				)
			}
			composable(route = Screen.APP_LIST.name) {
				AppListScreen(
					onConfigureApp = { app ->
						navController.navigate("${Screen.MAIN.name}?appPkg=${app.packageName}")
					}
				)
			}
			composable(
				route = "${Screen.TTS.name}?appPkg={appPkg}",
				arguments = argsAppPkg
			) {
				TtsConfigScreen(vm)
			}
		}
	}
}

@VNPreview
@Composable
private fun AppPreview() {
	AppTheme {
		AppMain(PreferencesPreviewVM)
	}
}
