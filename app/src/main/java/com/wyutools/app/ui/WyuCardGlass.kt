package com.wyutools.app.ui

import android.graphics.BitmapShader
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.GlassStyle
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The soft-glass material, drawn by us instead of by the upstream pipeline.
 *
 * Upstream runs a chain of RenderEffects — backdrop capture, blur, refraction,
 * reflection, rim light — for every glass surface on every frame. This produces
 * the same look in a single shader pass against the page's pre-blurred
 * wallpaper, with everything that does not vary per pixel precomputed: the wide
 * colour average on the CPU, a cheap distance field away from the rim, and the
 * gamma and burn stages skipped when their exponent is 1.
 *
 * Only cards and timetable course blocks use this path; every other surface
 * keeps the upstream pipeline.
 */

/**
 * One compiled shader for every glass surface: compiling AGSL is expensive and
 * a page carries a dozen cards. Uniforms are instance state, so they are
 * reapplied immediately before each draw. Draws happen on the main thread, one
 * surface at a time.
 */
private object WyuMiGlassShaderPool {
    val shader: android.graphics.RuntimeShader by lazy {
        android.graphics.RuntimeShader(WyuMiGlassSource)
    }
}

/**
 * Draws the soft-glass material on top of [underlay], the page's pre-blurred
 * wallpaper, so the material itself never blurs: one pass of the glass program
 * samples the wallpaper and the surface is done.
 *
 * Everything the surface needs is hoisted out of the shader. The wide,
 * detail-erasing average is computed once per frame on the CPU; the distance
 * field falls back to the cheap box away from the rim; gamma and burn are
 * skipped when their exponent is 1. What is left is a couple of texture taps
 * and some grading, which keeps a full screen of glass at a few milliseconds.
 */
@Composable
internal fun Modifier.wyuCardGlass(
    underlay: WyuGlassUnderlay?,
    shape: Shape,
    cornerRadius: Dp,
    style: GlassStyle,
    tint: Color,
    /**
     * Fill the surface with [fixedBase] instead of the wallpaper under it, keeping
     * the rim, refraction and rim light. A tall, narrow surface such as the
     * timetable's period column otherwise reads the whole wallpaper top to
     * bottom and turns into a colour ramp.
     */
    fixedBase: Color? = null,
): Modifier {
    if (underlay == null || !wyuSoftGlassSupported) return this
    val density = LocalDensity.current
    val pageCoordinates = LocalWyuPageCoordinates.current
    val runtimeShader = WyuMiGlassShaderPool.shader
    val paint = remember(runtimeShader) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            shader = runtimeShader
        }
    }
    val originState = remember { mutableStateOf(Offset.Zero) }
    // Thumbnail of the pre-blurred wallpaper used to average the surface-wide
    // colour on the CPU: one probe per underlay, not per frame.
    val probe = remember(underlay) { WyuGlassColorProbe.of(underlay.bitmap) }
    val drawGlass = remember(
        runtimeShader,
        paint,
        shape,
        underlay,
        probe,
        style,
        tint,
        fixedBase,
        density,
        cornerRadius,
    ) {
        Modifier.drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = Path()
            when (outline) {
                is Outline.Generic -> path.addPath(outline.path)
                is Outline.Rounded -> path.addRoundRect(outline.roundRect)
                is Outline.Rectangle -> path.addRect(outline.rect)
            }
            val nativePath = path.asAndroidPath()
            val cornerPx = with(density) { cornerRadius.toPx() }
            // Upstream expresses every rim constant in pixels at a 3x reference
            // density and then divides by its layer downscale; at 1:1 the only
            // conversion left is the density one.
            val densityScale = density.density / GlassDefaults.SourceDensity
            val edgePx = (style.edge.width * densityScale).coerceAtLeast(1f)
            val thicknessPx = style.edge.thickness * densityScale
            val reflectPx = style.edge.reflectOffset * densityScale
            val widePx = style.blur.big * densityScale
            val overspillPx = GlassDefaults.Overspill * density.density
            val bitmap = underlay.bitmap
            // One texel: every tap clamps onto it, so the material shades a flat
            // fill as if the surface sat on a uniform backdrop.
            val flatBitmap = fixedBase?.let { color ->
                android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
                    .apply { eraseColor(color.toArgb()) }
            }
            // Wallpaper pixels per display pixel: the underlay is a downscaled
            // bake, so the sampling matrix has to undo that.
            val toBitmap = 1f / underlay.scale
            onDrawBehind {
                val local = originState.value
                // Where this surface starts inside the cached wallpaper.
                val shiftX = (local.x - underlay.origin.x) * underlay.scale
                val shiftY = (local.y - underlay.origin.y) * underlay.scale
                val width = size.width
                val height = size.height
                // Radius of the wide, detail-erasing sample the colour grading
                // reads. Upstream caps it at half the surface on each axis; with
                // it left at zero the grading falls back to the sharp refraction
                // tap and the glass keeps every wallpaper detail instead of a hue.
                val wideRadius = widePx
                val wideX = min(wideRadius, width * 0.5f)
                val wideY = min(wideRadius, height * 0.5f)
                val wideMix = (wideRadius / max(1f, max(width, height) * 0.5f))
                    .coerceIn(0f, 1f)
                // The wide disc, expressed in wallpaper pixels: centre of the
                // surface, radius capped at half its smaller side exactly like
                // the shader caps `wideX`/`wideY`.
                val wideColor = if (fixedBase != null && flatBitmap != null) {
                    floatArrayOf(fixedBase.red, fixedBase.green, fixedBase.blue, 1f)
                } else {
                    probe?.average(
                        cx = shiftX + width * underlay.scale * 0.5f,
                        cy = shiftY + height * underlay.scale * 0.5f,
                        radius = min(wideX, wideY) * underlay.scale,
                    ) ?: WyuGlassColorProbe.Unavailable
                }
                val shader = runtimeShader
                shader.setFloatUniform("in_pad", 0f, 0f)
                shader.setFloatUniform("in_maxCoord", 1.0e5f, 1.0e5f)
                shader.setFloatUniform("in_size", width, height)
                shader.setFloatUniform("in_radii", cornerPx, cornerPx, cornerPx, cornerPx)
                shader.setFloatUniform("in_smoothing", 1f)
                shader.setFloatUniform(
                    "in_alphaEdge",
                    style.inner.alpha.coerceIn(0f, 1f),
                    edgePx,
                    thicknessPx,
                    reflectPx,
                )
                shader.setFloatUniform(
                    "in_iorRefl",
                    style.refract.ior,
                    style.reflect.strength,
                    style.reflect.lighten,
                    style.inner.colorPow,
                )
                shader.setFloatUniform(
                    "in_tint",
                    tint.red,
                    tint.green,
                    tint.blue,
                    tint.alpha * style.inner.tintStrength,
                )
                shader.setFloatUniform(
                    "in_whiteMixBg",
                    style.inner.colorWhite,
                    style.inner.colorMix,
                    style.background.saturation,
                    style.background.brightness,
                )
                shader.setFloatUniform(
                    "in_darker",
                    style.blend.darkerStart,
                    style.blend.darkerEnd,
                    style.blend.darker,
                    style.inner.bottom,
                )
                shader.setFloatUniform(
                    "in_lightDir",
                    style.light.directionX,
                    style.light.directionY,
                    style.light.directionZ,
                    style.light.angleRange * Math.PI.toFloat(),
                )
                shader.setFloatUniform(
                    "in_lightAmt",
                    style.light.intensity,
                    style.light.oppositeIntensity,
                    style.blend.amount,
                    overspillPx,
                )
                shader.setFloatUniform(
                    "in_lumCurve",
                    style.blend.curveA,
                    style.blend.curveB,
                    style.blend.curveC,
                    style.blend.curveD,
                )
                shader.setFloatUniform(
                    "in_satBri",
                    style.blend.saturation,
                    style.blend.brightness,
                    style.background.burn,
                    style.background.unShade.coerceIn(0f, 1f),
                )
                shader.setFloatUniform(
                    "in_edgePow",
                    style.edge.pow.coerceAtLeast(0.01f),
                    wideX,
                    wideY,
                    wideMix,
                )
                shader.setFloatUniform(
                    "in_wideColor",
                    wideColor[0],
                    wideColor[1],
                    wideColor[2],
                    wideColor[3],
                )
                shader.setInputShader(
                    "child",
                    if (flatBitmap != null) {
                        BitmapShader(flatBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                            .apply { filterMode = BitmapShader.FILTER_MODE_LINEAR }
                    } else {
                        BitmapShader(
                            bitmap,
                            Shader.TileMode.CLAMP,
                            Shader.TileMode.CLAMP,
                        ).apply {
                            filterMode = BitmapShader.FILTER_MODE_LINEAR
                            setLocalMatrix(
                                android.graphics.Matrix().apply {
                                    // Skia reads a shader's local matrix as
                                    // texture -> draw space and inverts it before
                                    // sampling, so this maps the surface back onto
                                    // the wallpaper, not the other way round. With
                                    // the forward mapping the taps land outside the
                                    // bitmap and every card comes out a flat clamp
                                    // of its edge pixels.
                                    setScale(toBitmap, toBitmap)
                                    postTranslate(-shiftX * toBitmap, -shiftY * toBitmap)
                                },
                            )
                        }
                    },
                )
                drawIntoCanvas { it.nativeCanvas.drawPath(nativePath, paint) }
            }
        }
    }
    return this
        .onGloballyPositioned {
            originState.value = it.pageLocalPosition(pageCoordinates())
        }
        .then(drawGlass)
}

/**
 * Surface-wide average of the pre-blurred wallpaper.
 *
 * It mirrors the Vogel disc the material used to average per pixel: the disc is
 * far wider than any surface it sits on, so one mean per surface is visually
 * the same colour and costs a few dozen integer reads on a thumbnail instead of
 * 25 texture fetches for every fragment of every glass surface.
 */
private class WyuGlassColorProbe(
    private val width: Int,
    private val height: Int,
    private val pixels: IntArray,
    private val scaleX: Float,
    private val scaleY: Float,
) {
    private val out = floatArrayOf(0f, 0f, 0f, 0f)

    /** Mean colour of a disc centred at [cx],[cy] (wallpaper px) in `[0,1]` rgb. */
    fun average(cx: Float, cy: Float, radius: Float): FloatArray {
        val px = cx * scaleX
        val py = cy * scaleY
        val pr = radius * max(scaleX, scaleY)
        var r = 0f
        var g = 0f
        var b = 0f
        var taps = 0
        for (i in 0 until 25) {
            val index = i.toFloat()
            val discRadius = sqrt((index + 0.5f) * 0.04f)
            val angle = index * 2.39996323f
            val x = (px + cos(angle) * discRadius * pr).roundToInt().coerceIn(0, width - 1)
            val y = (py + sin(angle) * discRadius * pr).roundToInt().coerceIn(0, height - 1)
            val color = pixels[y * width + x]
            r += ((color shr 16) and 0xFF) / 255f
            g += ((color shr 8) and 0xFF) / 255f
            b += (color and 0xFF) / 255f
            taps++
        }
        out[0] = r / taps
        out[1] = g / taps
        out[2] = b / taps
        out[3] = 1f
        return out
    }

    companion object {
        /** Tells the shader to fall back to its own per-pixel disc. */
        val Unavailable: FloatArray = floatArrayOf(0f, 0f, 0f, 0f)

        private const val ThumbWidth = 64

        fun of(bitmap: android.graphics.Bitmap): WyuGlassColorProbe? {
            val sourceW = bitmap.width
            val sourceH = bitmap.height
            if (sourceW <= 0 || sourceH <= 0) return null
            val thumbH = (sourceH.toLong() * ThumbWidth / sourceW).toInt().coerceIn(1, 512)
            val thumb = if (sourceW == ThumbWidth && sourceH == thumbH) {
                bitmap
            } else {
                android.graphics.Bitmap.createScaledBitmap(
                    bitmap,
                    ThumbWidth,
                    thumbH,
                    true,
                )
            }
            val data = IntArray(ThumbWidth * thumbH)
            thumb.getPixels(data, 0, ThumbWidth, 0, 0, ThumbWidth, thumbH)
            if (thumb !== bitmap) thumb.recycle()
            return WyuGlassColorProbe(
                width = ThumbWidth,
                height = thumbH,
                pixels = data,
                scaleX = ThumbWidth.toFloat() / sourceW,
                scaleY = thumbH.toFloat() / sourceH,
            )
        }
    }
}

/**
 * The material shader. It is the upstream glass program with its separate
 * full-resolution rim pass folded in, so a single cached layer carries the
 * complete look: silhouette field, refraction, reflection, colour grading and
 * rim light.
 */
private val WyuMiGlassSource: String = """

uniform shader child;

uniform float2 in_pad;          // component origin inside the padded layer
uniform float2 in_maxCoord;     // sampling clamp of the padded layer
uniform float4 in_alphaEdge;    // alpha, edge width, thickness, reflect offset
uniform float4 in_iorRefl;      // ior, reflect strength, reflect lighten, colour gamma
uniform float4 in_tint;         // tint rgb, tint strength
uniform float4 in_whiteMixBg;   // colour white, colour mix, backdrop saturation, backdrop brightness
uniform float4 in_darker;       // darker start, darker end, darker, inner bottom
uniform float4 in_lightDir;     // direction xyz, angle range * pi
uniform float4 in_lightAmt;     // intensity, opposite intensity, luminance amount, overspill
uniform float4 in_lumCurve;     // cubic coefficients A, B, C, D
uniform float4 in_satBri;       // saturation, brightness, burn, unshade
uniform float4 in_edgePow;      // edge pow, wide-sample radius
uniform float4 in_wideColor;    // surface-wide average colour, a = availability

const float3 kCurveWeights = float3(0.2125, 0.7153, 0.0721);
const float3 kShadowTint = float3(0.07874, 0.02848, 0.09278);

// ------------------------------------------------- silhouette and edge profile

uniform float2 in_size;         // component size
uniform float4 in_radii;        // topLeft, topRight, bottomRight, bottomLeft
uniform float in_smoothing;     // 0 = circular corner, 1 = continuous corner

float roundedBoxSdf(float2 p, float2 b, float r) {
    float2 d = p - b + r;
    return min(max(d.x, d.y), 0.0) + length(max(d, float2(0.0))) - r;
}

float supercircleSdf(float2 off, float tile) {
    float2 q = max(float2(0.0), (float2(tile) + off) / tile);
    float hi = max(q.x, q.y);
    float ratio = (hi == 0.0) ? 0.0 : clamp(min(q.x, q.y) / hi, 0.0, 1.0);
    float fit = ((((-0.7391197 * ratio + 2.4034927) * ratio + -2.490732) * ratio + 0.4768709) * ratio + 0.47478476);
    float len = length(q);
    float distBase = (len + 1.0) - 1.0 / (1.0 - ratio * ratio * clamp(len, 0.0, 1.0) * fit);
    return min(max(tile + off.x, tile + off.y), 0.0) + tile * (distBase - 1.0);
}

float pickRadius(float2 local, float2 b) {
    float top = (local.x < b.x) ? in_radii.x : in_radii.y;
    float bottom = (local.x < b.x) ? in_radii.w : in_radii.z;
    return min((local.y < b.y) ? top : bottom, min(b.x, b.y));
}

float sdfShape(float2 p, float2 b, float r) {
    float box = roundedBoxSdf(p, b, r);
    float minHalf = min(b.x, b.y);
    if (in_smoothing <= 0.001 || r < 0.5 || (minHalf - r) <= 1.0) {
        return box;
    }
    float tile = min(1.528665 * r, minHalf);
    return mix(box, supercircleSdf(p - b, tile), in_smoothing);
}

// Cheap rounded-box field. The continuous-corner variant is only needed where
// the silhouette actually bends: deep inside, both fields agree that the point
// is far from the edge, and this one costs a fraction of the squircle maths.
float boxSdf(float2 local) {
    float2 b = in_size * 0.5;
    return roundedBoxSdf(abs(local - b), b, pickRadius(local, b));
}

// Distance to the silhouette from a point in component space. Negative inside.
float silhouetteSdf(float2 local) {
    float2 b = in_size * 0.5;
    return sdfShape(abs(local - b), b, pickRadius(local, b));
}


const float3 kLumaWeights = float3(0.2126, 0.7152, 0.0722);

float luma(float3 color) {
    return dot(color, kLumaWeights);
}

float smooth5Map(float t) {
    t = clamp(mix(0.5, 1.0, t), 0.0, 1.0);
    t = t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    return (t - 0.5) * 2.0;
}

// Depth 0 at the silhouette, 1 once the surface is flat. `p` shapes how fast it gets there.
float edgeCurve(float x, float p) {
    if (x >= 0.85) return 1.0;
    float c = smooth5Map(sqrt(clamp(x, 0.0, 1.0)));
    return 1.0 - pow(1.0 - c, p);
}

// Slope of `edgeCurve` in `x`.
//
// The rim profile is differentiated in closed form. A one-pixel step would be a sixth of the band
// on the downscaled layer this pass runs on, and a step that coarse averages the turn away: the
// tilt it reports inside the band comes out several times the real one, and the rim reads as a dome
// rather than an edge.
float edgeCurveSlope(float x, float p) {
    if (x >= 0.85) return 0.0;
    float t = sqrt(clamp(x, 0.000001, 1.0));
    float u = 0.5 + 0.5 * t;
    float um = u - 1.0;
    float c = smooth5Map(t);
    // d(smooth5Map(sqrt x))/dx, the quintic's derivative carried through the square root.
    float slope = 15.0 * u * u * um * um / t;
    return p * pow(max(1.0 - c, 0.0), p - 1.0) * slope;
}

// Direction the distance field grows in: a unit vector pointing out of the shape. The unshaped
// field is locally a plane, so a half-pixel difference reads it exactly.
float2 sdfGradient(float2 local) {
    float dx = silhouetteSdf(local + float2(0.5, 0.0)) - silhouetteSdf(local - float2(0.5, 0.0));
    float dy = silhouetteSdf(local + float2(0.0, 0.5)) - silhouetteSdf(local - float2(0.0, 0.5));
    float2 gradient = float2(dx, dy);
    float length2d = length(gradient);
    return (length2d < 0.0001) ? float2(0.0) : gradient / length2d;
}

// Lifts a colour toward white without clipping: the gain is applied around a luminance-dependent
// pivot, so bright pixels move less than dark ones.
float3 liftToward(float3 color, float amount) {
    float v = luma(color);
    float w = smoothstep(0.0, 0.5, v);
    float k = mix(1.0 - v, v, w);
    float gain = 1.0 + smoothstep(0.0, 1.0, amount) * mix(0.75, 0.4, w);
    return (color + k) * gain - k;
}

float dynamicAdd(float3 color) {
    float toWhite = smoothstep(0.2, 1.0, distance(float3(1.0), color));
    return mix(0.8, mix(0.2, 1.0, toWhite), luma(color));
}

// acos is not worth its cost here; this is the usual sqrt-based polynomial fit.
float lightFalloff(float3 normal, float3 direction, float intensity, float angleRange) {
    float dp = dot(normal, direction);
    float d = clamp(dp, -1.0, 1.0);
    float u = 1.0 - d;
    float positive = sqrt(u) * (u * (u * 0.04587603 + 0.10884186) + 1.41502593);
    float angle = (d < 0.0) ? (3.14159265 - positive) : positive;
    return max(dp, 0.0) * max(intensity * (1.0 - angle / max(angleRange, 0.0001)), 0.0);
}

// How much light the rim carries where neither directional light reaches. Calibrated so the sides
// between the two lit arcs come out about a dozen levels above the surface.
const float kEdgeAmbient = 0.24;

// How much of the rim band the light rides, measured from the silhouette inward. The band itself
// is what bends the refraction ray; the highlight drawn on it is a hairline.
const float kRimBandFraction = 0.33;

// How far the two lights lift a surface of `color`, in `[0, 1]`. The second light points the
// opposite way in x and y, so one direction lights both sides of the shape.
float rimLighten(float3 normal, float4 lightDir, float2 amounts, float3 color) {
    float3 lit = float3(normal.x, -normal.y, normal.z);
    float primary = lightFalloff(lit, lightDir.xyz, amounts.x, lightDir.w);
    float opposite = lightFalloff(lit, lightDir.xyz * float3(-1.0, -1.0, 1.0), amounts.y, lightDir.w);
    // Two opposed lights alone leave two lit arcs and two dark ones. An ambient term proportional
    // to how far the rim has turned away from the viewer joins them up.
    float edgeFactor = clamp(length(lit.xy), 0.0, 1.0);
    float raw = clamp(primary + opposite + kEdgeAmbient * edgeFactor, 0.0, 1.0) * dynamicAdd(color);
    raw = pow(clamp(raw, 0.0, 1.0), 0.85);
    // A soft knee, tighter over a bright backdrop: full light lifts the rim about half way, never
    // to white. Without it the whole edge band clips and the hairline has nothing left to sit on.
    float knee = mix(1.0, 0.7, smoothstep(0.0, 0.5, luma(color)));
    return raw / (raw + knee);
}

// The steepest the bevel is allowed to get, as a rise over a run.
//
// The closed-form slope runs to the thousands at the silhouette, which stands the normal on end and
// hands both lights everything they can take. The cap keeps the highlight a hairline.
const float kMaxBevelSlope = 1.7;

// ------------------------------------------------------------------ sampling

float4 tapLayer(float2 local) {
    return float4(child.eval(clamp(local + in_pad, float2(0.5), in_maxCoord)));
}

// The rays reach past the silhouette, and the recorded layer only carries backdrop as far as the
// blur padding. A tap beyond it returns transparent black, which would ring the shape in a dark
// halo, so fall back to the nearest tap inside the surface.
float4 sampleBackdrop(float2 local) {
    float4 col = tapLayer(local);
    if (col.a < 0.02) {
        col = tapLayer(clamp(local, float2(0.5), in_size - 0.5));
    }
    if (col.a > 0.004) {
        col = float4(col.rgb / col.a, 1.0);
    }
    float t = smoothstep(in_darker.x, in_darker.y, luma(col.rgb)) * in_darker.z;
    col.rgb = mix(col.rgb, kShadowTint, t);
    return col;
}

// Stands in for the second, far wider blur.
//
// Upstream averages a 25-tap Vogel disc per pixel, which on a card is 25 texture fetches for every
// fragment of every glass surface — by far the most expensive thing in the material. The disc's
// radius is much larger than any surface it sits on, so within one surface the result is very
// nearly a single colour: the host computes that average once per surface and hands it in as
// `in_wideColor`, which turns the whole pass into a couple of ALU ops. When the host has no
// average (alpha < 0.5) the per-pixel disc is still there as a fallback.
float4 wideColorAt(float2 local) {
    if (in_wideColor.a < 0.5) return sampleBackdrop(local);
    float t = smoothstep(in_darker.x, in_darker.y, luma(in_wideColor.rgb)) * in_darker.z;
    return float4(mix(in_wideColor.rgb, kShadowTint, t), 1.0);
}

// ------------------------------------------------------------------- grading

float4 adjustColor(float4 color, float saturation, float brightness) {
    float l = dot(color.rgb, kCurveWeights);
    return float4(mix(float3(l), color.rgb, saturation) + float3(brightness * color.a), color.a);
}

float4 luminanceCurve(float4 color) {
    float a = max(color.a, 0.0001);
    float3 straight = color.rgb / a;
    float l = clamp(dot(straight, kCurveWeights), 0.0, 1.0);
    float adjusted = ((in_lumCurve.x * l + in_lumCurve.y) * l + in_lumCurve.z) * l + in_lumCurve.w;
    adjusted = clamp(adjusted, 0.0, 1.0);
    float scale = adjusted / max(l, 0.01) * smoothstep(0.0, 0.1, l);
    return float4(mix(straight, straight * scale, in_lightAmt.z) * a, color.a);
}

float4 processColor(float4 color) {
    color = luminanceCurve(color);
    color.rgb = adjustColor(color, in_satBri.x, in_satBri.y).rgb;
    return color;
}

float4 processGlassColor(float4 material, float2 local) {
    float4 wide = adjustColor(wideColorAt(local), in_whiteMixBg.z, in_whiteMixBg.w);
    // Whitening is off by default; keep the mix only when it actually moves the colour.
    if (in_whiteMixBg.x > 0.0001) wide.rgb = mix(wide.rgb, float3(1.0), in_whiteMixBg.x);

    float lumin = clamp(luma(wide.rgb), 0.0, 1.0);
    float burnExp = max(in_satBri.z, 0.5);
    float burn = lumin - 0.5;
    // Same story as the gamma: the usual exponent is 1, where `pow` is identity.
    if (abs(burnExp - 1.0) > 0.0001) burn = pow(lumin, burnExp) - 0.5;
    float colorRatio = 0.8 * mix(lumin, 1.0, (1.587 * burn * burn * burn) + 0.5);

    float3 ratio = mix(float3(1.0), wide.rgb, colorRatio);
    float mean = (ratio.r + ratio.g + ratio.b) * 0.33333333;
    // Pull near-grey backdrops all the way to grey so the glass never picks up a false cast.
    ratio = mix(float3(mean), ratio, smoothstep(0.0, 0.4, distance(wide.rgb, float3(mean))));
    ratio = mix(ratio, in_tint.rgb, in_tint.a);

    material.rgb *= ratio;
    material.rgb = mix(material.rgb, ratio, in_whiteMixBg.y * colorRatio);
    return material;
}

float3 addLight(float3 color, float3 lightColor, float strength) {
    float toWhite = smoothstep(0.2, 1.0, distance(float3(1.0), color));
    return color + lightColor * strength * mix(0.3, 1.0, toWhite);
}

// ---------------------------------------------------------------------- main

// The material at one point, given how deep into the rim it is and which way the rim faces.
//
// `d` is passed in rather than measured: the caller walks it across a pixel, and over that distance
// the distance field is a plane, so stepping the value is exact and free.
float4 shadeMaterial(float2 local, float d, float2 gradient, float edge) {
    float depth = clamp(-d / edge, 0.0, 1.0);
    float shaped = edgeCurve(depth, in_edgePow.x);
    // The reflection and the added light ride the same outer third of the band the rim light does.
    // Spread across the whole band they come out as a soft halo several times wider than the edge
    // the edge, because this pass cannot resolve where the turn actually is.
    float nmlZ = edgeCurve(min(depth / kRimBandFraction, 1.0), 1.0);

    bool isFlat = shaped >= 1.0;
    float3 normal = isFlat
        ? float3(0.0, 0.0, 1.0)
        : normalize(float3(gradient * edgeCurveSlope(depth, in_edgePow.x), 1.0));

    float2 refractUv = local;
    if (!isFlat) {
        float3 bent = refract(float3(0.0, 0.0, -1.0), normal, 1.0 / max(in_iorRefl.x, 1.0));
        float thickness = in_alphaEdge.z;
        refractUv += bent.xy * mix((thickness - edge) * 2.0, thickness * 2.0, shaped);
    }
    float4 material = sampleBackdrop(refractUv);

    float rimZ = 1.0 - nmlZ;
    float4 mirrored = float4(0.0);
    if (rimZ > 0.000001) {
        float3 bounced = reflect(float3(0.0, 0.0, -1.0), normal);
        mirrored = sampleBackdrop(local + bounced.xy * (in_alphaEdge.w * (1.0 - shaped)));
        material = mix(material, mirrored, clamp(rimZ * in_iorRefl.y, 0.0, 1.0));
    }
    // A flat interior adds no light: the strength is `1 - shaped`, which is zero there, and the
    // falloff inside `addLight` would cost a length and a smoothstep on every such pixel.
    if (shaped < 0.9999) {
        material.rgb = addLight(material.rgb, mirrored.rgb, (1.0 - shaped) * in_iorRefl.z);
    }

    material = processColor(material);
    material = processGlassColor(material, local);

    // Additive glow along the bottom edge.
    float bottom = smoothstep(1.0, 0.0, 1.0 - local.y / max(in_size.y, 1.0));
    material.rgb += bottom * bottom * in_darker.w;


    // Rim highlight, inlined from the separate full-resolution pass so one
    // cached layer carries the whole material. Same profile and lights.
    float rimEdge = max(in_alphaEdge.y * kRimBandFraction, 1.0);
    if (d <= 0.5 && -d < rimEdge * 0.85 && length(gradient) > 0.5) {
        float rimSlope = min(edgeCurveSlope(depth, in_edgePow.x), kMaxBevelSlope);
        float3 rimNormal = normalize(float3(gradient * rimSlope, 1.0));
        float rimAmount = rimLighten(rimNormal, in_lightDir, in_lightAmt.xy, in_tint.rgb);
        float3 rimAdded = max(liftToward(in_tint.rgb, rimAmount) - in_tint.rgb, float3(0.0));
        material.rgb += rimAdded * (in_alphaEdge.x * clamp(0.5 - d, 0.0, 1.0));
    }
    // Final gamma. The default is 1.0, where this is a no-op that still costs four `pow` calls per
    // pixel — a large share of the material's arithmetic — so it is skipped unless it does work.
    if (abs(in_iorRefl.w - 1.0) > 0.0001) {
        material = pow(max(material, float4(0.0)), float4(in_iorRefl.w));
    }
    material.rgb = mix(clamp(material.rgb, 0.0, 1.0), in_tint.rgb, in_satBri.w);
    return material;
}

// ---------------------------------------------------------------------- main

half4 main(float2 coord) {
    float2 local = coord - in_pad;
    float edge = max(in_alphaEdge.y, 1.0);
    // Both fields agree that a point well inside the shape is far from the edge; take the cheap
    // one there and pay for the continuous-corner field only near the silhouette.
    float boxD = boxSdf(local);
    float d = boxD;
    if (boxD > -edge * 3.0) d = silhouetteSdf(local);
    // Outside the silhouette there is nothing to draw. The shape is masked here from the distance
    // field instead of by clipping the canvas: a clip is not antialiased, and a one-pixel ramp off
    // the field is both crisper and cheaper than an extra full-resolution pass.
    float cover = clamp(0.5 - d, 0.0, 1.0);
    if (cover <= 0.0) return half4(0.0);

    // A fully unshaded style is a flat tinted shape; skip the whole material.
    if ((1.0 - in_satBri.w) <= 0.000001) {
        float flatAlpha = in_alphaEdge.x;
        return half4(half3(in_tint.rgb * flatAlpha), half(flatAlpha));
    }

    // Only the band bends light. The interior is flat, and the distance field there is not used at
    // all, so four extra field evaluations per pixel are skipped for the bulk of the surface.
    float2 gradient = float2(0.0);
    if (-d < edge * 2.5) gradient = sdfGradient(local);
    float4 material;

    // Now that the normal is exact, the lit part of the rim is a few display pixels wide — less
    // than one pixel of this layer. Read once per pixel it would alias into a staircase, so inside
    // the band the material is integrated across the pixel instead. One dimension is enough: the
    // rim varies along the distance field's gradient and is constant across it.
    if (-d < edge && length(gradient) > 0.5) {
        material = shadeMaterial(local - gradient * 0.375, d - 0.375, gradient, edge) * 0.25;
        material += shadeMaterial(local, d, gradient, edge) * 0.5;
        material += shadeMaterial(local + gradient * 0.375, d + 0.375, gradient, edge) * 0.25;
    } else {
        material = shadeMaterial(local, d, gradient, edge);
    }

    float alpha = material.a * in_alphaEdge.x * cover;
    return half4(half3(material.rgb * alpha), half(alpha));
}
"""
