/*
 * LazyColumns: Jetpack Compose enhanced LazyColumns and usage examples
 *   Copyright (C) 2022  Vasilis Tsapalos
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *
 *   the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *  modified by Terry Cai
 */
package com.pilot51.voicenotify.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.pilot51.voicenotify.ui.AlphabeticIndexHelper

@Stable
data class IndexedLazyColumnsSettings(
    val indicesPosition: Alignment = Alignment.CenterEnd
)

@OptIn(ExperimentalFoundationApi::class)
@RequiresApi(Build.VERSION_CODES.N)
@Composable
fun <T> IndexedLazyColumn(
    indices: ImmutableList<T>,
    itemsListState: LazyListState,
    mainModifier: Modifier = Modifier,
    indicesModifier: Modifier = Modifier,
    predicate: (T) -> Int,
    reversePredicate: (() -> Int)? = null,
    settings: IndexedLazyColumnsSettings = IndexedLazyColumnsSettings(),
    stickyHeader: @Composable () -> Unit,
    lazyColumnContent: @Composable () -> Unit,
    indexedItemContent: @Composable (T, Boolean, Boolean) -> Unit
) {
    val coroutineContext = rememberCoroutineScope()
    val indexState = rememberLazyListState()
    val shouldUpdateIndexedSelection = remember {
        mutableStateOf(false)
    }
    val isItemsListScrolledByUser = remember {
        mutableStateOf(true)
    }
    val isIndicesListScrolledByUser = remember {
        mutableStateOf(true)
    }
    val selectedIndex = remember {
        mutableStateOf(0)
    }
    val isSelectedItemExist = remember {
        mutableStateOf(true)
    }

    Box(modifier = mainModifier, contentAlignment = settings.indicesPosition) {
        lazyColumnContent()
        if (!itemsListState.isScrollInProgress) {
            LaunchedEffect(isItemsListScrolledByUser.value) {
                if (isItemsListScrolledByUser.value && reversePredicate != null) {
                    // scrolling on indices is not by user (false)
                    isIndicesListScrolledByUser.value = false

                    val index = reversePredicate()
                    indexState.scrollToItem(index)
                    selectedIndex.value = index
                }
                // reset scrolling on items list to be by user (true)
                isItemsListScrolledByUser.value = true
            }
        }

        LazyColumn(
            state = indexState,
            horizontalAlignment = Alignment.End,
            modifier = indicesModifier
        ) {
            if (!indexState.isScrollInProgress && shouldUpdateIndexedSelection.value) {
                val index = indexState.layoutInfo.visibleItemsInfo.first().index
                // scrolling on items list is not by user (false)
                isItemsListScrolledByUser.value = false
                isSelectedItemExist.value = true

                if (isIndicesListScrolledByUser.value) {
                    isSelectedItemExist.value = scrollMainListBasedOnIndex(
                        coroutineContext, predicate,
                        indices, itemsListState,
                        selectedIndex, index
                    )
                }

                // scrolling on indices is by user (true)
                isIndicesListScrolledByUser.value = true
                shouldUpdateIndexedSelection.value = false
            } else if (indexState.isScrollInProgress) {
                shouldUpdateIndexedSelection.value = true
            }

            stickyHeader {
                stickyHeader()
            }

            itemsIndexed(indices) { index, item ->
                Box(modifier = Modifier
                    .padding(5.dp)
                    .clickable {
                        // scrolling on items list is not by user (false)
                        isItemsListScrolledByUser.value = false

                        isSelectedItemExist.value = scrollMainListBasedOnIndex(
                            coroutineContext, predicate,
                            indices, itemsListState,
                            selectedIndex, index
                        )
                    }) {
                    indexedItemContent(item, index == selectedIndex.value, isSelectedItemExist.value)
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.N)
@ExperimentalFoundationApi
@Composable
fun <T> IndexedDataLazyColumn(
    indices: ImmutableList<T>,
    data: ImmutableList<T>,
    mainModifier: Modifier = Modifier,
    indicesModifier: Modifier = Modifier,
    predicate: (T) -> Int,
    reversePredicate: ((LazyListState) -> Int)? = null,
    settings: IndexedLazyColumnsSettings = IndexedLazyColumnsSettings(),
    mainItemContent: @Composable LazyItemScope.(Int) -> Unit,
    indexedItemContent: @Composable (T, Boolean, Boolean) -> Unit
) {
    val coroutineContext = rememberCoroutineScope()
    val indexScrollerCoroutineContext = rememberCoroutineScope()
    val indexState = rememberLazyListState()
    val itemsState = rememberLazyListState()
    val shouldUpdateIndexedSelection = remember {
        mutableStateOf(false)
    }
    val isItemsListScrolledByUser = remember {
        mutableStateOf(true)
    }
    val isIndicesListScrolledByUser = remember {
        mutableStateOf(true)
    }
    val selectedIndex = remember {
        mutableStateOf(0)
    }
    val isSelectedItemExist = remember {
        mutableStateOf(true)
    }

    Box(modifier = mainModifier, contentAlignment = settings.indicesPosition) {
        LazyColumn(state = itemsState) {
            if (!itemsState.isScrollInProgress) {
                if (isItemsListScrolledByUser.value && reversePredicate != null) {
                    // scrolling on indices is not by user (false)
                    isIndicesListScrolledByUser.value = false
                    indexScrollerCoroutineContext.launch {
                        val index = reversePredicate(itemsState)
                        indexState.scrollToItem(index)
                        selectedIndex.value = index
                    }
                }
                // reset scrolling on items list to be by user (true)
                isItemsListScrolledByUser.value = true
            }
            itemsIndexed(data) { index, item ->
                mainItemContent(index)
            }
        }

        LazyColumn(
            state = indexState,
            horizontalAlignment = Alignment.End,
            modifier = indicesModifier
        ) {
            if (!indexState.isScrollInProgress && shouldUpdateIndexedSelection.value) {
                val index = indexState.layoutInfo.visibleItemsInfo.first().index
                // scrolling on items list is not by user (false)
                isItemsListScrolledByUser.value = false
                isSelectedItemExist.value = true

                if (isIndicesListScrolledByUser.value) {
                    isSelectedItemExist.value = scrollMainListBasedOnIndex(
                        coroutineContext, predicate,
                        indices, itemsState,
                        selectedIndex, index
                    )
                }

                // scrolling on indices is by user (true)
                isIndicesListScrolledByUser.value = true
                shouldUpdateIndexedSelection.value = false
            } else if (indexState.isScrollInProgress) {
                shouldUpdateIndexedSelection.value = true
            }

            itemsIndexed(indices) { index, item ->
                Box(modifier = Modifier
                    .padding(5.dp)
                    .clickable {
                        // scrolling on items list is not by user (false)
                        isItemsListScrolledByUser.value = false

                        isSelectedItemExist.value = scrollMainListBasedOnIndex(
                            coroutineContext, predicate,
                            indices, itemsState,
                            selectedIndex, index
                        )
                    }) {
                    indexedItemContent(item, index == selectedIndex.value, isSelectedItemExist.value)
                }
            }
        }
    }
}

private fun <T> scrollMainListBasedOnIndex(
    coroutineContext: CoroutineScope,
    predicate: (T) -> Int,
    indices: List<T>,
    itemsListState: LazyListState,
    selectedIndex: MutableState<Int>,
    index: Int,
): Boolean {
    val itemIndex = predicate(indices[index])
    coroutineContext.launch {
        if (itemIndex >= 0) {
            itemsListState.scrollToItem(itemIndex)
        }
        selectedIndex.value = index
    }
    return itemIndex >= 0
}