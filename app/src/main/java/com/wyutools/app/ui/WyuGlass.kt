package com.wyutools.app.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonColors
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.squircle.addSquircleRect
import top.yukonga.miuix.kmp.utils.PressFeedbackType

private const val CardGlassAlpha = 0.55f
private const val ButtonGlassAlpha = 0.78f

/**
 * Miuix renders card and button surfaces with its continuous-corner squircle,
 * so the blur underneath must share that silhouette; a plain rounded rect
 * leaves visible slivers at the four corners.
 */
private class WyuSquircleShape(private val cornerRadius: Dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        if (size.width <= 0f || size.height <= 0f) return Outline.Rectangle(Rect.Zero)
        val radius = with(density) { cornerRadius.toPx() }
        return Outline.Generic(
            Path().apply { addSquircleRect(size.width, size.height, radius) },
        )
    }
}
private const val CardBlurRadius = 38f
private const val ButtonBlurRadius = 30f

/**
 * Frosted-glass variants of Miuix's card and button. They stay identical to the
 * originals unless a wallpaper-only backdrop is published, which only happens
 * while a custom wallpaper is active.
 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = CardDefaults.CornerRadius,
    insideMargin: PaddingValues = CardDefaults.InsideMargin,
    colors: CardColors = CardDefaults.defaultColors(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = LocalWyuGlassBackdrop.current
    val shape = remember(cornerRadius) { WyuSquircleShape(cornerRadius) }
    MiuixCard(
        modifier = if (glass != null) {
            modifier.textureBlur(backdrop = glass, shape = shape, blurRadius = CardBlurRadius)
        } else {
            modifier
        },
        cornerRadius = cornerRadius,
        insideMargin = insideMargin,
        colors = if (glass != null) colors.frosted(CardGlassAlpha) else colors,
        content = content,
    )
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = CardDefaults.CornerRadius,
    insideMargin: PaddingValues = CardDefaults.InsideMargin,
    colors: CardColors = CardDefaults.defaultColors(),
    pressFeedbackType: PressFeedbackType = PressFeedbackType.None,
    showIndication: Boolean = false,
    holdDownState: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = LocalWyuGlassBackdrop.current
    val shape = remember(cornerRadius) { WyuSquircleShape(cornerRadius) }
    MiuixCard(
        modifier = if (glass != null) {
            modifier.textureBlur(backdrop = glass, shape = shape, blurRadius = CardBlurRadius)
        } else {
            modifier
        },
        cornerRadius = cornerRadius,
        insideMargin = insideMargin,
        colors = if (glass != null) colors.frosted(CardGlassAlpha) else colors,
        pressFeedbackType = pressFeedbackType,
        showIndication = showIndication,
        holdDownState = holdDownState,
        onClick = onClick,
        onLongPress = onLongPress,
        content = content,
    )
}

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = ButtonDefaults.CornerRadius,
    minWidth: Dp = ButtonDefaults.MinWidth,
    minHeight: Dp = ButtonDefaults.MinHeight,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    insideMargin: PaddingValues = ButtonDefaults.InsideMargin,
    content: @Composable RowScope.() -> Unit,
) {
    val glass = LocalWyuGlassBackdrop.current
    val useGlass = glass != null && colors.color.alpha > 0.01f
    val shape = remember(cornerRadius) { WyuSquircleShape(cornerRadius) }
    MiuixButton(
        onClick = onClick,
        modifier = if (useGlass) {
            modifier.textureBlur(backdrop = glass, shape = shape, blurRadius = ButtonBlurRadius)
        } else {
            modifier
        },
        enabled = enabled,
        cornerRadius = cornerRadius,
        minWidth = minWidth,
        minHeight = minHeight,
        colors = if (useGlass) colors.frosted(ButtonGlassAlpha) else colors,
        insideMargin = insideMargin,
        content = content,
    )
}

private fun CardColors.frosted(alpha: Float): CardColors =
    if (color.alpha >= 0.99f) copy(color = color.copy(alpha = alpha)) else this

private fun ButtonColors.frosted(alpha: Float): ButtonColors = copy(
    color = color.glassTint(alpha),
    disabledColor = disabledColor.glassTint(alpha * 0.75f),
)

private fun Color.glassTint(alpha: Float): Color =
    if (this.alpha >= 0.99f) copy(alpha = alpha) else this
