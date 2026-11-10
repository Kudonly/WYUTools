package com.wyutools.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.captionBarPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.anim.DecelerateEasing
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils

private val DialogCardCorner = 32.dp
private val DialogScreenMargin = 16.dp
private const val DialogCardTint = 0.62f
// Dialogs keep their own fixed blur instead of following the soft-glass slider;
// the dark sheet can carry 50%, while light mode stays at 20% to keep the
// white-tinted card readable.
private const val DialogSoftGlassBlurPercentLight = 20
private const val DialogSoftGlassBlurPercentDark = 50

/**
 * Every dialog goes through here. The card itself is frosted glass: it samples
 * the current page's backdrop and draws it blurred under a translucent tint.
 * With soft glass enabled the blur becomes the same glass material as cards,
 * at a fixed radius that is lower in light mode. The page behind stays sharp;
 * only the standard window dim applies.
 */
@Composable
fun WyuOverlayDialog(
    show: Boolean,
    modifier: Modifier = Modifier,
    title: String? = null,
    summary: String? = null,
    enableWindowDim: Boolean = true,
    dismissOnOutsideTap: Boolean = true,
    onDismissRequest: (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f, visibilityThreshold = 0.0001f) }
    val dimProgress = remember { Animatable(0f) }
    val internallyVisible = remember { mutableStateOf(false) }
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val currentOnDismissFinished by rememberUpdatedState(onDismissFinished)
    val currentOutsideDismiss by rememberUpdatedState(dismissOnOutsideTap)

    LaunchedEffect(show) {
        if (show) {
            internallyVisible.value = true
            launch { dimProgress.animateTo(1f, tween(340, easing = DecelerateEasing(1.5f))) }
            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.88f,
                    stiffness = 380f,
                    visibilityThreshold = 0.0001f,
                ),
            )
        } else {
            if (!internallyVisible.value) return@LaunchedEffect
            launch { dimProgress.animateTo(0f, tween(300, easing = DecelerateEasing(1.5f))) }
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(320, easing = DecelerateEasing(1.5f)),
            )
            dimProgress.snapTo(0f)
            internallyVisible.value = false
            currentOnDismissFinished?.invoke()
        }
    }

    if (!show && !internallyVisible.value) return

    val windowInfo = LocalWindowInfo.current
    val screenHeightPx = with(LocalDensity.current) {
        windowInfo.containerDpSize.height.toPx()
    }
    val backdrop = WyuPageBackdrops.current
    val surface = MiuixTheme.colorScheme.background
    val dark = LocalWyuDarkTheme.current
    val softGlass by WyuAppearance.softGlass.collectAsState()
    val tuning by WyuAppearance.softGlassTuning.collectAsState()
    val useSoftGlass = backdrop != null && softGlass && wyuSoftGlassSupported
    val cardColor = when {
        backdrop == null -> surface
        useSoftGlass -> Color.Transparent
        else -> surface.copy(alpha = DialogCardTint)
    }
    val dialogShape = remember { WyuSquircleShape(DialogCardCorner) }
    val frostModifier = if (backdrop != null) {
        if (useSoftGlass) {
            Modifier.wyuSoftGlass(
                backdrop = backdrop,
                shape = dialogShape,
                glassShape = WyuSoftGlassShape.RoundedRect,
                cornerRadius = DialogCardCorner,
                blurPercent = if (dark) {
                    DialogSoftGlassBlurPercentDark
                } else {
                    DialogSoftGlassBlurPercentLight
                },
                baseTint = if (dark) {
                    Color.Transparent
                } else {
                    Color.White.copy(alpha = SoftGlassWhiteTintAlpha)
                },
                shapeEdgePx = tuning.shapeEdgePx,
                shapeEdgePow = tuning.shapeEdgePow,
                refractionThicknessPx = tuning.edgeThickness,
                reflectionThicknessPx = tuning.reflectOffset,
                reflectionLighten = tuning.reflectLighten,
                bgSaturation = tuning.saturation(dark),
                reflectionStrength = tuning.reflectionStrength,
                refractionIor = tuning.refractionIor,
            )
        } else {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = 44f,
            )
        }
    } else {
        Modifier
    }
    val topInset = maxOf(
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
        WindowInsets.captionBar.asPaddingValues().calculateTopPadding(),
        WindowInsets.displayCutout.asPaddingValues().calculateTopPadding(),
    )

    MiuixPopupUtils.DialogLayout(
        visible = internallyVisible,
        enableWindowDim = false,
        enterTransition = EnterTransition.None,
        exitTransition = ExitTransition.None,
    ) {
        BackHandler(enabled = show && onDismissRequest != null) {
            currentOnDismissRequest?.invoke()
        }
        Box(Modifier.fillMaxSize()) {
            val dimColor = if (enableWindowDim) MiuixTheme.colorScheme.windowDimming else Color.Transparent
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(dimColor.copy(alpha = dimColor.alpha * dimProgress.value))
                    }
                    .pointerInput(Unit) {
                        detectTapGestures {
                            if (currentOutsideDismiss) currentOnDismissRequest?.invoke()
                        }
                    }
                    .semantics {
                        onClick(label = "Dismiss") {
                            currentOnDismissRequest?.invoke()
                            true
                        }
                    },
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .navigationBarsPadding()
                    .captionBarPadding()
                    .padding(
                        start = DialogScreenMargin,
                        end = DialogScreenMargin,
                        top = topInset,
                        bottom = DialogScreenMargin,
                    ),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = modifier
                        .graphicsLayer {
                            translationY = (1f - progress.value) * screenHeightPx
                        }
                        .widthIn(max = 440.dp)
                        .fillMaxWidth()
                        .squircleClip(DialogCardCorner)
                        .then(frostModifier)
                        .background(cardColor)
                        .squircleBorder(
                            width = { 0.5.dp },
                            color = {
                                if (dark) {
                                    Color.White.copy(alpha = 0.16f)
                                } else {
                                    Color.White.copy(alpha = 0.5f)
                                }
                            },
                            cornerRadius = DialogCardCorner,
                        )
                        .pointerInput(Unit) { detectTapGestures { } }
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                ) {
                    title?.let {
                        Text(
                            text = it,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            style = MiuixTheme.textStyles.body1,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    summary?.let {
                        Text(
                            text = it,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            style = MiuixTheme.textStyles.body2,
                            textAlign = TextAlign.Center,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    CompositionLocalProvider(LocalWyuGlassBackdrop provides null) {
                        content()
                    }
                }
            }
        }
    }
}

enum class WyuDialogButtonStyle {
    Primary,
    Secondary,
    Destructive,
    Cancel,
}

@Composable
fun WyuDialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    style: WyuDialogButtonStyle = WyuDialogButtonStyle.Primary,
    loading: Boolean = false,
    loadingText: String? = null,
) {
    val dark = LocalWyuDarkTheme.current
    val neutralFill = if (dark) {
        Color.White.copy(alpha = 0.12f)
    } else {
        Color.White.copy(alpha = 0.55f)
    }
    val colors = when (style) {
        WyuDialogButtonStyle.Primary -> ButtonDefaults.buttonColorsPrimary()
        WyuDialogButtonStyle.Secondary -> ButtonDefaults.buttonColors(
            color = neutralFill,
            disabledColor = neutralFill.copy(alpha = neutralFill.alpha * 0.5f),
            contentColor = MiuixTheme.colorScheme.onBackground,
            disabledContentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
                .copy(alpha = 0.5f),
        )
        WyuDialogButtonStyle.Destructive -> ButtonDefaults.buttonColors(
            color = MiuixTheme.colorScheme.error,
            disabledColor = MiuixTheme.colorScheme.error.copy(alpha = 0.35f),
            contentColor = MiuixTheme.colorScheme.onError,
            disabledContentColor = MiuixTheme.colorScheme.onError.copy(alpha = 0.7f),
        )
        WyuDialogButtonStyle.Cancel -> ButtonDefaults.buttonColors(
            color = neutralFill,
            disabledColor = neutralFill.copy(alpha = neutralFill.alpha * 0.5f),
            contentColor = MiuixTheme.colorScheme.error,
            disabledContentColor = MiuixTheme.colorScheme.error.copy(alpha = 0.4f),
        )
    }
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !loading,
        minWidth = 0.dp,
        minHeight = 52.dp,
        cornerRadius = 26.dp,
        colors = colors,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                size = 18.dp,
                strokeWidth = 2.dp,
                colors = ProgressIndicatorDefaults.progressIndicatorColors(
                    foregroundColor = LocalContentColor.current,
                ),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = if (loading && loadingText != null) loadingText else text,
            style = MiuixTheme.textStyles.button,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun WyuDialogCancelButton(
    text: String = "取消",
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
) {
    WyuDialogButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        style = WyuDialogButtonStyle.Cancel,
    )
}

/** A full-width, centered action row, matching HyperLight's action-sheet rows. */
@Composable
fun WyuDialogOption(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        minWidth = 0.dp,
        minHeight = 54.dp,
        cornerRadius = 20.dp,
        colors = ButtonDefaults.buttonColors(
            color = Color.Transparent,
            disabledColor = Color.Transparent,
            contentColor = MiuixTheme.colorScheme.onBackground,
            disabledContentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
                .copy(alpha = 0.5f),
        ),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.title3,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun MiuixConfirmDialog(
    show: Boolean,
    title: String,
    message: String,
    confirmText: String = "确定",
    dismissText: String = "取消",
    destructive: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    WyuOverlayDialog(
        show = show,
        title = title,
        summary = message,
        onDismissRequest = onDismiss,
    ) {
        Spacer(Modifier.height(16.dp))
        WyuDialogButton(
            text = confirmText,
            onClick = onConfirm,
            style = if (destructive) {
                WyuDialogButtonStyle.Destructive
            } else {
                WyuDialogButtonStyle.Primary
            },
        )
        Spacer(Modifier.height(8.dp))
        WyuDialogCancelButton(
            text = dismissText,
            onClick = onDismiss,
        )
    }
}

@Composable
fun ActionChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        minHeight = 32.dp,
        cornerRadius = 12.dp,
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        colors = if (primary) {
            ButtonDefaults.buttonColorsPrimary()
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = LocalContentColor.current,
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
        )
    }
}
