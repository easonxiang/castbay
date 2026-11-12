package com.weenas.castbay.ui.screen

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.provider.Settings
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * How fast the record turns: [FULL_SPEED] degrees a second (one turn in 15 s, calm beside lyrics
 * and cheap to draw; a real 33 rpm record would be 1.8 s), eased in when music starts and coasting
 * to a stop when it pauses, so the angle is kept and playing resumes from it.
 */
class VinylSpin {
    var angle = 0f
        private set
    var speed = 0f
        private set

    /** Moves on by [dtSeconds]; true while the record is still turning (or meant to). */
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

/** The angle is updated about 30 times a second: enough for this speed, half the work of every frame. */
private const val SPIN_FRAME_MS = 30L

/**
 * The album cover as the label of a vinyl record that turns while [spinning]. The record is drawn
 * once (a bitmap with grooves and the cover as its label), so each frame only turns that bitmap;
 * the glint on it stays where it is, as light on a real record does.
 */
@Composable
fun VinylRecord(cover: ImageBitmap, spinning: Boolean, size: Dp, contentDescription: String?) {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val record by produceState<ImageBitmap?>(null, cover, sizePx) {
        value = withContext(Dispatchers.Default) { renderRecord(cover.asAndroidBitmap(), sizePx).asImageBitmap() }
    }
    // A new song starts from the top; the angle is read only while drawing, so turning never recomposes.
    val spin = remember(cover) { VinylSpin() }
    val angle = remember(cover) { mutableFloatStateOf(0f) }
    val resolver = LocalContext.current.contentResolver
    // The system's "remove animations" setting (also what a tester's scale 0 means).
    val animationsOn = remember { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f }
    val turning = spinning && animationsOn
    LaunchedEffect(spin, turning) {
        if (!turning && spin.speed == 0f) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            delay(SPIN_FRAME_MS)
            val now = withFrameNanos { it }
            val moving = spin.step((now - last) / 1_000_000_000f, turning)
            last = now
            angle.floatValue = spin.angle
            if (!moving) break
        }
    }
    Box(modifier = Modifier.size(size).shadow(24.dp, CircleShape)) {
        record?.let {
            Image(
                bitmap = it,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    rotationZ = angle.floatValue
                    // Turned as one picture: the record is not drawn again every frame.
                    compositingStrategy = CompositingStrategy.Offscreen
                },
            )
        }
        // The glint: two soft bands across the record that do not turn with it.
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.sweepGradient(
                    0f to Color.Transparent, 0.07f to Color.White.copy(alpha = 0.10f), 0.14f to Color.Transparent,
                    0.5f to Color.Transparent, 0.57f to Color.White.copy(alpha = 0.10f), 0.64f to Color.Transparent,
                    1f to Color.Transparent,
                ),
            )
        }
    }
}

/** The record as a bitmap of [size] pixels: black vinyl with fine grooves, [cover] as its label, and a spindle hole. */
internal fun renderRecord(cover: Bitmap, size: Int): Bitmap {
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(out)
    val radius = size / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = 0xFF0E0E0E.toInt()
    canvas.drawCircle(radius, radius, radius, paint)
    // Grooves: thin rings, every fifth a little brighter (the gaps between tracks).
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = maxOf(1f, size / 520f)
    var ring = 0
    var r = radius * 0.46f
    while (r < radius * 0.97f) {
        paint.color = if (ring % 5 == 0) 0x2AFFFFFF else 0x12FFFFFF
        canvas.drawCircle(radius, radius, r, paint)
        r += radius * 0.017f
        ring++
    }
    // The label: the cover, centred and cropped to a circle.
    val labelRadius = radius * 0.38f
    val scale = (2 * labelRadius) / minOf(cover.width, cover.height)
    val shader = BitmapShader(cover, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    shader.setLocalMatrix(Matrix().apply {
        setScale(scale, scale)
        postTranslate(radius - cover.width * scale / 2f, radius - cover.height * scale / 2f)
    })
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader }
    canvas.drawCircle(radius, radius, labelRadius, labelPaint)
    paint.shader = null
    paint.color = 0xFF050505.toInt()
    paint.strokeWidth = maxOf(2f, size / 200f)
    canvas.drawCircle(radius, radius, labelRadius, paint)
    // The spindle hole.
    paint.style = Paint.Style.FILL
    paint.color = 0xFF0B0B0B.toInt()
    canvas.drawCircle(radius, radius, radius * 0.045f, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = maxOf(1f, size / 400f)
    paint.color = 0x55FFFFFF
    canvas.drawCircle(radius, radius, radius * 0.045f, paint)
    return out
}
