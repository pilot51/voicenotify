package com.pilot51.voicenotify.ui

import android.os.Build
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import com.pilot51.voicenotify.AppTheme
import kotlinx.coroutines.launch
import kotlin.math.min
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import com.pilot51.voicenotify.AlphabeticIndexHelper
import com.pilot51.voicenotify.ui.theme.VoiceNotifyTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import java.nio.charset.Charset


//@Composable
//fun<T> AlphabetIndex(
//    items: List<T>,
//    alphabet: List<Char> = listOf('#') + ('A'..'Z'),
//    onLetterChanged: (Char) -> Unit,
//    modifier: Modifier = Modifier,
//    vibratorEnabled: Boolean = true
//) {
//
//    var currentLetter by remember { mutableStateOf<Char?>(null) }
//    var showLetter by remember { mutableStateOf(false) }
//
//    val context = LocalContext.current
//    val vibrator = remember { context.getSystemService(VibratorManager::class.java)?.defaultVibrator }
//
//    var lastVibrationTime by remember { mutableStateOf(0L) }
//
//    Box(
//        modifier = modifier
//
//    ) {
//        Canvas(modifier = Modifier
//            .fillMaxHeight()
//            .align(Alignment.Center)
//            .pointerInput(Unit) {
//                detectVerticalDragGestures { change, _ ->
//                    val y = change.position.y
//                    val itemHeight = size.height / alphabet.size
//                    val index = ((y / itemHeight).toInt()).coerceIn(alphabet.indices)
//                    currentLetter = alphabet[index]
//                    showLetter = true
//                    onLetterChanged(alphabet[index])
//                    if (vibratorEnabled) {
//                        val currentTime = System.currentTimeMillis()
//                        if (currentTime - lastVibrationTime >= vibrationCooldown) {
//                            vibrator?.vibrate(
//                                VibrationEffect.createOneShot(
//                                    50,
//                                    VibrationEffect.DEFAULT_AMPLITUDE
//                                )
//                            )
//                            lastVibrationTime = currentTime
//                        }
//                    }
//                }
//            }
//        ) {
//            val itemHeight = size.height / alphabet.size
//            val maxItemHeight = 20.dp.toPx()
//            val actualItemHeight = min(itemHeight, maxItemHeight)
//            val verticalPadding = (size.height - actualItemHeight * alphabet.size) / 2
//
//            drawIntoCanvas { canvas ->
//                val paint = android.graphics.Paint().apply {
//                    textSize = actualItemHeight * 0.7f
//                    textAlign = android.graphics.Paint.Align.CENTER
//                }
//                alphabet.forEachIndexed { index, letter ->
//                    val x = size.width / 2
//                    val y = verticalPadding + (index * actualItemHeight) + (actualItemHeight / 2) - (paint.descent() + paint.ascent()) / 2
//
//                    if (letter == currentLetter) {
//                        paint.color = Color.Blue.copy(alpha = 0.3f).toArgb()
//                        canvas.nativeCanvas.drawRoundRect(
//                            x - actualItemHeight / 2,
//                            y - actualItemHeight / 2,
//                            x + actualItemHeight / 2,
//                            y + actualItemHeight / 2,
//                            10f,
//                            10f,
//                            paint
//                        )
//                        paint.color = Color.Blue.toArgb()
//                        paint.style = android.graphics.Paint.Style.STROKE
//                        paint.strokeWidth = 4f
//                        canvas.nativeCanvas.drawRoundRect(
//                            x - actualItemHeight / 2,
//                            y - actualItemHeight / 2,
//                            x + actualItemHeight / 2,
//                            y + actualItemHeight / 2,
//                            10f,
//                            10f,
//                            paint
//                        )
//                        paint.style = android.graphics.Paint.Style.FILL
//                    }
//
//                    paint.color = if (letter == currentLetter) android.graphics.Color.BLUE else android.graphics.Color.WHITE
//                    canvas.nativeCanvas.drawText(letter.toString(), x, y, paint)
//                }
//            }
//        }
//    }
//}

val vibrationCooldown = 50 // Cooldown time in milliseconds

val alphabetItemSize = 24.dp

val alphabetCharList = (listOf('#') + ('A'..'Z')).toPersistentList()

internal fun Float.getIndexOfCharBasedOnYPosition(
    alphabetHeightInPixels: Float,
): Char {

    var index = ((this) / alphabetHeightInPixels).toInt()
    index = when {
        index > 26 -> 26
        index < 0 -> 0
        else -> index
    }
    return alphabetCharList[index]
}




fun getFirstChar(input: String): Char {
    val firstChar = input[0]
    return when {
        firstChar.isLetter() -> firstChar.uppercaseChar()
        firstChar.isDigit() -> '#'
        isChineseCharacter(firstChar) -> getPinyinInitial(firstChar)
        else -> '#'
    }
}

fun isChineseCharacter(char: Char): Boolean {
    val unicodeBlock = Character.UnicodeBlock.of(char)
    return unicodeBlock == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            unicodeBlock == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
            unicodeBlock == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
}


fun getPinyinInitial(char: Char): Char {
    val s = char.toString()
    val s1 = String(s.toByteArray(Charset.forName("UTF-8")), Charset.forName("GB2312"))
    val s2 = String(s1.toByteArray(Charset.forName("GB2312")), Charset.forName("UTF-8"))

    val original = if (s2 == s) s1 else s

    val bytes = original.toByteArray(Charset.forName("GB2312"))
    val asc = (bytes[0].toInt() and 0xFF) * 256 + (bytes[1].toInt() and 0xFF) - 65536

    return when {
        asc >= -20319 && asc <= -20284 -> 'A'
        asc >= -20283 && asc <= -19776 -> 'B'
        asc >= -19775 && asc <= -19219 -> 'C'
        asc >= -19218 && asc <= -18711 -> 'D'
        asc >= -18710 && asc <= -18527 -> 'E'
        asc >= -18526 && asc <= -18240 -> 'F'
        asc >= -18239 && asc <= -17923 -> 'G'
        asc >= -17922 && asc <= -17418 -> 'H'
        asc >= -17417 && asc <= -16475 -> 'J'
        asc >= -16474 && asc <= -16213 -> 'K'
        asc >= -16212 && asc <= -15641 -> 'L'
        asc >= -15640 && asc <= -15166 -> 'M'
        asc >= -15165 && asc <= -14923 -> 'N'
        asc >= -14922 && asc <= -14915 -> 'O'
        asc >= -14914 && asc <= -14631 -> 'P'
        asc >= -14630 && asc <= -14150 -> 'Q'
        asc >= -14149 && asc <= -14091 -> 'R'
        asc >= -14090 && asc <= -13319 -> 'S'
        asc >= -13318 && asc <= -12839 -> 'T'
        asc >= -12838 && asc <= -12557 -> 'W'
        asc >= -12556 && asc <= -11848 -> 'X'
        asc >= -11847 && asc <= -11056 -> 'Y'
        asc >= -11055 && asc <= -10247 -> 'Z'
        else -> '#'
    }
}


fun<T> List<T>.getFirstUniqueSeenCharIndex(callback: (T) -> String): ImmutableMap<Char, Int> {
    val firstLetterIndexes = mutableMapOf<Char, Int>()
    this
        .map {
            callback(it).uppercase().first()
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
    onAlphabetListDrag: (Float?, Float) -> Unit,
    lazyListState: LazyListState = rememberLazyListState(),
    vibratorEnabled: Boolean = false,
    alphabetModifier: Modifier = Modifier,
    content: LazyListScope.(firstLetterIndexes: ImmutableMap<Char, Int>) -> Unit
) {
    val mapOfFirstLetterIndex: ImmutableMap<Char, Int> =
        remember(items) { items.getFirstUniqueSeenCharIndex(keySelector) }
    val alphabetHeightInPixels: Float =
        with(LocalDensity.current) { alphabetItemSize.toPx() }
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
                .fillMaxHeight()
                .align(Alignment.CenterVertically),
            vibratorEnabled = vibratorEnabled,
            onAlphabetListDrag = { relativeDragYOffset, containerDistanceFromTopOfScreen ->
                onAlphabetListDrag(relativeDragYOffset, containerDistanceFromTopOfScreen)
                coroutineScope.launch {
                    // null case can happen if we go through list
                    // and we don't have a name that starts with I
                    val indexOfChar = relativeDragYOffset?.getIndexOfCharBasedOnYPosition(
                        alphabetHeightInPixels = alphabetHeightInPixels,
                    )
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
    onAlphabetListDrag: (Float?, Float) -> Unit,
    lazyListState: LazyListState = rememberLazyListState(),
    vibratorEnabled: Boolean = false,
    alphabetModifier: Modifier = Modifier,
    content: @Composable (firstLetterIndexes: ImmutableMap<Char, Int>) -> Unit
) {
    val mapOfFirstLetterIndex: ImmutableMap<Char, Int> =
        remember(items) { items.getFirstUniqueSeenCharIndex(keySelector) }
    val alphabetHeightInPixels: Float =
        with(LocalDensity.current) { alphabetItemSize.toPx() }
    val coroutineScope = rememberCoroutineScope()

    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().weight(1f),
        ) {
            content(mapOfFirstLetterIndex)
        }
        AlphabetScroller(
            modifier = alphabetModifier
                .fillMaxHeight()
                .align(Alignment.CenterVertically),
            vibratorEnabled = vibratorEnabled,
            onAlphabetListDrag = { relativeDragYOffset, containerDistanceFromTopOfScreen ->
                onAlphabetListDrag(relativeDragYOffset, containerDistanceFromTopOfScreen)
                coroutineScope.launch {
                    // null case can happen if we go through list
                    // and we don't have a name that starts with I
                    val indexOfChar = relativeDragYOffset?.getIndexOfCharBasedOnYPosition(
                        alphabetHeightInPixels = alphabetHeightInPixels,
                    )
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
    onAlphabetListDrag: (relativeDragYOffset: Float?, distanceFromTopOfScreen: Float) -> Unit,
) {
    val alphabetCharList = (listOf('#') + ('A'..'Z'))
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
        modifier = modifier
            .onGloballyPositioned {
                distanceFromTopOfScreen = it.positionInRoot().y
            }
            .pointerInput(alphabetCharList) {
                detectVerticalDragGestures(
                    onDragStart = {
                        onAlphabetListDrag(it.y, distanceFromTopOfScreen)
                    },
                    onDragEnd = {
                        onAlphabetListDrag(null, distanceFromTopOfScreen)
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
                    onAlphabetListDrag(
                        change.position.y,
                        distanceFromTopOfScreen
                    )
                }
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (i in alphabetCharList) {
            Text(
                modifier = Modifier.height(alphabetItemSize),
                text = i.toString(),
            )
        }
    }
}

@Composable
fun ScrollingBubble(
    boxConstraintMaxWidth: Dp,
    bubbleOffsetYFloat: Float,
    currAlphabetScrolledOn: Char,
) {
    val bubbleSize = 96.dp
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
                style = VoiceNotifyTheme.typography.headlineLarge
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
    var widthSize by remember { mutableStateOf(20.dp) }
    AppTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = VoiceNotifyTheme.colorScheme.background
        ) {
            BoxWithConstraints {
                LazyAlphabetIndexColumn(
                    items = items,
                    alphabetModifier = Modifier,
//                        .padding(vertical = 8.dp)
//                        .fillMaxHeight()
//                        .width(widthSize)
//                        .clip(RoundedCornerShape(8.dp))
//                        .background(Color.Gray.copy(alpha = 0.5f)),
                    onAlphabetListDrag = { relativeDragYOffset, containerDistance ->
                        alphabetRelativeDragYOffset = relativeDragYOffset
                        alphabetDistanceFromTopOfScreen = containerDistance
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
    var widthSize by remember { mutableStateOf(20.dp) }
    AppTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = VoiceNotifyTheme.colorScheme.background
        ) {
            BoxWithConstraints {
                LazyAlphabetIndexRow(
                    items = items,
                    alphabetModifier = Modifier,
//                        .padding(vertical = 8.dp)
//                        .fillMaxHeight()
//                        .width(widthSize)
//                        .clip(RoundedCornerShape(8.dp))
//                        .background(Color.Gray.copy(alpha = 0.5f)),
                    onAlphabetListDrag = { relativeDragYOffset, containerDistance ->
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
@Preview
fun PreviewLazyAlphabetIndexColumnDemo() {
    LazyAlphabetIndexColumnDemo()
}

@Composable
@Preview
fun PreviewLazyAlphabetIndexRowDemo() {
    LazyAlphabetIndexRowDemo()
}
