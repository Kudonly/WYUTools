package com.wyutools.app.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.glass
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.material.GlassStyle
import top.yukonga.miuix.kmp.material.GlassStyles

/** Cards and the dropdown popup share one style so the two materials match. */
@Composable
internal fun wyuUpstreamGlassStyle(
    tuning: WyuSoftGlassTuning,
    dark: Boolean,
    blurPercent: Int,
    baked: Boolean = false,
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
            // 半径低于 ~22px 时上游退回 1x 全分辨率采样，滑动会明显掉帧；
            // 给模糊度设一个下限，外观仍有差别但始终走降采样快路径。
            small = if (baked) {
                // 烘焙路径：模糊已经烘进底图，管线只留最小模糊以保持在
                // 降采样模糊的常规通路上，采样成本降到最低。
                4f * scale
            } else {
                (maxOf(wyuSoftGlassScreenBlurPx(blurPercent) * 4f, 22f) * scale)
                    .coerceIn(4f, 160f)
            },
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
    baked: Boolean = false,
): Modifier = glass(
    backdrop = backdrop,
    shape = GlassShape(cornerRadius),
    style = wyuUpstreamGlassStyle(tuning, dark, blurPercent, baked),
    tint = tint,
    shading = true,
)

/**
 * A [Backdrop] sampling the pre-baked wallpaper underlay ([WyuGlassUnderlay])
 * instead of a live recorded layer — the static-material counterpart of what
 * the liquid-glass theme does with its baked layer. Each frame becomes one
 * scaled bitmap blit (no layer recording, no wallpaper redraw), and the
 * pipeline blur stays at its floor because the blur is already baked in.
 *
 * The instance is stable across underlay regeneration; draw time reads the
 * current underlay from [underlayState] so wallpaper/dim/blur changes apply
 * without rebuilding the glass node.
 */
internal class WyuBitmapBackdrop(
    private val underlayState: State<WyuGlassUnderlay?>,
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
            coords.positionInRoot()
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
