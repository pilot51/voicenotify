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
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pilot51.voicenotify.AppListViewModel.IgnoreType
import com.pilot51.voicenotify.db.App
import com.pilot51.voicenotify.ui.Layout
import com.pilot51.voicenotify.ui.LazyAlphabetIndexRow
import com.pilot51.voicenotify.ui.ListItem
import com.pilot51.voicenotify.ui.ScrollingBubble
import com.pilot51.voicenotify.ui.SearchBar
import com.pilot51.voicenotify.ui.Switch
import com.pilot51.voicenotify.ui.bottomBorder
import com.pilot51.voicenotify.ui.theme.VoiceNotifyTheme

private lateinit var vmStoreOwner: ViewModelStoreOwner


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


@Composable
fun AppListScreen(
    list: List<App> = emptyList(),
    onConfigureApp: (app: App) -> Unit
) {
    vmStoreOwner = LocalViewModelStoreOwner.current!!
    val vm: AppListViewModel = viewModel(vmStoreOwner)
    val packagesWithOverride by vm.packagesWithOverride
    var showConfirmDialog by remember { mutableStateOf<IgnoreType?>(null) }
    var filteredApps = list.takeIf { it.isNotEmpty() } ?: vm.filteredApps
    val lazyListState = rememberLazyListState()
    val showList by vm::showList
    val appListLocalMap by vm::appListLocalMap

    Layout(modifier = Modifier) {
        AppListActions(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp, 4.dp, 16.dp, 8.dp)
        )
        AppList(
            lazyListState = lazyListState,
            filteredApps = filteredApps,
            appListLocalMap = appListLocalMap,
            showList = showList,
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
                        Text(text = stringResource(R.string.ignore_none))
                        Switch(
                            checked = vm.appEnable,
                            onCheckedChange = {
                                showConfirmDialog =
                                    if (it) IgnoreType.IGNORE_NONE else IgnoreType.IGNORE_ALL
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
            toggleIgnore = { app -> vm.setIgnore(app, IgnoreType.IGNORE_TOGGLE) },
            onConfigureApp = onConfigureApp,
            onRemoveOverrides = vm::removeOverrides
        )
    }
}

@Composable
fun SkeletonListItem() {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Gray.copy(alpha = 0.1f)),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Gray.copy(alpha = 0.3f), shape = CircleShape)
            )
        },
        headlineContent = {
            Box(
                modifier = Modifier
                    .height(18.dp)
                    .fillMaxWidth(0.5f)
                    .background(Color.Gray.copy(alpha = 0.3f))
            )
        },
        supportingContent = {
            Box(
                modifier = Modifier
                    .height(12.dp)
                    .fillMaxWidth(0.7f)
                    .background(Color.Gray.copy(alpha = 0.3f))
            )
        },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(Color.Gray.copy(alpha = 0.3f))
                )
            }
        }
    )
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppList(
    lazyListState: LazyListState = rememberLazyListState(),
    filteredApps: List<App>,
    appListLocalMap: MutableMap<String, Char>,
    showList: Boolean,
    packagesWithOverride: List<String>,
    toggleIgnore: (app: App) -> Unit,
    onConfigureApp: (app: App) -> Unit,
    onRemoveOverrides: (app: App) -> Unit,
    stickyHeader: @Composable () -> Unit = {}
) {

    if (!showList) return
    var alphabetRelativeDragYOffset: Float? by remember { mutableStateOf(null) }
    var alphabetDistanceFromTopOfScreen: Float by remember { mutableStateOf(0F) }
    var currentLetter by remember { mutableStateOf<Char?>(null) }
    var appListTopOffset by remember { mutableStateOf(0F) }

    BoxWithConstraints(
        Modifier.onGloballyPositioned { coordinates ->
            appListTopOffset = coordinates.positionInWindow().y
        }
    ) {
        LazyAlphabetIndexRow(
            items = filteredApps,
            keySelector = { appListLocalMap[it.packageName].toString() },
            lazyListState = lazyListState,
            alphabetModifier = Modifier
                .fillMaxHeight()
                .width(16.dp)
                .clip(RoundedCornerShape(8.dp)),
            alphabetPaddingValues = PaddingValues(0.dp, 0.dp, 0.dp, 60.dp),
            onAlphabetListDrag = { relativeDragYOffset, containerDistance, char ->
                alphabetRelativeDragYOffset = relativeDragYOffset
                alphabetDistanceFromTopOfScreen = containerDistance
                currentLetter = char
            }
        ) {
            Box(
                modifier = Modifier
                    .padding(12.dp, 0.dp, 0.dp, 0.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(VoiceNotifyTheme.colors.boxItem)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = lazyListState,
                    contentPadding = PaddingValues(end = 0.dp)
                ) {
                    stickyHeader { stickyHeader() }
                    itemsIndexed(items = filteredApps) { index, it ->
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
        val yOffset = alphabetRelativeDragYOffset
        if (yOffset != null && currentLetter != null) {
            ScrollingBubble(
                bubbleOffsetX = this.maxWidth - 80.dp,
                bubbleOffsetY = this.maxHeight / 4,
                currAlphabetScrolledOn = currentLetter,
            )
        }
    }
}

@Composable
fun PackageImage(context: Context, packageName: String, modifier: Modifier = Modifier) {
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
            // .toggleable(
            //     value = app.enabled,
            //     role = Role.Checkbox,
            //     onValueChange = { toggleIgnore(app) }
            // )
            .clickable {
                onConfigureApp(app)
            }
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
            appListLocalMap = mutableMapOf("package.name.one" to 'A', "package.name.two" to 'B'),
            packagesWithOverride = listOf("package.name.one"),
            toggleIgnore = {},
            onConfigureApp = {},
            onRemoveOverrides = {}
        )
    }
}



