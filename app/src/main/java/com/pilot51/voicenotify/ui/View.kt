package com.pilot51.voicenotify.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Resources
import android.os.Process.killProcess
import android.os.Process.myPid
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.fragment.app.FragmentActivity
import com.pilot51.voicenotify.BuildConfig
import com.pilot51.voicenotify.MainActivity
//import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.util.*
import kotlin.concurrent.timerTask
import kotlin.system.exitProcess


fun Int.dipToPixels() = (Resources.getSystem().displayMetrics.density * this).toInt()

val Int.nonScaledSp
    @Composable
    get() = (this / LocalDensity.current.fontScale).sp

fun Context.getActivity(): FragmentActivity? = when (this) {
    is AppCompatActivity -> this
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}

fun androidx.compose.ui.graphics.Color.toAGColor() = toArgb().run {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O){
        android.graphics.Color.argb(alpha, red, green, blue)
    } else {
        android.graphics.Color.argb(
            (alpha * 255).toInt(),
            (red * 255).toInt(),
            (green * 255).toInt(),
            (blue * 255).toInt()
        )
    }
}

val Float.toPx get() = this * Resources.getSystem().displayMetrics.density
val Float.toDp get() = this / Resources.getSystem().displayMetrics.density

val Int.toPx get() = (this * Resources.getSystem().displayMetrics.density).toInt()
val Int.toDp get() = (this / Resources.getSystem().displayMetrics.density).toInt()




fun IntSize.toDp(density: Density): DpSize = with(density) { DpSize(width = width.toDp(), height = height.toDp()) }

//fun Activity.handleUncaughtException() {
//    val crashedKey = "isCrashed"
//    if (intent.getBooleanExtra(crashedKey, false)) return
//    Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
//        if (BuildConfig.DEBUG) throw throwable
//
//        FirebaseCrashlytics.getInstance().recordException(throwable)
//
//        val intent = Intent(this, MainActivity::class.java).apply {
//            putExtra(crashedKey, true)
//            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
//        }
//        startActivity(intent)
//        finish()
//        killProcess(myPid())
//        exitProcess(2)
//    }
//}

fun withDelay(delay: Long, block: () -> Unit) {
    Timer().schedule(timerTask { block() } , delay)
}