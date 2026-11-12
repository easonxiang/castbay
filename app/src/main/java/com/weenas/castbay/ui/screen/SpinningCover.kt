package com.weenas.castbay.ui.screen

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.exp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * How fast the cover turns: [FULL_SPEED] degrees a second (one turn in 15 s, calm beside lyrics
 * and cheap to draw), eased in when music starts and coasting
 * to a stop when it pauses, so the angle is kept and playing resumes from it.
 */
class CoverSpin {
    var angle = 0f
        private set
    var speed = 0f
        private set

    /** Moves on by [dtSeconds]; true while the cover is still turning (or meant to). */
    fun step(dtSeconds: Float, spinning: Boolean): Boolean {
        val target = if (spinning) FULL_SPEED else 0f
        val timeConstant = if (spinning) SPEED_UP_SECONDS else SLOW_DOWN_SECONDS
        speed += (target - speed) * (1f - exp(-dtSeconds / timeConstant))
        if (!spinning && speed < STOPPED_BELOW) speed = 0f
        angle = (angle + speed * dtSeconds) % 360f
        return spinning || speed > 0f
    }

    companion object {
        const val FULL_SPEED = 24f
        private const val SPEED_UP_SECONDS = 0.4f
        private const val SLOW_DOWN_SECONDS = 0.7f
        private const val STOPPED_BELOW = 0.5f
    }
}

/** The thin rim round the cover (white, a little see-through), as a share of its diameter: it parts the picture from its own blurred backdrop. */
private const val RIM_RATIO = 0.008f
private const val RIM_COLOR = 0xD9FFFFFF.toInt()

/**
 * The angle moves on every screen frame (60 a second; a 120 Hz screen skips every other): at 30
 * updates a second, spaced unevenly by the delay and the next frame, the turning looked jerky.
 */
private const val MIN_FRAME_SECONDS = 0.014f

/**
 * The album cover as a round picture with a thin white rim, turning while [spinning]; [songKey] tells songs apart. The picture
 * is drawn once (a bitmap, off the main thread) and each frame only turns that layer.
 */
@Composable
fun SpinningCover(cover: ImageBitmap, songKey: Any?, spinning: Boolean, size: Dp, contentDescription: String?) {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val picture by produceState<ImageBitmap?>(null, cover, sizePx) {
        value = withContext(Dispatchers.Default) { renderRoundCover(cover.asAndroidBitmap(), sizePx).asImageBitmap() }
    }
    // A new song starts from the top; the cover arriving (again) for the same song does not move
    // the angle, nor does pausing. The angle is read only while drawing, so turning never recomposes.
    val spin = remember(songKey) { CoverSpin() }
    val angle = remember(songKey) { mutableFloatStateOf(0f) }
    val resolver = LocalContext.current.contentResolver
    // The system's "remove animations" setting (also what a tester's scale 0 means).
    val animationsOn = remember { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f }
    val turning = spinning && animationsOn
    LaunchedEffect(spin, turning) {
        if (!turning && spin.speed == 0f) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val seconds = (now - last) / 1_000_000_000f
            if (seconds < MIN_FRAME_SECONDS) continue
            // A long gap (the screen was busy or off) is not a jump of the angle.
            val moving = spin.step(minOf(seconds, 0.1f), turning)
            last = now
            angle.floatValue = spin.angle
            if (!moving) break
        }
    }
    Box(modifier = Modifier.size(size).shadow(24.dp, CircleShape)) {
        picture?.let {
            Image(
                bitmap = it,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    rotationZ = angle.floatValue
                    // Turned as one picture: the picture is not drawn again every frame.
                    compositingStrategy = CompositingStrategy.Offscreen
                },
            )
        }
    }
}

/** [cover] centred and cropped to a circle of [size] pixels, with a thin rim. */
internal fun renderRoundCover(cover: Bitmap, size: Int): Bitmap {
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(out)
    val radius = size / 2f
    val rim = maxOf(2f, size * RIM_RATIO)
    val pictureRadius = radius - rim / 2f
    val scale = (2 * pictureRadius) / minOf(cover.width, cover.height)
    val shader = BitmapShader(cover, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    shader.setLocalMatrix(Matrix().apply {
        setScale(scale, scale)
        postTranslate(radius - cover.width * scale / 2f, radius - cover.height * scale / 2f)
    })
    canvas.drawCircle(radius, radius, pictureRadius, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader })
    val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = rim
        color = RIM_COLOR
    }
    canvas.drawCircle(radius, radius, pictureRadius, rimPaint)
    return out
}
