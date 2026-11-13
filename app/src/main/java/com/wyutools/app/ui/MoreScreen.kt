package com.wyutools.app.ui

import android.graphics.Rect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.wyutools.app.SharedCardSnapshot
import com.wyutools.app.SharedViewTransition
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt
import kotlin.math.sin

// iOS home screen metrics, ported 1:1 from Apple's icon grid: a 60pt icon in a
// 4-up row, squircle corners at ~23% of the side, ~26pt between columns and a
// 96pt row pitch. Columns are equal width, so centring one icon in each one
// reproduces the gap on any screen without hard-coding a device width.
// The whole grid is then scaled up a touch: the tiles read small next to the
// real thing on a tall phone, so they take 10% more of their column.
private const val MoreIconScale = 1.1f
private val MoreIconSize = 60.dp * MoreIconScale

/** 更多页的图标比别处圆一档：共享比例是按小方块校准的，60dp 的大图标沿用
 *  同一比例会显得偏方，所以这里单独放大 25%。 */
private const val MoreIconCornerScale = 1.25f
private const val MoreIconCornerRatio = WyuIconCornerRatio * MoreIconCornerScale

/** The 更多 page's icon corner; the morph starts from this exact radius, so the
 *  window opens out of the tile with no step. */
private val MoreIconRadius = MoreIconSize * MoreIconCornerRatio
private const val MoreColumns = 4
private val MorePagePadding = 22.dp
/** 66 (icon) + 5 + 14 (label) + 17 ≈ 102dp row pitch. */
private val MoreRowSpacing = 17.dp

/**
 * px of slack around the slot the icon already owns. Without it a finger
 * resting exactly on the seam between two columns keeps tipping the target back
 * and forth, and the whole row shivers with each reflow.
 */
private val MoreStickySlop = 3.dp

/** How much of the finger's lift-off speed the icon keeps while it lands. */
private const val MoreSettleFlingGain = 0.45f
/** px/s ceiling on that speed: a hard flick must still read as a landing. */
private const val MoreSettleFlingCap = 1600f

/**
 * The 更多 page: one icon per tool, laid out like a phone home screen. Reordering
 * is only possible in edit mode, and the model reflows on every move so the grid
 * never shows a hole.
 */
@Composable
fun MoreContent(
    gridState: LazyGridState,
    topInset: Dp,
    bottomInset: Dp,
    editing: Boolean,
    tools: List<WyuTool>,
    onMove: (Int, Int) -> Unit,
    onOpen: (WyuTool, SharedCardSnapshot?) -> Unit,
    onEnterEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val currentOnMove by rememberUpdatedState(onMove)
    val currentTools by rememberUpdatedState(tools)
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragIndex by remember { mutableIntStateOf(-1) }
    var cellOrigin by remember { mutableStateOf(Offset.Zero) }
    var cellSize by remember { mutableStateOf(IntSize.Zero) }
    // True from lift-off to the moment the icon has coasted into its slot: the
    // finger is gone, the icon is still moving on its own momentum.
    var settling by remember { mutableStateOf(false) }
    val settleOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    // 1 while the icon is held, then eased down to 0 on release; it drives the
    // lift, so the tile settles back to its resting size as it lands.
    val dropProgress = remember { Animatable(1f) }
    val stickySlop = with(LocalDensity.current) { MoreStickySlop.toPx() }
    // Guards the drop animation: a grab that starts while a previous drop is
    // still easing must not be cancelled by that drop's tail.
    val dragGen = remember { intArrayOf(0) }

    fun startDrag(id: String, fallbackIndex: Int) {
        dragGen[0]++
        val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
        // Re-grabbing the icon that is still coasting picks it up from where it
        // is on screen instead of snapping it back to its slot first.
        val coasting = settling && draggingId == id
        draggingId = id
        dragIndex = info?.index ?: fallbackIndex
        cellOrigin = info?.let { Offset(it.offset.x.toFloat(), it.offset.y.toFloat()) }
            ?: Offset.Zero
        cellSize = info?.size ?: IntSize.Zero
        dragOffset = if (coasting) settleOffset.value else Offset.Zero
        settling = false
        // snapTo cancels anything still in flight, so a re-grab takes over from
        // whatever pixels are on screen.
        scope.launch {
            settleOffset.snapTo(Offset.Zero)
            dropProgress.snapTo(1f)
        }
    }

    /**
     * The finger has left: the icon keeps whatever speed it was thrown with,
     * coasts a little further along that line and decelerates back into its
     * slot. Because the spring starts from an offset *and* a velocity, the two
     * contributions only line up when the flick was along the slot's direction;
     * any other throw makes the icon trace a curve on its way home.
     */
    fun endDrag(velocity: Offset) {
        val gen = dragGen[0]
        val from = dragOffset
        if (from.getDistanceSquared() < 0.25f) {
            draggingId = null
            dragOffset = Offset.Zero
            settling = false
            return
        }
        val kick = velocity.limit(MoreSettleFlingCap) * MoreSettleFlingGain
        scope.launch {
            // Hand the pixels over before flipping the flag: the frame that
            // switches the cell from the finger's offset to the animated one
            // must not see the two disagree, or the icon blinks into its slot
            // for a frame and then jumps back out.
            settleOffset.snapTo(from)
            if (dragGen[0] != gen) return@launch
            settling = true
            coroutineScope {
                launch {
                    settleOffset.animateTo(
                        targetValue = Offset.Zero,
                        animationSpec = spring(
                            dampingRatio = 0.82f,
                            stiffness = Spring.StiffnessLow,
                            visibilityThreshold = Offset(0.5f, 0.5f),
                        ),
                        initialVelocity = kick,
                    )
                }
                launch {
                    dropProgress.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = 0.9f,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
                }
            }
            if (dragGen[0] == gen) {
                draggingId = null
                settling = false
                dragOffset = Offset.Zero
                settleOffset.snapTo(Offset.Zero)
            }
        }
    }

    fun moveDrag(delta: Offset) {
        dragOffset += delta
        if (cellSize == IntSize.Zero) return
        val cells = gridState.layoutInfo.visibleItemsInfo
        if (cells.isEmpty()) return
        val center = cellOrigin + dragOffset +
            Offset(cellSize.width / 2f, cellSize.height / 2f)
        // 1. The slot the icon already owns keeps it until the finger is clearly
        //    past its edge — hysteresis, so seams cannot make the grid flap.
        // 2. Any slot directly under the finger takes it.
        // 3. Nothing under the finger — the icon was dragged past the last one,
        //    into a column the short last row leaves empty, or out over the
        //    padding. The nearest slot still claims it, so the icons behind step
        //    back and close the gap instead of the drag silently doing nothing.
        // 4. Further than a cell and a half: an icon drifting over the header or
        //    the tab bar must not reshuffle the grid from a distance.
        val reach = maxOf(cellSize.width, cellSize.height) * 1.5f
        val target = cells.firstOrNull { it.index == dragIndex && it.holds(center, stickySlop) }
            ?: cells.firstOrNull { it.holds(center, 0f) }
            ?: cells.minByOrNull { it.centerDistance(center) }
                ?.takeIf { it.centerDistance(center) <= reach }
            ?: return
        if (target.index == dragIndex) return
        val shift = Offset(
            (target.offset.x - cellOrigin.x),
            (target.offset.y - cellOrigin.y),
        )
        currentOnMove(dragIndex, target.index)
        dragIndex = target.index
        cellOrigin = Offset(
            target.offset.x.toFloat(),
            target.offset.y.toFloat(),
        )
        // Keep the lifted icon under the finger even though the grid just
        // slid underneath it.
        dragOffset -= shift
        if (dragIndex !in currentTools.indices) {
            draggingId = null
            dragOffset = Offset.Zero
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(MoreColumns),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = MorePagePadding,
            end = MorePagePadding,
            // Lines the icons up with the first card of the pages on either
            // side: 常用 and 我 both start their content at topInset + 22dp
            // (8dp column padding + 14dp of item spacing).
            top = topInset + 22.dp,
            bottom = bottomInset + 24.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalArrangement = Arrangement.spacedBy(MoreRowSpacing),
    ) {
        if (tools.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Swipe,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "所有工具都在常用页了",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
        itemsIndexed(
            items = tools,
            key = { _, tool -> tool.id },
        ) { index, tool ->
            val dragging = draggingId == tool.id
            MoreToolCell(
                tool = tool,
                editing = editing,
                dragging = dragging,
                // Read through lambdas: the drop animation then only invalidates
                // the icon's own layer instead of recomposing the grid.
                dragOffset = {
                    when {
                        !dragging -> Offset.Zero
                        // The coast home is an Offset animation of its own, so
                        // the path can bend; while the finger is down the offset
                        // is simply where the finger is.
                        settling -> settleOffset.value
                        else -> dragOffset
                    }
                },
                dragScale = { 1f + 0.08f * dropProgress.value },
                onOpen = { snapshot -> onOpen(tool, snapshot) },
                onLongPress = onEnterEdit,
                onDragStart = { startDrag(tool.id, index) },
                onDrag = { delta -> moveDrag(delta) },
                onDragEnd = { velocity -> endDrag(velocity) },
                modifier = Modifier
                    // The lifted icon must not animate its own placement. The
                    // drag offset already compensates, frame for frame, for the
                    // slot it just left; an item animation would add its own
                    // travel on top of that, so the icon would fly off the
                    // finger and only then slide back to it. Its neighbours do
                    // animate — that is what shows the grid reflowing around it.
                    .then(if (dragging) Modifier else Modifier.animateItem())
                    .zIndex(if (dragging) 1f else 0f),
            )
        }
    }
}

@Composable
private fun MoreToolCell(
    tool: WyuTool,
    editing: Boolean,
    dragging: Boolean,
    dragOffset: () -> Offset,
    dragScale: () -> Float,
    onOpen: (SharedCardSnapshot?) -> Unit,
    onLongPress: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    /** The finger's velocity at lift-off, in px/s. */
    onDragEnd: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val graphicsLayer = rememberGraphicsLayer()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val radiusPx = with(density) { MoreIconRadius.toPx() }
    val tileShape = rememberWyuSquircle(MoreIconRadius)
    val bounds = remember { Rect() }
    var launching by remember { mutableStateOf(false) }
    // Read inside the gesture, not captured: entering edit mode must not restart
    // the pointer input (that would drop the finger that is already down).
    val editingNow by rememberUpdatedState(editing)
    val liftPx = with(density) { 3.dp.toPx() }
    val open: () -> Unit = {
        // Let the tap interrupt an exit that is still playing instead of
        // waiting for it; the short settle below keeps the capture clean.
        WyuMorphPageState.interruptMorph()
        if (launching) {
            Unit
        } else if (bounds.isEmpty) {
            onOpen(null)
        } else {
            launching = true
            scope.launch {
                // The press already handed the screen to this tap; no need to
                // wait for the parallel close to finish.
                val bitmap = runCatching {
                    graphicsLayer.toImageBitmap().asAndroidBitmap()
                }.getOrNull()
                launching = false
                onOpen(
                    bitmap?.let {
                        SharedCardSnapshot(
                            bitmap = it,
                            bounds = Rect(bounds),
                            radiusPx = radiusPx,
                        )
                    },
                )
            }
        }
    }
    val jiggle = rememberJiggle(tool.id, editing && !dragging)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                if (!editing) {
                    role = Role.Button
                    onClick(label = tool.title) {
                        open()
                        true
                    }
                }
            }
            .graphicsLayer {
                val drag = dragOffset()
                translationX = drag.x + jiggle.offsetX()
                translationY = drag.y + jiggle.offsetY()
                // Full assignment every time: a layer block mutates a persistent
                // layer, so omitting a value would freeze it at its last one.
                rotationZ = jiggle.rotation()
                scaleX = if (dragging) dragScale() else 1f
                scaleY = if (dragging) dragScale() else 1f
            }
            .pointerInput(tool.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!editingNow) {
                        // Hold still and the icon is picked up: edit mode is
                        // entered *inside this same gesture*, so the finger that
                        // pressed can keep going and drag the icon without ever
                        // lifting. Moving before the timeout instead hands the
                        // gesture to the pager, so ordinary swipes still page.
                        val longPress = awaitLongPressOrCancellation(down.id)
                        if (longPress == null) {
                            val up = currentEvent.changes.firstOrNull { it.id == down.id }
                            if (up != null &&
                                up.changedToUpIgnoreConsumed() &&
                                !up.isConsumed
                            ) {
                                open()
                            }
                            return@awaitEachGesture
                        }
                        if ((longPress.position - down.position).getDistance() >
                            viewConfiguration.touchSlop
                        ) {
                            return@awaitEachGesture
                        }
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                    }
                    // The gesture belongs to the icon from here on: every
                    // change is consumed, so the pager can never claim the drag
                    // half-way through and leave the icon stuck under the finger.
                    var started = false
                    var travel = Offset.Zero
                    // Tracked by hand rather than with detectDragGestures: the
                    // pointer session is shared with the long press above, and
                    // the lift-off speed is what decides the settle's direction.
                    val tracker = VelocityTracker()
                    var lastSample = 0L
                    var flingVelocity = Offset.Zero
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.uptimeMillis > lastSample) {
                            lastSample = change.uptimeMillis
                            tracker.addPosition(lastSample, change.position)
                        }
                        if (change.changedToUpIgnoreConsumed()) {
                            if (started) {
                                val release = tracker.calculateVelocity()
                                flingVelocity = Offset(release.x, release.y)
                            }
                            change.consume()
                            break
                        }
                        val delta = change.positionChange()
                        travel += delta
                        if (!started && travel.getDistance() > liftPx) {
                            started = true
                            onDragStart()
                            // Hand over the whole travel, so the icon lands
                            // under the finger instead of behind it.
                            onDrag(travel)
                        } else if (started) {
                            onDrag(delta)
                        }
                        change.consume()
                    }
                    if (started) onDragEnd(flingVelocity)
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                // The tile in flight is not drawn in its slot: it keeps its size
                // and place, so the grid never reflows and the morph's target
                // stays put, but the pixels go away. Read here, in the layer, so
                // a capture can hide it and record in the same frame.
                .graphicsLayer {
                    // Full assignment: a layer block mutates a persistent layer,
                    // so leaving the value out would freeze it at zero.
                    alpha = if (WyuMorphPageState.flyingRoute == tool.route) 0f else 1f
                }
                .size(MoreIconSize)
                // The tile alone is the morph source: the label under it stays
                // behind, the way iOS zooms the icon and not the caption.
                .onGloballyPositioned { coordinates ->
                    val local = coordinates.boundsInWindow()
                    val origin = if (SharedViewTransition.boundsUseWindowCoordinate) {
                        null
                    } else {
                        IntArray(2).also(view::getLocationOnScreen)
                    }
                    bounds.set(
                        local.left.roundToInt() + (origin?.get(0) ?: 0),
                        local.top.roundToInt() + (origin?.get(1) ?: 0),
                        local.right.roundToInt() + (origin?.get(0) ?: 0),
                        local.bottom.roundToInt() + (origin?.get(1) ?: 0),
                    )
                }
                .drawWithContent {
                    graphicsLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(graphicsLayer)
                }
                .clip(tileShape)
                // Opaque like a real app icon: the wallpaper never bleeds
                // through the tile, only the glyph sits on top of it.
                .background(
                    brush = Brush.linearGradient(
                        listOf(
                            lerp(tool.iconColor, Color.White, 0.22f),
                            tool.iconColor,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = tool.title,
                // Half the tile, so the glyph keeps its weight as the grid grows.
                modifier = Modifier.size(MoreIconSize * 0.5f),
                tint = Color.White,
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = tool.title,
            style = MiuixTheme.textStyles.footnote2.copy(
                fontSize = 11.sp,
                lineHeight = 13.sp,
            ),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * One flat, saturated fill per tool, standing in for iOS's colour-coded app
 * icons: the tile is what tells them apart at a glance, the glyph stays white.
 */
private val WyuTool.iconColor: Color
    get() = when (this) {
        WyuTool.Schedule -> Color(0xFF0A84FF)
        WyuTool.Lxt -> Color(0xFF30B0C7)
        WyuTool.Elec -> Color(0xFFFF9F0A)
        WyuTool.Gym -> Color(0xFF30D158)
        WyuTool.Exam -> Color(0xFFBF5AF2)
        WyuTool.Grades -> Color(0xFF5E5CE6)
        WyuTool.Eval -> Color(0xFFFF375F)
        WyuTool.Physical -> Color(0xFF00C7BE)
    }

/** Whether [point] is inside this cell, with [slop] px of forgiveness. */
private fun LazyGridItemInfo.holds(point: Offset, slop: Float): Boolean =
    point.x >= offset.x - slop && point.x < offset.x + size.width + slop &&
        point.y >= offset.y - slop && point.y < offset.y + size.height + slop

/** How far [point] sits from the middle of this cell. */
private fun LazyGridItemInfo.centerDistance(point: Offset): Float =
    (Offset(offset.x + size.width / 2f, offset.y + size.height / 2f) - point).getDistance()

/** [this] shortened to at most [max] long, keeping its direction. */
private fun Offset.limit(max: Float): Offset {
    val distance = getDistance()
    return if (distance <= max || distance == 0f) this else this * (max / distance)
}

/**
 * iOS's edit-mode wiggle is not one tilt but three asynchronous micro motions
 * layered on top of each other: a ~1.2° rock, a ±3–4px drift and a smaller
 * counter-drift on half the period. Each icon gets its own phase and its own
 * period from its id, so no two of them ever line up and the grid reads as
 * alive instead of as a metronome.
 *
 * All three are read inside a `graphicsLayer` block, so a frame only updates
 * the icon's layer — the grid itself never recomposes for the animation.
 */
private class IconJiggle(
    private val clock: State<Float>,
    private val phase: Float,
    private val amplitudePx: Float,
) {
    private fun wave(multiplier: Float, shift: Float): Float =
        sin((((clock.value + phase) * multiplier + shift) % 1f) * TwoPi)

    fun rotation(): Float = wave(1f, 0f) * 1.2f

    fun offsetX(): Float = wave(2f, 0.25f) * amplitudePx

    fun offsetY(): Float = wave(2f, 0.62f) * amplitudePx * 0.7f
}

private const val TwoPi = 6.2831855f

/** Everything at rest: used outside edit mode, so no transition runs at all. */
private val StillJiggle = IconJiggle(mutableStateOf(0f), 0f, 0f)

@Composable
private fun rememberJiggle(key: String, enabled: Boolean): IconJiggle {
    val amplitudePx = with(LocalDensity.current) { 1.3.dp.toPx() }
    if (!enabled) return StillJiggle
    val seed = remember(key) { key.hashCode() }
    // 0f..1f: where in its cycle this icon starts, which is what breaks the
    // synchronised look of a grid-wide animation.
    val phase = remember(seed) { (seed and 0xFFFF).toFloat() / 65536f }
    // 400..470ms per rock — slower than the reference 8–12Hz, which read as
    // nervous at this icon size; still jittered per icon so the grid breathes
    // instead of ticking together.
    val period = remember(seed) { 400 + ((seed ushr 4) and 63) }
    val transition = rememberInfiniteTransition(label = "jiggle")
    val clock = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(period, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "jiggleClock",
    )
    return remember(clock, phase, amplitudePx) { IconJiggle(clock, phase, amplitudePx) }
}
