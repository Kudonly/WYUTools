package com.wyutools.app.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonColors
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.basic.TextFieldColors
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.squircle.addSquircleRect
import top.yukonga.miuix.kmp.utils.PressFeedbackType

private const val CardGlassAlpha = 0.55f
private const val CardSoftGlassAlpha = 0f
private const val CardSoftGlassTintAlpha = 0.64f
private const val CardSoftGlassBlurScale = 0.6f
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
    val softGlass by WyuAppearance.softGlass.collectAsState()
    val softGlassBlur by WyuAppearance.softGlassBlur.collectAsState()
    val dark = LocalWyuDarkTheme.current
    // Default containers keep the white/dark glass wash; custom containers
    // (e.g. elec card primaryContainer) carry their own tint into the shader.
    val softGlassCardTint = when {
        colors.color != MiuixTheme.colorScheme.surfaceContainer ->
            colors.color.copy(alpha = CardSoftGlassTintAlpha)
        dark -> Color.Transparent
        else -> Color.White.copy(alpha = CardSoftGlassTintAlpha)
    }
    val shape = remember(cornerRadius) { WyuSquircleShape(cornerRadius) }
    MiuixCard(
        modifier = when {
            glass == null -> modifier
            softGlass && wyuSoftGlassSupported -> modifier.wyuSoftGlass(
                backdrop = glass,
                shape = shape,
                glassShape = WyuSoftGlassShape.RoundedRect,
                cornerRadius = cornerRadius,
                blurScale = CardSoftGlassBlurScale,
                blurPercent = softGlassBlur,
                baseTint = softGlassCardTint,
                bgSaturation = softGlassBgSaturation(dark),
            )
            else -> modifier.textureBlur(backdrop = glass, shape = shape, blurRadius = CardBlurRadius)
        },
        cornerRadius = cornerRadius,
        insideMargin = insideMargin,
        colors = if (glass != null) {
            colors.frosted(if (softGlass && wyuSoftGlassSupported) CardSoftGlassAlpha else CardGlassAlpha)
        } else {
            colors
        },
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
    val softGlass by WyuAppearance.softGlass.collectAsState()
    val softGlassBlur by WyuAppearance.softGlassBlur.collectAsState()
    val dark = LocalWyuDarkTheme.current
    // Default containers keep the white/dark glass wash; custom containers
    // (e.g. elec card primaryContainer) carry their own tint into the shader.
    val softGlassCardTint = when {
        colors.color != MiuixTheme.colorScheme.surfaceContainer ->
            colors.color.copy(alpha = CardSoftGlassTintAlpha)
        dark -> Color.Transparent
        else -> Color.White.copy(alpha = CardSoftGlassTintAlpha)
    }
    val shape = remember(cornerRadius) { WyuSquircleShape(cornerRadius) }
    MiuixCard(
        modifier = when {
            glass == null -> modifier
            softGlass && wyuSoftGlassSupported -> modifier.wyuSoftGlass(
                backdrop = glass,
                shape = shape,
                glassShape = WyuSoftGlassShape.RoundedRect,
                cornerRadius = cornerRadius,
                blurScale = CardSoftGlassBlurScale,
                blurPercent = softGlassBlur,
                baseTint = softGlassCardTint,
                bgSaturation = softGlassBgSaturation(dark),
            )
            else -> modifier.textureBlur(backdrop = glass, shape = shape, blurRadius = CardBlurRadius)
        },
        cornerRadius = cornerRadius,
        insideMargin = insideMargin,
        colors = if (glass != null) {
            colors.frosted(if (softGlass && wyuSoftGlassSupported) CardSoftGlassAlpha else CardGlassAlpha)
        } else {
            colors
        },
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

private const val InputBlurRadius = 46f

/**
 * Text field on a frosted surface. The fill sits a touch lighter than the
 * card's glass tint so the field reads as an inset without breaking the
 * surface; without a wallpaper backdrop it falls back to a quiet wash.
 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val glass = LocalWyuGlassBackdrop.current
    val shape = remember { WyuSquircleShape(TextFieldDefaults.CornerRadius) }
    MiuixTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = if (glass != null) {
            modifier.textureBlur(backdrop = glass, shape = shape, blurRadius = InputBlurRadius)
        } else {
            modifier
        },
        colors = glassTextFieldColors(glass != null),
        label = label,
        useLabelAsPlaceholder = true,
        singleLine = true,
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
    )
}

@Composable
fun GlassTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val glass = LocalWyuGlassBackdrop.current
    val shape = remember { WyuSquircleShape(TextFieldDefaults.CornerRadius) }
    MiuixTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = if (glass != null) {
            modifier.textureBlur(backdrop = glass, shape = shape, blurRadius = InputBlurRadius)
        } else {
            modifier
        },
        colors = glassTextFieldColors(glass != null),
        label = label,
        useLabelAsPlaceholder = true,
        singleLine = true,
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
    )
}

@Composable
private fun glassTextFieldColors(frosted: Boolean): TextFieldColors {
    val scheme = MiuixTheme.colorScheme
    return TextFieldDefaults.textFieldColors(
        backgroundColor = if (frosted) {
            scheme.surfaceContainer.copy(alpha = 0.45f)
        } else {
            scheme.onSurface.copy(alpha = 0.055f)
        },
        labelColor = scheme.onSurfaceVariantSummary,
        borderColor = scheme.primary.copy(alpha = 0.4f),
    )
}
