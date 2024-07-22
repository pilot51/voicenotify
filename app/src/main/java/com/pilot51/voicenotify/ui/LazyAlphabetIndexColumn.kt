package com.pilot51.voicenotify.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.pilot51.voicenotify.AppTheme
import com.pilot51.voicenotify.ui.theme.VoiceNotifyTheme
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.launch


val vibrationCooldown = 50 // Cooldown time in milliseconds
val alphabetItemSize = 20.dp

val alphabetCharList = (listOf('#') + ('A'..'Z'))


val alphabetCharSize = alphabetCharList.size


internal fun Float.getIndexOfCharBasedOnYPosition(
    alphabetHeightInPixels: Float,
): Char {

    var index = ((this) / alphabetHeightInPixels).toInt()
    index = when {
        index > alphabetCharSize -> alphabetCharSize
        index < 0 -> 0
        else -> index
    }
    return alphabetCharList[index]
}



fun<T> List<T>.getFirstUniqueSeenCharIndex(callback: (T) -> String): ImmutableMap<Char, Int> {
    val firstLetterIndexes = mutableMapOf<Char, Int>()
    this
        .map {
            // if is number return #
            if (it.toString().first().isDigit()) {
                '#'
            } else {
                callback(it).uppercase().first()
            }
        }
        .forEachIndexed { index, char ->
            if (!firstLetterIndexes.contains(char)) {
                firstLetterIndexes[char] = index
            }
            // else don't care about letters that don't exist
        }
    return firstLetterIndexes.toPersistentMap()
}


@Composable
fun<T> LazyAlphabetIndexColumn(
    items: List<T>,
    keySelector: (item: T) -> String = keySelector@{ it.toString() },
    onAlphabetListDrag: (Float?, Float, Char?) -> Unit,
    lazyListState: LazyListState = rememberLazyListState(),
    vibratorEnabled: Boolean = false,
    alphabetModifier: Modifier = Modifier,
    alphabetPaddingValues: PaddingValues = PaddingValues(0.dp, 0.dp, 0.dp, 0.dp),
    content: LazyListScope.(firstLetterIndexes: ImmutableMap<Char, Int>) -> Unit
) {
    val mapOfFirstLetterIndex: ImmutableMap<Char, Int> =
        remember(items) { items.getFirstUniqueSeenCharIndex(keySelector) }
    val coroutineScope = rememberCoroutineScope()

    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlphabetLazyColumn(
            Modifier
                .fillMaxHeight()
                .weight(1f),
            lazyListState,
            mapOfFirstLetterIndex
        ) { firstLetterIndexes ->
            content(firstLetterIndexes)
        }
        AlphabetScroller(
            modifier = alphabetModifier
                .align(Alignment.CenterVertically),
            vibratorEnabled = vibratorEnabled,
            alphabetPaddingValues = alphabetPaddingValues,
            onAlphabetListDrag = { relativeDragYOffset, containerDistanceFromTopOfScreen, indexOfChar ->
                onAlphabetListDrag(relativeDragYOffset, containerDistanceFromTopOfScreen, indexOfChar)
                coroutineScope.launch {
                    mapOfFirstLetterIndex[indexOfChar]?.let {
                        lazyListState.scrollToItem(it)
                    }
                }
            },
        )
    }
}


@Composable
fun<T> LazyAlphabetIndexRow(
    items: List<T>,
    keySelector: (item: T) -> String = keySelector@{ it.toString() },
    onAlphabetListDrag: (Float?, Float, Char?) -> Unit,
    lazyListState: LazyListState = rememberLazyListState(),
    vibratorEnabled: Boolean = false,
    alphabetModifier: Modifier = Modifier,
    alphabetPaddingValues: PaddingValues = PaddingValues(0.dp, 0.dp, 0.dp, 0.dp),
    content: @Composable (firstLetterIndexes: ImmutableMap<Char, Int>) -> Unit
) {
    val mapOfFirstLetterIndex: ImmutableMap<Char, Int> =
        remember(items) { items.getFirstUniqueSeenCharIndex(keySelector) }
    val coroutineScope = rememberCoroutineScope()

    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
        ) {
            content(mapOfFirstLetterIndex)
        }
        AlphabetScroller(
            modifier = alphabetModifier
                .align(Alignment.CenterVertically),
            vibratorEnabled = vibratorEnabled,
            alphabetPaddingValues = alphabetPaddingValues,
            onAlphabetListDrag = { relativeDragYOffset, containerDistanceFromTopOfScreen, indexOfChar ->
                onAlphabetListDrag(relativeDragYOffset, containerDistanceFromTopOfScreen, indexOfChar)
                coroutineScope.launch {
                    mapOfFirstLetterIndex[indexOfChar]?.let {
                        lazyListState.scrollToItem(it)
                    }
                }
            },
        )
    }
}

@Composable
fun RowScope.AlphabetLazyColumn(
    modifier: Modifier,
    lazyListState: LazyListState,
    mapOfFirstLetterIndex: ImmutableMap<Char, Int>,
    content: LazyListScope.(firstLetterIndexes: ImmutableMap<Char, Int>) -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        state = lazyListState
    ) {
        content(mapOfFirstLetterIndex)
    }
}


@Composable
private fun AlphabetScroller(
    modifier: Modifier,
    vibratorEnabled: Boolean,
    alphabetPaddingValues: PaddingValues,
    onAlphabetListDrag: (relativeDragYOffset: Float?, distanceFromTopOfScreen: Float, alphabetChar: Char?) -> Unit,
) {

    var currentLetter by remember { mutableStateOf<Char?>(null) }

    var alphabetRelativeDragYOffset: Float? by remember { mutableStateOf(null) }
    var distanceFromTopOfScreen by remember { mutableStateOf(0F) }
    val context = LocalContext.current
    val vibrator = remember {
        if (vibratorEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(VibratorManager::class.java)?.defaultVibrator
                } else {
                    TODO("VERSION.SDK_INT < S")
                }
            } else {
                TODO("VERSION.SDK_INT < M")
            }
        } else {
            null
        }
    }
    var lastVibrationTime by remember { mutableStateOf(0L) }
    Column(
        modifier = Modifier
            .wrapContentHeight()

    ) {
        Spacer(modifier = Modifier.height(alphabetPaddingValues.calculateTopPadding()))
        Column(
            modifier = modifier
//                .background(VoiceNotifyTheme.colorScheme.tertiary)
                .onGloballyPositioned {
                    distanceFromTopOfScreen = it.positionInRoot().y
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            onAlphabetListDrag(it.y, distanceFromTopOfScreen, null)
                        },
                        onDragEnd = {
                            alphabetRelativeDragYOffset = null
                            onAlphabetListDrag(null, distanceFromTopOfScreen, null)
                        }
                    ) { change, _ ->
                        if (vibratorEnabled) {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastVibrationTime >= vibrationCooldown) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator?.vibrate(
                                        VibrationEffect.createOneShot(
                                            50,
                                            VibrationEffect.DEFAULT_AMPLITUDE
                                        )
                                    )
                                }
                                lastVibrationTime = currentTime
                            }
                        }
                        alphabetRelativeDragYOffset = change.position.y
                        val itemHeight = size.height / alphabetCharSize
                        val index = ((alphabetRelativeDragYOffset!! / itemHeight).toInt()).coerceIn(
                            alphabetCharList.indices
                        )
                        currentLetter = alphabetCharList.getOrNull(index)
                        onAlphabetListDrag(
                            alphabetRelativeDragYOffset,
                            distanceFromTopOfScreen,
                            currentLetter
                        )
                    }
                },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            for (i in alphabetCharList) {
                if (i == currentLetter && alphabetRelativeDragYOffset != null) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(alphabetItemSize)
                            .align(Alignment.CenterHorizontally)
                            .background(VoiceNotifyTheme.colorScheme.primary, CircleShape),
                    ) {
                        Text(
                            text = i.toString(),
                            fontSize = 14.sp,
                        )
                    }
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(alphabetItemSize),
                    ) {
                        Text(
                            text = i.toString(),
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(alphabetPaddingValues.calculateBottomPadding()))

    }

}



@Composable
fun ScrollingBubble(
    boxConstraintMaxWidth: Dp,
    bubbleOffsetYFloat: Float,
    currAlphabetScrolledOn: Char?,
    bubbleSize: Dp = 50.dp
) {
    Surface(
        shape = CircleShape,
        modifier = Modifier
            .size(bubbleSize)
            .offset(
                x = (boxConstraintMaxWidth - (bubbleSize + alphabetItemSize)),
                y = with(LocalDensity.current) {
                    bubbleOffsetYFloat.toDp() - (bubbleSize / 2)
                },
            ),
        color = VoiceNotifyTheme.colorScheme.primary,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = currAlphabetScrolledOn.toString(),
                style = VoiceNotifyTheme.typography.headlineLarge,
                color = VoiceNotifyTheme.colors.colorNeutralInverse
            )
        }
    }
}
@Composable
fun ScrollRect(
    currAlphabetScrolledOn: Char
) {
    Popup(
        alignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(50.dp, 50.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Gray.copy(alpha = 0.5f))
        ) {
            Text(
                text = currAlphabetScrolledOn.toString(),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}


@Composable
fun ContactItem(
    contact: String,
    isAlphabeticallyFirstInCharGroup: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.width(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isAlphabeticallyFirstInCharGroup) {
                Text(
                    text = contact.first().toString(),
                    style = VoiceNotifyTheme.typography.bodyLarge
                )
            }
        }

        Surface(
            shape = CircleShape,
            modifier = Modifier.size(32.dp),
            color = VoiceNotifyTheme.colorScheme.secondary
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = contact.first().toString(),
                    style = VoiceNotifyTheme.typography.bodyLarge
                )
            }
        }
        Text(
            modifier = Modifier.padding(16.dp),
            text = contact,
            style = VoiceNotifyTheme.typography.titleLarge,
        )
    }
}



data class AppInfo(
    val name: String,
    val icon: Drawable,
    val packageName: String
)

fun getInstalledApps(context: Context): List<AppInfo> {
    val pm: PackageManager = context.packageManager
    val packages: List<ApplicationInfo> = pm.getInstalledApplications(PackageManager.GET_META_DATA)

    return packages.map { packageInfo ->
        AppInfo(
            name = packageInfo.loadLabel(pm).toString(),
            icon = packageInfo.loadIcon(pm),
            packageName = packageInfo.packageName
        )
    }
}

@Composable
fun AppListItem(app: AppInfo, isAlphabeticallyFirstInCharGroup: Boolean) {
    Row(modifier = Modifier.padding(8.dp)) {
        if (isAlphabeticallyFirstInCharGroup) {
            // label
            Text(
                text = app.name.first().toString(),
                style = VoiceNotifyTheme.typography.bodyLarge
            )
        }
        val iconBitmap = (app.icon as BitmapDrawable).bitmap
        Image(
            bitmap = iconBitmap.asImageBitmap(),
            contentDescription = app.name,
            modifier = Modifier.padding(end = 8.dp)
        )
        Column {
            Text(text = app.name, style = VoiceNotifyTheme.typography.titleLarge)
            Text(text = app.packageName, style = VoiceNotifyTheme.typography.bodySmall)
        }
    }
}


@Composable
fun LazyAlphabetIndexColumnDemo() {
    val items = listOf("360", "HK01", "Apple", "Banana", "Cherry",
        "Date", "Fig", "Grape", "Lemon", "Mango", "Nvidia", "Man", "Orange", "Peach", "Quince",
        "Raspberry", "Strawberry", "Tomato", "Ugli Fruit",
        "Watermelon", "Xigua", "Yam", "Zucchini").toImmutableList()
    val lazyListState = rememberLazyListState()
    val context = LocalDensity.current
    val alphabetHeightInPixels = remember { with(context) { alphabetItemSize.toPx() } }
    var alphabetRelativeDragYOffset: Float? by remember { mutableStateOf(null) }
    var alphabetDistanceFromTopOfScreen: Float by remember { mutableStateOf(0F) }
    var currentLetter by remember { mutableStateOf<Char?>(null) }
    AppTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = VoiceNotifyTheme.colorScheme.background
        ) {
            BoxWithConstraints {
                LazyAlphabetIndexColumn(
                    items = items,
                    alphabetModifier = Modifier,
                    onAlphabetListDrag = { relativeDragYOffset, containerDistance, char ->
                        alphabetRelativeDragYOffset = relativeDragYOffset
                        alphabetDistanceFromTopOfScreen = containerDistance
                        currentLetter = char
                    }
                ) { firstLetterIndexes ->
                    itemsIndexed(items) { index, contact ->
                        ContactItem(
                            contact = contact,
                            isAlphabeticallyFirstInCharGroup =
                            firstLetterIndexes[contact.uppercase().first()] == index,
                        )
                    }

                }
                val yOffset = alphabetRelativeDragYOffset
                if (yOffset != null && currentLetter != null) {
                    ScrollingBubble(
                        boxConstraintMaxWidth = this.maxWidth,
                        bubbleOffsetYFloat = yOffset + alphabetDistanceFromTopOfScreen,
                        currAlphabetScrolledOn = currentLetter,
                    )
                }
            }
        }
    }
}


@Composable
fun LazyAlphabetIndexRowDemo() {
    val items = listOf("360", "HK01", "Apple", "Banana", "Cherry",
        "Date", "Fig", "Grape", "Lemon", "Mango", "Nvidia", "Man", "Orange", "Peach", "Quince",
        "Raspberry", "Strawberry", "Tomato", "Ugli Fruit",
        "Watermelon", "Xigua", "Yam", "Zucchini").toImmutableList()
    val lazyListState = rememberLazyListState()
    val context = LocalDensity.current
    val alphabetHeightInPixels = remember { with(context) { alphabetItemSize.toPx() } }
    var alphabetRelativeDragYOffset: Float? by remember { mutableStateOf(null) }
    var alphabetDistanceFromTopOfScreen: Float by remember { mutableStateOf(0F) }
    AppTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = VoiceNotifyTheme.colorScheme.background
        ) {
            BoxWithConstraints {
                LazyAlphabetIndexRow(
                    items = items,
                    alphabetModifier = Modifier,
                    onAlphabetListDrag = { relativeDragYOffset, containerDistance, char ->
                        alphabetRelativeDragYOffset = relativeDragYOffset
                        alphabetDistanceFromTopOfScreen = containerDistance
                    }
                ) { firstLetterIndexes ->
                    LazyColumn(
                        modifier = Modifier.fillMaxHeight(),
                        state = lazyListState
                    ) {
                        itemsIndexed(items) { index, contact ->
                            ContactItem(
                                contact = contact,
                                isAlphabeticallyFirstInCharGroup =
                                firstLetterIndexes[contact.uppercase().first()] == index,
                            )
                        }
                    }
                }
                val yOffset = alphabetRelativeDragYOffset
                if (yOffset != null) {
                    ScrollingBubble(
                        boxConstraintMaxWidth = this.maxWidth,
                        bubbleOffsetYFloat = yOffset + alphabetDistanceFromTopOfScreen,
                        currAlphabetScrolledOn = yOffset.getIndexOfCharBasedOnYPosition(
                            alphabetHeightInPixels = alphabetHeightInPixels,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
fun LazyInstalledAppsColumnDemo() {
    // get installed app list
    val installedApps = getInstalledApps(LocalContext.current).toImmutableList()
    // log
    val lazyListState = rememberLazyListState()
    val context = LocalDensity.current
    val alphabetHeightInPixels = remember { with(context) { alphabetItemSize.toPx() } }
    var alphabetRelativeDragYOffset: Float? by remember { mutableStateOf(null) }
    var alphabetDistanceFromTopOfScreen: Float by remember { mutableStateOf(0F) }
    AppTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = VoiceNotifyTheme.colorScheme.background
        ) {
            BoxWithConstraints {
                LazyAlphabetIndexColumn(
                    items = installedApps,
                    alphabetModifier = Modifier,
                    onAlphabetListDrag = { relativeDragYOffset, containerDistance, char ->
                        alphabetRelativeDragYOffset = relativeDragYOffset
                        alphabetDistanceFromTopOfScreen = containerDistance
                    }
                ) { firstLetterIndexes ->
                    itemsIndexed(installedApps) { index, app ->
                        AppListItem(
                            app = app,
                            isAlphabeticallyFirstInCharGroup =
                            firstLetterIndexes[app.name.uppercase().first()] == index,
                        )
                    }

                }
                val yOffset = alphabetRelativeDragYOffset
                if (yOffset != null) {
                    ScrollingBubble(
                        boxConstraintMaxWidth = this.maxWidth,
                        bubbleOffsetYFloat = yOffset + alphabetDistanceFromTopOfScreen,
                        currAlphabetScrolledOn = yOffset.getIndexOfCharBasedOnYPosition(
                            alphabetHeightInPixels = alphabetHeightInPixels,
                        ),
                    )
                }
            }
        }
    }

}

@Composable
@Preview
fun PreviewLazyAlphabetIndexColumnDemo() {
    LazyAlphabetIndexColumnDemo()
}

@Composable
@Preview
fun PreviewLazyAlphabetIndexRowDemo() {
    LazyAlphabetIndexRowDemo()
}


@Composable
@Preview
fun PreviewInstalledAppDemo() {
    LazyInstalledAppsColumnDemo()
}
