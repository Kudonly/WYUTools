package com.wyutools.app.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/** One tool card; fixed so drag-to-reorder can map pixels to slots. */
private val CommonRowHeight = 68.dp
/** The grab handle at the right of every card. */
private val CommonHandleWidth = 30.dp
/** Vertical gap between two cards; the drop slot pitch includes it. */
private val CommonCardSpacing = 10.dp
private val CommonCardSideInset = 16.dp
/**
 * Dead band on either side of the 更多工具 title. Without it a finger resting on
 * the line flips the card's section on every pixel of tremor: each flip reflows
 * the two lists, which moves the line, which flips it back. The card has to be
 * dragged this far past the title before it changes sides.
 */
private val CommonSectionHysteresis = 16.dp
/**
 * How much of a neighbouring card the carried card has to cover before that card
 * gives way. The carried card hangs half its height below the finger, so 0.5
 * means the finger reaching the neighbour's near edge — the moment the two are
 * half overlapped. Comparing against the slot midpoint instead only fired once
 * the neighbour was almost completely buried.
 */
private const val CommonGiveWayCoverage = 0.5f
/** How lifted the card looks while it is carried. */
private const val CommonLiftScale = 0.02f
/** Landing time for the release animation. */
private const val CommonSettleMillis = 320
/**
 * How long the handle has to be held before the card is picked up. Just long
 * enough that a hand sliding down the page past the handles does not grab a
 * card on the way, short enough that it does not feel like a wait.
 */
private const val CommonHoldMillis = 150L

/**
 * One entry of the single column below 显示的卡片: a tool card, or the block that
 * introduces 更多工具. Keeping both sections in **one** column is what makes a
 * card survive the crossing: its keyed group is moved, never disposed, so the
 * pointer node that owns the drag keeps running while the card changes sides.
 */
private sealed interface CommonItem {
    val key: String

    class Tool(val tool: WyuTool, val inCommon: Boolean) : CommonItem {
        override val key: String = tool.id
    }

    object MoreHeader : CommonItem {
        override val key: String = "more-header"
    }
}

/**
 * 设置 → 常用卡片: the 常用 cards and the 更多 icons, with one drag between them.
 * There are no switches — a tool is on 常用 exactly while it sits in the upper
 * list, so the drag *is* the toggle.
 *
 * The card that is picked up disappears from the list (the slot stays, so the
 * layout is still honest) and the same glass card follows the finger; the other
 * cards spring from where they were to where they now belong. Slots are
 * re-evaluated on every frame of the drag, so a card changes section the moment
 * it is carried across the 更多工具 title instead of waiting for the finger to
 * lift; on release it flies home along a decelerating curve.
 */
@Composable
fun CommonCardsScreen(onBack: () -> Unit) {
    val scroll = rememberScrollState()
    val fraction = rememberWyuCollapseFraction(scroll)
    val layout by WyuToolLayoutStore.state.collectAsState()
    val commonTools = layout.commonTools
    // Ordered by the stored layout so a card dropped into 更多 keeps the slot it
    // was dropped in; anything the file lost is appended rather than hidden.
    val moreTools = remember(layout) {
        val known = layout.moreTools.toMutableList()
        WyuTool.entries.forEach { tool ->
            if (!layout.isCommon(tool) && tool !in known) known += tool
        }
        known
    }
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val rowHeightPx = with(density) { CommonRowHeight.toPx() }
    val pitchPx = with(density) { (CommonRowHeight + CommonCardSpacing).toPx() }
    val hysteresisPx = with(density) { CommonSectionHysteresis.toPx() }
    // Line inside a row at which the carried card has covered it by
    // [CommonGiveWayCoverage]: its top edge when approached from above, its
    // bottom edge when approached from below, so a half-covered neighbour always
    // gives way regardless of the direction of the drag.
    val giveWayFromAbovePx = rowHeightPx * (CommonGiveWayCoverage - 0.5f)
    val giveWayFromBelowPx = rowHeightPx * (1.5f - CommonGiveWayCoverage)

    // Root origin of the page, so the floating card can be placed from the
    // finger's root position.
    var boxTop by remember { mutableFloatStateOf(0f) }
    // Root top of every slot, refreshed on each layout pass *before* the reorder
    // spring is applied. The drop slot is counted against these, and the release
    // animation lands on one of them, so neither inherits a half-finished bounce.
    val rowTops = remember { mutableStateMapOf<String, Float>() }
    // Settled top of the 更多工具 block, plus the title's offset *inside* that
    // block. The two are added at read time to get the title's settled line: the
    // block itself is translated by the reorder spring, so its own root position
    // cannot be measured from inside the moving layer.
    var moreHeaderTop by remember { mutableFloatStateOf(0f) }
    var moreTitleOffset by remember { mutableFloatStateOf(0f) }

    // The card being carried: non-null from the moment the handle is pressed
    // until it has landed after release.
    var dragId by remember { mutableStateOf<String?>(null) }
    // Centre of the floating card, in root coordinates.
    var pointerY by remember { mutableFloatStateOf(0f) }
    // 0 while carried under the finger, 1 once it has landed.
    var landing by remember { mutableFloatStateOf(0f) }
    // Frozen at drag start: the 更多工具 title shifts a whole row whenever a card
    // crosses it, and a live boundary would flip the side back and forth under a
    // finger resting on the line.
    var dragBoundary by remember { mutableFloatStateOf(0f) }
    // Which side the drag currently belongs to; only a decisive crossing flips
    // it, see [CommonSectionHysteresis].
    var dragInCommon by remember { mutableStateOf(true) }
    var settleJob by remember { mutableStateOf<Job?>(null) }

    fun tick() {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.SEGMENT_TICK
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            },
        )
    }

    /** Which slot the floating card is over: section, then index inside it. */
    fun slotAt(pointer: Float): Pair<Boolean, Int>? {
        val id = dragId ?: return null
        val state = WyuToolLayoutStore.state.value
        val toCommon = when {
            pointer < dragBoundary - hysteresisPx -> true
            pointer > dragBoundary + hysteresisPx -> false
            else -> dragInCommon
        }
        val list = if (toCommon) state.common else state.more
        // The card itself is not a slot: count the others the finger has already
        // passed, which is exactly the index it would be inserted at.
        //
        // Which *line* of a neighbour counts depends on where that neighbour
        // currently sits: rows already above the carried card must be pushed up
        // and give way at their bottom edge, rows still below give way at their
        // top edge. Reading that from the committed order instead of a position
        // test is what gives each swap its dead band — a finger resting on a card
        // cannot flip it back and forth.
        val current = list.indexOf(id)
        val above = list.withIndex().count { (index, other) ->
            if (other == id) return@count false
            val top = rowTops[other] ?: return@count false
            val lead = if (current >= 0 && index < current) giveWayFromBelowPx else giveWayFromAbovePx
            pointer > top + lead
        }
        return toCommon to above
    }

    fun followFinger() {
        val slot = slotAt(pointerY) ?: return
        dragInCommon = slot.first
        val tool = dragId?.let(WyuTool::fromId) ?: return
        val state = WyuToolLayoutStore.state.value
        val list = if (slot.first) state.common else state.more
        val at = slot.second.coerceIn(0, list.size)
        // Already where the finger wants it: nothing to commit, and no tick. This
        // keeps a slowly moving finger from re-inserting the card (and buzzing)
        // on every frame it stays in the same slot. -1 means the card is still in
        // the other list, which always has to move.
        if (list.indexOf(tool.id) == at) return
        WyuToolLayoutStore.moveTool(tool, slot.first, at)
        tick()
    }

    WyuPageScaffold(
        progress = { fraction.value },
        maskCollapseHeight = WyuLargeTitleHeight,
        topBar = {
            WyuActionBar(
                title = "常用卡片",
                collapsedFraction = { fraction.value },
                navigationIcon = {
                    WyuTopBarIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        onClick = onBack,
                    )
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { boxTop = it.positionInRoot().y },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(top = padding.calculateTopPadding()),
            ) {
                SmallTitle(
                    text = "显示的卡片",
                    insideMargin = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                )
                if (commonTools.isEmpty()) {
                    SectionHint("常用还是空的，把下面的卡片拖上来吧。")
                }
                val items = buildList {
                    commonTools.forEach { tool ->
                        add(CommonItem.Tool(tool, inCommon = true))
                    }
                    add(CommonItem.MoreHeader)
                    moreTools.forEach { tool ->
                        add(CommonItem.Tool(tool, inCommon = false))
                    }
                }
                // Absolute row number over the whole column (both lists, plus the
                // 更多工具 block). Reorder offsets are computed from these, so a
                // row only animates when it truly moves on screen — including the
                // title, which rides up a slot whenever a card leaves 显示的卡片
                // and pushes the whole 更多工具 section's top edge up with it.
                items.forEachIndexed { row, item ->
                    key(item.key) {
                        when (item) {
                            is CommonItem.Tool -> ToolCardRow(
                                tool = item.tool,
                                row = row,
                                pitchPx = pitchPx,
                                dragging = dragId == item.tool.id,
                                onPlaced = { top -> rowTops[item.tool.id] = top },
                                onHold = { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) },
                                onDragStart = { fingerY ->
                                    settleJob?.cancel()
                                    settleJob = null
                                    landing = 0f
                                    dragId = item.tool.id
                                    pointerY = fingerY
                                    dragBoundary = moreHeaderTop + moreTitleOffset
                                    dragInCommon = item.inCommon
                                },
                                onDragMove = { dy ->
                                    pointerY += dy
                                    followFinger()
                                },
                                onDragEnd = end@{
                                    // A stale end from a superseded gesture must
                                    // not fly a newer card home.
                                    if (dragId != item.tool.id) return@end
                                    // Fly the carried card home instead of
                                    // letting it vanish under the finger.
                                    val carried = item.tool.id
                                    val from = pointerY
                                    settleJob?.cancel()
                                    settleJob = scope.launch {
                                        // One frame of slack so a move committed
                                        // by the last touch event has been laid
                                        // out before the landing spot is read.
                                        withFrameNanos { }
                                        val slotTop = rowTops[carried]
                                        if (slotTop == null) {
                                            dragId = null
                                            return@launch
                                        }
                                        val to = slotTop + rowHeightPx / 2f
                                        // Pressed and released without moving:
                                        // nothing to fly back from.
                                        if (abs(to - from) < 1f) {
                                            dragId = null
                                            return@launch
                                        }
                                        Animatable(0f).animateTo(
                                            targetValue = 1f,
                                            animationSpec = tween(
                                                durationMillis = CommonSettleMillis,
                                                easing = CubicBezierEasing(
                                                    0.18f,
                                                    0.86f,
                                                    0.2f,
                                                    1f,
                                                ),
                                            ),
                                        ) {
                                            pointerY = from + (to - from) * value
                                            landing = value
                                        }
                                        pointerY = to
                                        dragId = null
                                    }
                                },
                            )

                            CommonItem.MoreHeader -> ReorderRow(
                                index = row,
                                pitchPx = pitchPx,
                                onPlaced = { moreHeaderTop = it },
                            ) {
                                MoreToolsHeader(
                                    empty = moreTools.isEmpty(),
                                    emptyText = "所有工具都在常用页了。",
                                    onTitleOffset = { moreTitleOffset = it },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }

            // The card under the finger: the very same glass card, not a flat
            // stand-in. It lives outside the scrolling column so it is never
            // clipped by the list it came from.
            val dragged = dragId?.let(WyuTool::fromId)
            if (dragged != null) {
                val shape = remember { WyuSquircleShape(WyuCardCornerRadius) }
                val lift = 1f - landing
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset {
                            IntOffset(
                                x = 0,
                                y = (pointerY - rowHeightPx / 2f - boxTop).roundToInt(),
                            )
                        }
                        .padding(horizontal = CommonCardSideInset)
                        .graphicsLayer {
                            val scale = 1f + CommonLiftScale * lift
                            scaleX = scale
                            scaleY = scale
                        },
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 18.dp * lift,
                                shape = shape,
                                clip = false,
                                ambientColor = Color.Black.copy(alpha = 0.16f * lift),
                                spotColor = Color.Black.copy(alpha = 0.24f * lift),
                            ),
                        cornerRadius = WyuCardCornerRadius,
                        insideMargin = PaddingValues(0.dp),
                    ) {
                        ToolRow(
                            tool = dragged,
                            handle = { _ -> DragHandle(active = lift > 0.5f) },
                        )
                    }
                }
            }
        }
    }
}

/** A tool card, wrapped so it can spring into its new slot on any reorder. */
@Composable
private fun ToolCardRow(
    tool: WyuTool,
    row: Int,
    pitchPx: Float,
    dragging: Boolean,
    onPlaced: (Float) -> Unit,
    onHold: () -> Unit,
    onDragStart: (Float) -> Unit,
    onDragMove: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    ReorderRow(index = row, pitchPx = pitchPx, onPlaced = onPlaced) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = CommonCardSideInset,
                    end = CommonCardSideInset,
                    bottom = CommonCardSpacing,
                )
                // The whole card goes, surface included: hiding only its content
                // left an empty pane of glass sitting in the list while the real
                // card was being carried around.
                .alpha(if (dragging) 0f else 1f),
            cornerRadius = WyuCardCornerRadius,
            samplePageContent = false,
            insideMargin = PaddingValues(0.dp),
        ) {
            ToolRow(
                tool = tool,
                handle = { coords ->
                    DragHandle(
                        active = dragging,
                        modifier = Modifier.dragGesture(
                            id = tool.id,
                            coords = coords,
                            onHold = onHold,
                            onStart = onDragStart,
                            onMove = onDragMove,
                            onEnd = onDragEnd,
                        ),
                    )
                },
            )
        }
    }
}

/**
 * The band between the two lists: how the drag works, then the 更多工具 title
 * that doubles as the side line for the drop maths.
 *
 * [onTitleOffset] reports where the title sits *inside* this block. The block is
 * translated by its reorder spring, so only a parent-relative offset is stable
 * while it slides; the caller adds it to the block's settled top.
 */
@Composable
private fun MoreToolsHeader(
    empty: Boolean,
    emptyText: String,
    onTitleOffset: (Float) -> Unit,
) {
    Column {
        Text(
            text = "按住右侧把手，把卡片拖到下面就是收进「更多」，拖回上面就是放回常用。",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 10.dp),
        )
        Box(Modifier.onGloballyPositioned { onTitleOffset(it.positionInParent().y) }) {
            SmallTitle(
                text = "更多工具",
                insideMargin = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        if (empty) SectionHint(emptyText)
    }
}

@Composable
private fun SectionHint(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
    )
}

/**
 * Animates a card from where it used to sit to where it sits now: the column has
 * already jumped to the new layout, so the card starts offset by the slots it
 * travelled and springs home. That travel is the 补位 the eye reads as "the
 * others made room".
 *
 * [index] is the row's number over the **whole** column (both lists and the
 * 更多工具 block), never its index inside one section. A card crossing the
 * 更多工具 title keeps its absolute row and must therefore stay perfectly still
 * while the title slides up past it — per-section indices made it read as a slot
 * change and produced a jump-up-then-settle wobble. Absolute rows also let the
 * title itself be animated by the same machine: dropping a card into 更多 gives
 * the title one row less above it, so it glides up a slot while the cards below
 * open the gap one at a time.
 *
 * The offset is *born* with the new slot (`remember(index)`) instead of starting
 * at zero and being snapped afterwards. A row whose offset is still 0 when the
 * new layout is drawn lands at its destination for one frame and then jumps back
 * to travel — that one frame is the flash seen while dragging.
 *
 * [onPlaced] reports the slot's root top from **outside** the spring layer, so
 * callers always see where the row has settled rather than where the animation
 * currently has it.
 */
@Composable
private fun ReorderRow(
    index: Int,
    pitchPx: Float,
    onPlaced: (Float) -> Unit,
    content: @Composable () -> Unit,
) {
    // Plain field rather than state: nothing observes it, and staying out of the
    // snapshot system lets the new offset be ready while this very frame is
    // composed, so the first draw already shows the row where it came from.
    val slot = remember { SlotMemory(index) }
    val shift = remember(index) {
        val delta = (slot.index - index) * pitchPx
        slot.index = index
        Animatable(delta)
    }
    LaunchedEffect(shift) {
        if (shift.value == 0f) return@LaunchedEffect
        shift.animateTo(
            targetValue = 0f,
            animationSpec = spring(
                dampingRatio = 0.9f,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }
    Box(Modifier.onGloballyPositioned { onPlaced(it.positionInRoot().y) }) {
        Box(Modifier.graphicsLayer { translationY = shift.value }) { content() }
    }
}

/** Which slot a row was last composed in; bookkeeping for [ReorderRow]. */
private class SlotMemory(var index: Int)

/** Icon + name; the drag handle is the only interaction the card has. */
@Composable
private fun ToolRow(
    tool: WyuTool,
    modifier: Modifier = Modifier,
    handle: (@Composable (() -> LayoutCoordinates?) -> Unit)? = null,
) {
    val primary = MiuixTheme.colorScheme.primary
    var handleCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(CommonRowHeight)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Bare glyph: the card itself is the surface, so a tinted tile behind
        // the icon only added a second, louder square.
        Box(
            modifier = Modifier.size(38.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = primary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tool.title,
                style = MiuixTheme.textStyles.headline2,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = tool.summary,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
            )
        }
        if (handle != null) {
            // The coordinates are read through a lambda, not captured: they
            // change on every layout pass and a changing key would restart the
            // drag gesture under the finger.
            Box(Modifier.onGloballyPositioned { handleCoords = it }) {
                handle({ handleCoords })
            }
        }
    }
}

/** Grab handle: the card follows the finger, the lists reflow under it. */
@Composable
private fun DragHandle(
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Box(
        modifier = modifier
            .width(CommonHandleWidth)
            .height(CommonRowHeight),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(rememberWyuIconShape(28.dp))
                .background(
                    if (active) {
                        MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)
                    } else {
                        Color.Transparent
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.DragHandle,
                contentDescription = "拖动排序",
                modifier = Modifier.size(18.dp),
                tint = if (active) MiuixTheme.colorScheme.primary else tint,
            )
        }
    }
}

/**
 * Drag owner for one handle. Nothing is claimed until the finger has been held
 * for [CommonHoldMillis]: the page scrolls vertically, and a hand sliding past
 * the handles would otherwise grab a card on the way. Once the hold lands the
 * gesture is taken over from the DOWN, so a scroll can never steal it back.
 */
private fun Modifier.dragGesture(
    id: String,
    coords: () -> LayoutCoordinates?,
    onHold: () -> Unit,
    onStart: (fingerY: Float) -> Unit,
    onMove: (dy: Float) -> Unit,
    onEnd: () -> Unit,
): Modifier = pointerInput(id) {
    awaitEachGesture {
        val down = awaitFirstDown()
        coords() ?: return@awaitEachGesture
        if (!awaitHold(down)) return@awaitEachGesture
        onHold()
        // Read after the hold, not before: a row that was still springing into
        // place has settled by now, so the card starts from its real slot.
        val handle = coords() ?: return@awaitEachGesture
        onStart(handle.positionInRoot().y + down.position.y)
        down.consume()
        var started = false
        var travel = 0f
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.changedToUpIgnoreConsumed()) {
                change.consume()
                break
            }
            val dy = change.positionChange().y
            travel += dy
            if (!started) {
                if (abs(travel) > viewConfiguration.touchSlop) {
                    started = true
                    onMove(travel)
                }
            } else {
                onMove(dy)
            }
            change.consume()
        }
        onEnd()
    }
}

/**
 * Waits out [CommonHoldMillis] while the finger stays put. False means the
 * gesture was not a hold — the finger lifted, travelled past the touch slop, or
 * the page claimed the event — so the card is left alone and the page keeps it.
 */
private suspend fun AwaitPointerEventScope.awaitHold(down: PointerInputChange): Boolean {
    var aborted = false
    val finished = withTimeoutOrNull(CommonHoldMillis) {
        while (!aborted) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
            aborted = change == null ||
                change.changedToUpIgnoreConsumed() ||
                change.isConsumed ||
                (change.position - down.position).getDistance() > viewConfiguration.touchSlop
        }
        Unit
    }
    return finished == null && !aborted
}
