package com.wyutools.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.wyutools.app.auth.SliderChallenge
import com.wyutools.app.auth.SliderPayload
import com.wyutools.app.auth.SliderTrack
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private const val CanvasWidthDp = 280
private const val CanvasHeightDp = 155
private const val HandleSizeDp = 36f
private const val MaxOffset = CanvasWidthDp - HandleSizeDp

@Composable
fun SliderCaptchaDialog(
    challenge: SliderChallenge,
    verifying: Boolean,
    error: String?,
    onVerify: (SliderPayload) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val bigBitmap = remember(challenge) {
        runCatching {
            BitmapFactory.decodeByteArray(
                challenge.bigImage,
                0,
                challenge.bigImage.size,
            )
        }.getOrNull()
    }
    val smallBitmap = remember(challenge) {
        runCatching {
            BitmapFactory.decodeByteArray(
                challenge.smallImage,
                0,
                challenge.smallImage.size,
            )
        }.getOrNull()
    }
    val puzzleWidth = remember(challenge, bigBitmap, smallBitmap) {
        if (bigBitmap != null && smallBitmap != null && bigBitmap.width > 0) {
            (smallBitmap.width * (CanvasWidthDp / bigBitmap.width.toFloat()))
                .coerceIn(24f, CanvasWidthDp.toFloat())
                .dp
        } else {
            (CanvasWidthDp / 3f).dp
        }
    }
    val imagesReady = bigBitmap != null && smallBitmap != null

    var offset by remember(challenge) { mutableStateOf(0f) }
    var dragging by remember(challenge) { mutableStateOf(false) }
    val tracker = remember(challenge) { SliderDragTracker() }
    // The handle is positioned in dp, but drag deltas arrive in pixels. Without
    // this conversion the handle crawls at 1/density of the finger's speed.
    val density = LocalDensity.current

    LaunchedEffect(error) {
        if (error != null) {
            delay(900)
            onRefresh()
        }
    }

    val dragModifier = if (!imagesReady || verifying) {
        Modifier
    } else {
        Modifier.pointerInput(challenge, verifying) {
            detectHorizontalDragGestures(
                onDragStart = {
                    dragging = true
                    tracker.start(System.currentTimeMillis())
                },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    val deltaDp = with(density) { dragAmount.toDp().value }
                    val next = (offset + deltaDp).coerceIn(0f, MaxOffset)
                    offset = next
                    tracker.sample(next.roundToInt(), System.currentTimeMillis())
                },
                onDragEnd = {
                    dragging = false
                    val end = offset.roundToInt()
                    onVerify(tracker.finish(end, System.currentTimeMillis()))
                },
                onDragCancel = { dragging = false },
            )
        }
    }

    var visible by remember { mutableStateOf(true) }
    WyuOverlayDialog(
        show = visible,
        dismissOnOutsideTap = !verifying,
        onDismissRequest = { if (!verifying) visible = false },
        onDismissFinished = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "请完成安全验证",
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRefresh, enabled = !verifying) {
                    Icon(Icons.Filled.Refresh, contentDescription = "换一张")
                }
                IconButton(onClick = { visible = false }, enabled = !verifying) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭")
                }
            }
            Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(CanvasWidthDp.dp)
                        .height(CanvasHeightDp.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MiuixTheme.colorScheme.surfaceVariant),
                ) {
                    if (bigBitmap != null) {
                        Image(
                            bitmap = bigBitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (smallBitmap != null) {
                        Image(
                            bitmap = smallBitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .offset(x = offset.dp)
                                .width(puzzleWidth)
                                .height(CanvasHeightDp.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .width(CanvasWidthDp.dp)
                        .height(HandleSizeDp.dp),
                ) {
                    Surface(
                        color = MiuixTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = barText(verifying, dragging, error),
                                style = MiuixTheme.textStyles.footnote1,
                                color = if (error != null) {
                                    MiuixTheme.colorScheme.error
                                } else {
                                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                                },
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .width((offset + HandleSizeDp).dp)
                            .height(HandleSizeDp.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                MiuixTheme.colorScheme.primaryContainer
                                    .copy(alpha = 0.55f),
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .offset(x = offset.dp)
                            .size(HandleSizeDp.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (dragging) {
                                    MiuixTheme.colorScheme.primary
                                } else {
                                    MiuixTheme.colorScheme.surface
                                },
                            )
                            .border(
                                width = 1.dp,
                                color = if (dragging) {
                                    MiuixTheme.colorScheme.primary
                                } else {
                                    MiuixTheme.colorScheme.dividerLine
                                },
                                shape = RoundedCornerShape(8.dp),
                            )
                            .then(dragModifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (verifying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = if (dragging) {
                                    MiuixTheme.colorScheme.onPrimary
                                } else {
                                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                                },
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = when {
                        !imagesReady -> "验证码图片加载失败，请点击右上角刷新。"
                        error != null -> error
                        else -> "拖动小拼图到缺口位置即可"
                    },
                    style = MiuixTheme.textStyles.footnote1,
                    color = if (error != null || !imagesReady) {
                        MiuixTheme.colorScheme.error
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

private fun barText(verifying: Boolean, dragging: Boolean, error: String?): String = when {
    verifying -> "校验中…"
    error != null -> "请重试"
    dragging -> "松手完成验证"
    else -> "向右滑动填充拼图"
}

private class SliderDragTracker {
    private val tracks = mutableListOf<SliderTrack>()
    private var lastA = 0
    private var lastT = 0L
    private var started = false

    fun start(now: Long) {
        tracks.clear()
        tracks.add(SliderTrack(0, 0, 0))
        lastA = 0
        lastT = now
        started = true
    }

    fun sample(a: Int, now: Long) {
        if (!started) return
        val b = syntheticY(a)
        val dt = (now - lastT).toInt()
        val db = b - syntheticY(lastA)
        val distance = hypot((a - lastA).toDouble(), db.toDouble())
        if (dt < 20 || distance < 2) return
        tracks.add(SliderTrack(a, b, dt))
        lastA = a
        lastT = now
    }

    fun finish(a: Int, now: Long): SliderPayload {
        val b = syntheticY(a)
        val dt = if (started) (now - lastT).toInt() else 0
        tracks.add(SliderTrack(a, b, dt))
        started = false
        return SliderPayload(
            canvasLength = CanvasWidthDp,
            moveLength = a,
            tracks = tracks.toList(),
        )
    }

    private fun syntheticY(a: Int): Int = (sin(a / 12.0) * 1.8).roundToInt()
}
