package com.wyutools.app.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BackdropPipeline
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.glass.glass
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.material.GlassStyle
import top.yukonga.miuix.kmp.material.GlassStyles
import kotlin.math.floor
import kotlin.math.roundToInt

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
            // 0% 必须真的是 0 模糊；下限只做数值保护，不再抬高实际半径。
            // 烘焙底图路径也用同一半径，观感与实时路径保持一致。
            small = (wyuSoftGlassScreenBlurPx(blurPercent) * 4f * scale)
                .coerceIn(0f, 160f),
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
): Modifier = glass(
    backdrop = backdrop,
    shape = GlassShape(cornerRadius),
    style = wyuUpstreamGlassStyle(tuning, dark, blurPercent),
    tint = tint,
    noiseCoefficient = tuning.noiseCoefficient,
    shading = true,
)

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


/**
 * Static soft-glass card surface. The upstream glass pipeline is configured
 * once, rendered into a cached [GraphicsLayer] and replayed as a plain bitmap
 * afterwards: no backdrop recording, no blur cascade, no glass shader on the
 * animation frame. Only a changed geometry / wallpaper / style re-bakes it.
 */
@Composable
internal fun Modifier.wyuUpstreamStaticCardGlass(
    backdrop: Backdrop,
    underlayState: State<WyuGlassUnderlay?>,
    cornerRadius: Dp,
    tuning: WyuSoftGlassTuning,
    dark: Boolean,
    blurPercent: Int,
    tint: Color,
): Modifier {
    val style = wyuUpstreamGlassStyle(tuning, dark, blurPercent)
    val shape = GlassShape(cornerRadius)
    val tintPacked = (tint.toArgb().toLong() and 0xffffffffL) shl 32
    val pipeline = remember(style, shape, tintPacked, tuning.noiseCoefficient) {
        WyuUpstreamInternal.pipeline(shape, style, tintPacked, tuning.noiseCoefficient)
    }
    val layer = rememberGraphicsLayer()
    return this.then(
        WyuStaticGlassElement(
            backdrop = backdrop,
            underlayState = underlayState,
            pipeline = pipeline,
            glassShape = shape,
            style = style,
            tint = tintPacked,
            noise = tuning.noiseCoefficient,
            layer = layer,
        )
    )
}

@Stable
private data class WyuStaticGlassKey(
    val position: Offset,
    val size: IntSize,
    val style: GlassStyle,
    val glassShape: GlassShape,
    val tint: Long,
    val noise: Float,
    val backdrop: Backdrop,
    val underlay: WyuGlassUnderlay?,
    val density: Float,
    val fontScale: Float,
    val layoutDirection: LayoutDirection,
)

private class WyuStaticGlassElement(
    val backdrop: Backdrop,
    val underlayState: State<WyuGlassUnderlay?>,
    val pipeline: BackdropPipeline,
    val glassShape: GlassShape,
    val style: GlassStyle,
    val tint: Long,
    val noise: Float,
    val layer: GraphicsLayer,
) : ModifierNodeElement<WyuStaticGlassNode>() {
    override fun create(): WyuStaticGlassNode = WyuStaticGlassNode(
        backdrop, underlayState, pipeline, glassShape, style, tint, noise, layer
    )

    override fun update(node: WyuStaticGlassNode) {
        node.backdrop = backdrop
        node.underlayState = underlayState
        node.pipeline = pipeline
        node.glassShape = glassShape
        node.style = style
        node.tint = tint
        node.noise = noise
        node.layer = layer
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "wyuStaticSoftGlass"
    }

    override fun equals(other: Any?): Boolean = other is WyuStaticGlassElement &&
        backdrop === other.backdrop && underlayState === other.underlayState &&
        pipeline === other.pipeline && glassShape == other.glassShape &&
        style == other.style && tint == other.tint && noise == other.noise &&
        layer === other.layer

    override fun hashCode(): Int = backdrop.hashCode()
}

private class WyuStaticGlassNode(
    var backdrop: Backdrop,
    var underlayState: State<WyuGlassUnderlay?>,
    var pipeline: BackdropPipeline,
    var glassShape: GlassShape,
    var style: GlassStyle,
    var tint: Long,
    var noise: Float,
    var layer: GraphicsLayer,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {
    private var coordinates by mutableStateOf<LayoutCoordinates?>(null, neverEqualPolicy())
    private var effectScope: BackdropEffectScope? = null
    private var cachedKey: WyuStaticGlassKey? = null
    private var cachedPad = 0f
    private var cachedScale = 1f

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
    }

    override fun ContentDrawScope.draw() {
        val coords = coordinates?.takeIf { it.isAttached }
        val position = if (coords != null) {
            try {
                coords.positionInRoot()
            } catch (_: Exception) {
                Offset.Zero
            }
        } else {
            Offset.Zero
        }
        val underlay = underlayState.value
        val key = WyuStaticGlassKey(
            position = position,
            size = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            style = style,
            glassShape = glassShape,
            tint = tint,
            noise = noise,
            backdrop = backdrop,
            underlay = underlay,
            density = density,
            fontScale = fontScale,
            layoutDirection = layoutDirection,
        )
        if (key != cachedKey) {
            val scope = effectScope ?: WyuUpstreamInternal.effectScope(backdrop).also { effectScope = it }
            WyuUpstreamInternal.updateScope(scope, this)
            WyuUpstreamInternal.configure(scope, pipeline)
            val pad = scope.padding
            val scale = scope.downscaleFactor.coerceAtLeast(1f)
            val recordW = ((size.width + pad * 2f) / scale).roundToInt().coerceAtLeast(1)
            val recordH = ((size.height + pad * 2f) / scale).roundToInt().coerceAtLeast(1)
            layer.renderEffect = scope.renderEffect
            layer.record(
                density = this,
                layoutDirection = layoutDirection,
                size = IntSize(recordW, recordH),
            ) {
                translate(pad / scale, pad / scale) {
                    with(backdrop) {
                        drawBackdrop(
                            density = scope,
                            coordinates = coords,
                            layerBlock = null,
                            downscaleFactor = scale,
                        )
                    }
                }
            }
            cachedKey = key
            cachedPad = pad
            cachedScale = scale
        }
        val shiftedPad = cachedPad / cachedScale
        val wholeX = floor(shiftedPad).toInt()
        val wholeY = floor(shiftedPad).toInt()
        layer.topLeft = IntOffset(-wholeX, -wholeY)
        val fracX = (shiftedPad - wholeX) * cachedScale
        val fracY = (shiftedPad - wholeY) * cachedScale
        translate(-backdrop.offsetResidualX - fracX, -backdrop.offsetResidualY - fracY) {
            scale(cachedScale, cachedScale, Offset.Zero) {
                drawLayer(layer)
            }
        }
        pipeline.drawFront(this)
        drawContent()
    }
}


/**
 * Reflective access to miuix's internal glass pipeline and backdrop effect
 * scope. The dependency ships these classes package-private, but they are the
 * only way to run the exact upstream shader; keeping them via ProGuard makes
 * this stable across release minification.
 */
private object WyuUpstreamInternal {
    private val pipelineClass: Class<*> by lazy {
        Class.forName("top.yukonga.miuix.kmp.glass.GlassPipeline")
    }
    private val nodeClass: Class<*> by lazy {
        Class.forName("top.yukonga.miuix.kmp.blur.DrawBackdropNode")
    }
    private val scopeClass: Class<*> by lazy {
        Class.forName("top.yukonga.miuix.kmp.blur.BackdropEffectScopeImpl")
    }
    private val pipelineCtor by lazy {
        pipelineClass.declaredConstructors.first { it.parameterCount == 10 }
            .apply { isAccessible = true }
    }
    private val nodeCtor by lazy {
        nodeClass.declaredConstructors.first { it.parameterCount == 12 }
            .apply { isAccessible = true }
    }
    private val scopeField by lazy {
        nodeClass.getDeclaredField("effectScope").apply { isAccessible = true }
    }
    private val updateMethod by lazy {
        scopeClass.getMethod("update", DrawScope::class.java)
    }
    private val applyMethod by lazy {
        scopeClass.getMethod("apply", Function1::class.java)
    }

    fun pipeline(
        shape: GlassShape,
        style: GlassStyle,
        tint: Long,
        noise: Float,
    ): BackdropPipeline = pipelineCtor.newInstance(
        shape,
        style,
        1f,
        tint,
        null,
        null,
        noise,
        null,
        true,
        null,
    ) as BackdropPipeline

    fun effectScope(backdrop: Backdrop): BackdropEffectScope {
        val shapeProvider: Function0<Shape> = { RectangleShape }
        val noopEffects: Function1<BackdropEffectScope, Unit> = {}
        val defaultBackdropDraw: Function2<DrawScope, Function1<DrawScope, Unit>, Unit> = { scope, draw ->
            draw(scope)
        }
        val node = nodeCtor.newInstance(
            backdrop,
            shapeProvider,
            noopEffects,
            null,
            null,
            defaultBackdropDraw,
            null,
            null,
            0,
            null,
            true,
            null,
        )
        return scopeField.get(node) as BackdropEffectScope
    }

    fun updateScope(scope: BackdropEffectScope, drawScope: DrawScope) {
        updateMethod.invoke(scope, drawScope)
    }

    fun configure(scope: BackdropEffectScope, pipeline: BackdropPipeline) {
        val configure: Function1<BackdropEffectScope, Unit> = { pipeline.configureEffects(it) }
        applyMethod.invoke(scope, configure)
    }
}
