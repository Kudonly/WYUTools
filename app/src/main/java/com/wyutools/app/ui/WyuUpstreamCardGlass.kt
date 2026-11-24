package com.wyutools.app.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.glass
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.material.GlassStyle
import top.yukonga.miuix.kmp.material.GlassStyles
import kotlin.math.roundToInt

/**
 * Radius, in the pipeline's source-density units, that produces [sigmaPx] on
 * screen.
 *
 * The pipeline turns a style radius into a blur sigma with
 * `radius / SourceDensity * density * 0.45`. The card path does not go through
 * it at all — it bakes the sigma straight into its underlay — so the pipeline
 * has to be handed the inverse of that conversion, or every other glass
 * surface ends up at a different frost level than the cards.
 */
private fun upstreamBlurRadius(sigmaPx: Float, density: Float): Float =
    (sigmaPx * GlassDefaults.SourceDensity / (density * 0.45f)).coerceIn(0f, 160f)

/**
 * The tint the pipeline should lay down for [tint], in the form the card shader
 * would use it.
 *
 * The shader multiplies the colour's alpha by the style's `tintStrength`; the
 * pipeline uses the alpha as given. Handing it the product keeps one wash
 * strength across surfaces that render through either path.
 */
internal fun wyuUpstreamGlassTint(tint: Color, style: GlassStyle): Color =
    tint.copy(alpha = (tint.alpha * style.inner.tintStrength).coerceIn(0f, 1f))

/** Cards and the dropdown popup share one style so the two materials match. */
@Composable
internal fun wyuUpstreamGlassStyle(
    tuning: WyuSoftGlassTuning,
    dark: Boolean,
    blurPercent: Int,
): GlassStyle {
    val density = LocalDensity.current.density.coerceAtLeast(0.1f)
    val scale = GlassDefaults.SourceDensity / density
    val t = tuning.resolvedFor(dark)
    val base = if (dark) {
        GlassStyles.CommonMediumRegularDark
    } else {
        GlassStyles.CommonMediumRegularLowLight
    }
    return GlassStyle(
        blend = base.blend.copy(
            amount = t.blendAmount,
            saturation = t.blendSaturation,
            brightness = t.blendBrightness,
        ),
        inner = base.inner.copy(
            bottom = t.innerBottom,
            colorWhite = t.innerColorWhite,
            colorMix = t.innerColorMix,
            colorPow = t.innerColorPow,
        ),
        edge = base.edge.copy(
            width = t.shapeEdgePx * scale,
            pow = t.shapeEdgePow,
            thickness = t.edgeThickness * scale,
            reflectOffset = t.reflectOffset * scale,
        ),
        reflect = base.reflect.copy(
            lighten = t.reflectLighten,
            strength = t.reflectionStrength,
        ),
        light = base.light.copy(
            intensity = t.lightIntensity,
            oppositeIntensity = t.lightOppositeIntensity,
        ),
        refract = base.refract.copy(ior = t.refractionIor),
        background = base.background.copy(
            saturation = t.saturationLight,
            brightness = t.backgroundBrightness,
        ),
        blur = base.blur.copy(
            // The card path bakes this same sigma into its underlay. Feeding the
            // pipeline the matching radius keeps cards, bars, buttons, menus and
            // dialogs at one frost level; 0% still means no blur at all.
            small = upstreamBlurRadius(wyuSoftGlassScreenBlurPx(blurPercent), density),
        ),
    )
}

@Composable
internal fun Modifier.wyuUpstreamCardGlass(
    backdrop: Backdrop,
    cornerRadius: Dp,
    tuning: WyuSoftGlassTuning,
    dark: Boolean,
    blurPercent: Int,
    tint: Color,
): Modifier {
    val style = wyuUpstreamGlassStyle(tuning, dark, blurPercent)
    return glass(
        backdrop = backdrop,
        shape = GlassShape(cornerRadius),
        style = style,
        tint = wyuUpstreamGlassTint(tint, style),
        noiseCoefficient = tuning.noiseCoefficient,
        shading = true,
    )
}

/**
 * A [Backdrop] sampling a static wallpaper copy ([WyuGlassUnderlay], unblurred
 * — the miuix pipeline applies the blur itself) instead of a live recorded
 * layer. Each frame becomes one scaled bitmap blit: no backdrop recording and
 * no wallpaper layer redraw per glass surface, and cold start renders without
 * waiting for the live layer to be ready.
 *
 * The instance is stable across underlay regeneration; draw time reads the
 * current underlay from [underlayState] so wallpaper/dim changes apply without
 * rebuilding the glass node.
 */
internal class WyuBitmapBackdrop(
    private val underlayState: State<WyuGlassUnderlay?>,
    private val pageCoordinates: () -> LayoutCoordinates?,
) : Backdrop {
    // Sampling is positioned in root coordinates, so a moved surface must be
    // redrawn even without other draw invalidation.
    override val isCoordinatesDependent: Boolean = true

    private var cachedSource: Bitmap? = null
    private var cachedImage: ImageBitmap? = null

    private fun imageFor(bitmap: Bitmap): ImageBitmap {
        val cached = cachedImage
        if (cached != null && cachedSource === bitmap) return cached
        val image = bitmap.asImageBitmap()
        cachedSource = bitmap
        cachedImage = image
        return image
    }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Float,
    ) {
        val coords = coordinates ?: return
        val underlay = underlayState.value ?: return
        // Offset of this surface relative to the baked wallpaper's top-left,
        // mirroring LayerBackdrop's layer-space alignment math.
        val node = try {
            coords.pageLocalPosition(pageCoordinates())
        } catch (_: Exception) {
            return
        }
        val offsetX = node.x - underlay.origin.x
        val offsetY = node.y - underlay.origin.y
        val image = imageFor(underlay.bitmap)
        withTransform({
            if (downscaleFactor > 1f) {
                val inv = 1f / downscaleFactor
                translate(-offsetX * inv, -offsetY * inv)
                scale(inv, inv, Offset.Zero)
            } else {
                translate(-offsetX, -offsetY)
            }
        }) {
            drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(image.width, image.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(underlay.widthPx, underlay.heightPx),
                filterQuality = FilterQuality.Low,
            )
        }
    }
}
