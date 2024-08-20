package com.pilot51.voicenotify.ui

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.dp


fun Modifier.bottomBorder(strokeWidth: Float, color: Color) = composed(
    factory = {
        val strokeWidthPx = strokeWidth
        Modifier.drawBehind {
            val width = size.width
            val height = size.height + Math.round(-strokeWidthPx/2)
            drawLine(
                color = color,
                start = Offset(x = 0f, y = height),
                end = Offset(x = width , y = height),
                strokeWidth = strokeWidthPx
            )
        }
    }
)


fun Modifier.topBorder(strokeWidth: Float, color: Color) = composed(
    factory = {
        val strokeWidthPx = strokeWidth
        Modifier.drawBehind {
            val width = size.width
            val height = 0f +  Math.round(-strokeWidthPx/2)
            drawLine(
                color = color,
                start = Offset(x = 0f, y = height),
                end = Offset(x = width , y = height),
                strokeWidth = strokeWidthPx
            )
        }
    }
)

/**
 * @example
 *  Modifier.addIf(condition) { Modifier.padding(16.dp) }
 */
inline fun Modifier.addIf(
    predicate: Boolean,
    crossinline whenTrue: @Composable () -> Modifier,
): Modifier = composed {
    if (predicate) {
        this.then(whenTrue())
    } else {
        this
    }
}

/**
 *  @example
 * 
 *  Modifier.measured { topBarHeight = it.height }
 * 
 *  Modifier.measured { size ->
 *  topBarHeight = size.height
 * }
 * 
 */
fun Modifier.measured(block: (DpSize) -> Unit): Modifier = composed {
    val density = LocalDensity.current
    onPlaced { block(it.size.toDp(density)) }
}


/**
 * @example
 *  Modifier.debugBounds()
 */
fun Modifier.debugBounds(color: Color = Color.Magenta, shape: Shape = RectangleShape) =
    this.border(1.dp, color, shape)

