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


fun Modifier.bottomBorder(strokeWidth: Dp, color: Color) = composed(
    factory = {
        val density = LocalDensity.current
        val strokeWidthPx = density.run { strokeWidth.toPx() }
        Modifier.drawBehind {
        //  val strokeWidth = Dp.Hairline.toPx()
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


fun Modifier.topBorder(strokeWidth: Dp, color: Color) = composed(
    factory = {
        val density = LocalDensity.current
        val strokeWidthPx = density.run { strokeWidth.toPx() }
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

fun Modifier.measured(block: (DpSize) -> Unit): Modifier = composed {
    val density = LocalDensity.current
    onPlaced { block(it.size.toDp(density)) }
}

fun Modifier.debugBounds(color: Color = Color.Magenta, shape: Shape = RectangleShape) =
    this.border(1.dp, color, shape)

