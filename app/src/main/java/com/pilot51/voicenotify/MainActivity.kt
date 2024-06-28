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

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pilot51.voicenotify.db.App
import com.pilot51.voicenotify.db.Settings
import com.pilot51.voicenotify.ui.LargeTopAppBar
import com.pilot51.voicenotify.ui.SmallTopAppBar
import com.pilot51.voicenotify.ui.theme.VoiceNotifyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		WindowCompat.setDecorFitsSystemWindows(window, false)
		ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
			v.setPadding(0, 0, 0, 0)
			insets
		}

		val vm: PreferencesViewModel by viewModels()
		lifecycleScope.launch(Dispatchers.IO) {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				vm.configuringSettingsComboState.collect {
					volumeControlStream = it.ttsStream ?: Settings.DEFAULT_TTS_STREAM
				}
			}
		}

		setContent {
			val isSystemInDarkTheme = isSystemInDarkTheme()
			LaunchedEffect(isSystemInDarkTheme) {
				setSystemBarAppearance(isSystemInDarkTheme)
			}
			AppTheme {
				AppMain()
			}
		}
	}
	private fun setSystemBarAppearance(isDark: Boolean) {
		WindowCompat.getInsetsController(window, window.decorView.rootView).apply {
			isAppearanceLightStatusBars = !isDark
			isAppearanceLightNavigationBars = !isDark
		}
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
			window.statusBarColor = (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
		}
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
			window.navigationBarColor = (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
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
	VoiceNotifyTheme(
		darkTheme = isSystemInDarkTheme(),
		dynamicColor = true,
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
	modifier: Modifier = Modifier,
	scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
) {
	var colors = TopAppBarDefaults.mediumTopAppBarColors(
		containerColor = VoiceNotifyTheme.colors.background,
		scrolledContainerColor = VoiceNotifyTheme.colors.background,
	)
	if (currentScreen !== Screen.valueOf(Screen.MAIN.name)) {
		SmallTopAppBar(
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
							imageVector = Icons.Filled.ArrowBack,
							contentDescription = stringResource(R.string.back)
						)
					}
				}
			},
			colors = colors
		)
	} else {
		LargeTopAppBar(
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
							imageVector = Icons.Filled.ArrowBack,
							contentDescription = stringResource(R.string.back)
						)
					}
				}
			},
			scrollBehavior = scrollBehavior,
			actions = {

			},
			colors = colors
		)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
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
	val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
		rememberTopAppBarState(),
		canScroll = { true })
	Scaffold(
			modifier = Modifier
			.fillMaxSize()
			.background(VoiceNotifyTheme.colors.background)
			.nestedScroll(scrollBehavior.nestedScrollConnection),
		topBar = {
			AppBar(
				currentScreen = currentScreen,
				configApp = configApp,
				canNavigateBack = navController.previousBackStackEntry != null,
				navigateUp = navController::navigateUp,
				modifier = Modifier
					.fillMaxWidth(),
				scrollBehavior = scrollBehavior
			)
		}
	) { innerPadding ->
		NavHost(
			navController = navController,
			startDestination = Screen.MAIN.name,
			modifier = Modifier.padding(innerPadding)
				.background(VoiceNotifyTheme.colorScheme.background)
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
