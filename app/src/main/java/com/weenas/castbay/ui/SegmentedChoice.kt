package com.weenas.castbay.ui

import android.app.ActivityManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val HIGHLIGHT_DURATION_MS = 120
private val SEGMENT_HEIGHT = 44.dp
private val PILL = RoundedCornerShape(50)

/**
 * A setting's few choices side by side, as one focus stop: Left and Right pick the neighbour
 * (wrapping from one end to the other) and OK the next one, with the highlight sliding over.
 * Up and Down still move between settings, so the remote never gets stuck inside it.
 */
@Composable
fun <T> SegmentedChoice(
    value: T,
    choices: List<T>,
    display: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    onSelected: (T) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val density = LocalDensity.current
    val context = LocalContext.current
    val animateHighlight = remember(context) {
        context.getSystemService(ActivityManager::class.java)?.isLowRamDevice != true
    }
    // Each segment's x and width, measured, for the sliding highlight.
    val bounds = remember(choices) { mutableStateMapOf<Int, Pair<Dp, Dp>>() }
    val selected = choices.indexOf(value).coerceAtLeast(0)
    fun pick(index: Int) = onSelected(choices[Math.floorMod(index, choices.size)])

    // The highlight starts where it belongs, sliding only when the choice changes: animating
    // from where it was before the segments were measured made it sweep in from the left
    // whenever the control appeared (e.g. Settings recreated for a new language).
    val placed = bounds[selected] != null
    var slide by remember { mutableStateOf(false) }
    LaunchedEffect(placed) {
        if (placed && animateHighlight) {
            withFrameNanos { }
            slide = true
        }
    }
    // In a row that scrolls sideways (a narrow screen), a newly picked choice scrolls into
    // view; not on first showing, which would scroll the page to every control.
    val requesters = remember(choices) { choices.map { BringIntoViewRequester() } }
    var shown by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        if (selected != shown) {
            shown = selected
            requesters.getOrNull(selected)?.bringIntoView()
        }
    }
    val motion = if (slide) tween<Dp>(HIGHLIGHT_DURATION_MS) else snap()
    val thumbX by animateDpAsState(bounds[selected]?.first ?: 0.dp, motion, label = "thumbX")
    val thumbWidth by animateDpAsState(bounds[selected]?.second ?: 0.dp, motion, label = "thumbWidth")
    // Unfocused, the choice stays visible but quieter, so the focused row stands out.
    val thumbColor by animateColorAsState(
        if (focused) MaterialTheme.colorScheme.primary else Color(0xFF5A5470),
        animationSpec = if (animateHighlight) tween(HIGHLIGHT_DURATION_MS) else snap(),
        label = "thumbColor"
    )

    Box(
        modifier = modifier
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { pick(selected - 1); true }
                    Key.DirectionRight, Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { pick(selected + 1); true }
                    else -> false
                }
            }
            .focusable(interactionSource = interaction)
            .clip(PILL)
            .background(Color(0x26FFFFFF))
            .border(3.dp, if (focused) Color.White else Color.Transparent, PILL)
            .padding(4.dp)
            .drawWithContent {
                // Read animated values during drawing so each frame only redraws the highlight.
                val width = thumbWidth.toPx()
                val height = SEGMENT_HEIGHT.toPx()
                if (width > 0) {
                    drawRoundRect(
                        color = thumbColor,
                        topLeft = Offset(thumbX.toPx(), 0f),
                        size = Size(width, height),
                        cornerRadius = CornerRadius(minOf(width, height) / 2)
                    )
                }
                drawContent()
            }
    ) {
        Row {
            choices.forEachIndexed { index, choice ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(SEGMENT_HEIGHT)
                        .widthIn(min = 88.dp)
                        .bringIntoViewRequester(requesters[index])
                        .onPlaced { placed ->
                            bounds[index] = with(density) {
                                placed.positionInParent().x.toDp() to placed.size.width.toDp()
                            }
                        }
                        // Taps for touch screens; not focusable, so the remote treats the row as one.
                        .pointerInput(choice) { detectTapGestures { onSelected(choice) } }
                        .semantics {
                            role = Role.RadioButton
                            this.selected = index == selected
                        }
                        .padding(horizontal = 22.dp)
                ) {
                    Text(
                        display(choice),
                        fontSize = 18.sp,
                        color = if (index == selected) Color.White else Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
