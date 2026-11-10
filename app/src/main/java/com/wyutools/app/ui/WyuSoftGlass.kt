package com.wyutools.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.drawBackdrop

enum class WyuSoftGlassShape { Circle, RoundedRect }

internal val wyuSoftGlassSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * HyperOS 4 soft glass, ported from HyperLight 1.2.3, with uniforms based on
 * its "balanced" (平衡柔光玻璃) preset and tuned on-device. HyperLight samples a separate
 * pre-blurred wallpaper bitmap for the diffuse base; the miuix backdrop exposes
 * a single input, so sampleBlur() samples the chained blur directly. uBaseTint
 * is mixed after the backdrop colour pipeline and before reflection/lighting,
 * so the light-mode white base covers the whole shape and its refracted rim
 * while rim highlights stay additive.
 */
private const val WyuSoftGlassShader = """
uniform float4 uGeoPx_pos_size;
uniform float4 uRot_Scale;
uniform float4 uBgScale;
uniform float4 uBgOrigin_pad;
uniform float4 uShapeInset;
uniform float4 uAlpha_shapeEdgeThickReflect;
uniform float4 uRefractIORStrengthLight_clrpow;
uniform float4 uGlassColor;
uniform float4 uInClrWhiteMix_bgClrSatBri;
uniform float4 uDarkRangeDark_hit;
uniform float4 uDirLightDir_angleRange;
uniform float4 uRefractBlurScale_invEdgePow;
uniform float4 uDirLightIntensityOpp_inBottom_lumiAmount;
uniform float4 uLuminanceValues;
uniform float4 uSatBri_burn_unshade;
uniform float uLuminanceCurveMode;
uniform float uNmlZAAThreshold;
uniform float uCornerRadius;
uniform float uShapeEdgePx;
uniform float uShapeEdgePow;
uniform float4 uBaseTint;
uniform float4 uSampleRect;
uniform shader uRefractTex;

float smooth5Map(float t) {
  t = mix(0.5, 1.0, t);
  t = clamp(t, 0.0, 1.0);
  t = t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
  return (t - 0.5) * 2.0;
}
float smooth5Vertical(float x, float k) {
  float t = pow(clamp(x, 0.0, 1.0), k);
  return smooth5Map(t);
}
float curveValue(float x, float powRatio) {
  if (x >= 0.85) {
    return 1.0;
  }
  x = clamp(x, 0.0, 1.0);
  float circle = smooth5Vertical(x, 0.5);
  return 1.0 - pow(1.0 - circle, powRatio);
}
float luma3(float3 color) {
  return dot(color, float3(0.2126, 0.7152, 0.0722));
}
float4 hsvv(float4 col, float lighten) {
  float v = luma3(col.rgb);
  float w = smoothstep(0.0, 0.5, v);
  float k = mix(1.0 - v, v, w);
  float g = 1.0 + smoothstep(0.0, 1.0, lighten) * mix(0.75, 0.4, w);
  return float4((col.rgb + k) * g - k, col.a);
}
float dynamicAdd(float3 color) {
  return mix(0.5, mix(0.5, 1.0, smoothstep(0.2, 1.0, distance(float3(1.0, 1.0, 1.0), color))), luma3(color));
}
float3 addLight(float3 color, float3 lightColor, float lightStrength) {
  return color + lightColor * lightStrength * mix(0.3, 1.0, smoothstep(0.2, 1.0, distance(float3(1.0, 1.0, 1.0), color)));
}
float4 sampleSharp(float2 uv) {
  half4 hc = uRefractTex.eval(clamp(uv, uSampleRect.xy, uSampleRect.zw));
  return float4(hc.r, hc.g, hc.b, hc.a);
}
float4 sampleBlur(float2 uv) {
  return sampleSharp(uv);
}
float4 textureRefract(float2 uv) {
  uv = (uv - uGeoPx_pos_size.xy) * uRot_Scale.zw;
  float4 col = sampleSharp(uv);
  col.rgb = mix(col.rgb, float3(0.07874, 0.02848, 0.09278), mix(0.0, uDarkRangeDark_hit.z, smoothstep(uDarkRangeDark_hit.x, uDarkRangeDark_hit.y, luma3(col.rgb))));
  return col;
}
float4 textureBlur(float2 uv) {
  uv = (uv - uBgOrigin_pad.xy) * uBgScale.xy;
  float4 col = sampleBlur(uv);
  col.rgb = mix(col.rgb, float3(0.07874, 0.02848, 0.09278), mix(0.0, uDarkRangeDark_hit.z, smoothstep(uDarkRangeDark_hit.x, uDarkRangeDark_hit.y, luma3(col.rgb))));
  return col;
}
float2 uvToRefractLens(float2 uvPx, float normalizeSdf, float3 normal, float edge, float thickness) {
  float refractiveIndex = mix(uRefractIORStrengthLight_clrpow.x, uRefractIORStrengthLight_clrpow.x + 0.2, uDarkRangeDark_hit.w);
  float3 refractDir = refract(float3(0.0, 0.0, -1.0), normal, 1.0 / refractiveIndex);
  float refractThickness = mix((thickness - edge) * 2.0, thickness * 2.0, normalizeSdf);
  return refractDir.xy * refractThickness + uvPx;
}
float4 calculateRefraction(float2 uvPx, float normalizeSdf, float3 normal, float edge, float thickness) {
  return textureRefract((1.0 - normalizeSdf) <= 0.0 ? uvPx : uvToRefractLens(uvPx, normalizeSdf, normal, edge, thickness));
}
float4 calculateReflection(float2 uvPx, float normalizeSdf, float3 normal, float thickness) {
  float3 refractDir = reflect(float3(0.0, 0.0, -1.0), normal);
  return textureRefract(uvPx + refractDir.xy * mix(0.0, thickness, 1.0 - normalizeSdf));
}
float calculateLighting(float2 uvPx, float3 normal, float3 lightDirection, float intensity) {
  float dotProduct = dot(normal, lightDirection);
  float d = clamp(dotProduct, -1.0, 1.0);
  float u = 1.0 - d;
  float su = sqrt(u);
  float acosPos = su * (u * (u * 0.04587603 + 0.10884186) + 1.41502593);
  float acosVal = (d < 0.0) ? (3.14159265 - acosPos) : acosPos;
  float angleFactor = 1.0 - (acosVal / uDirLightDir_angleRange.w);
  return max(dotProduct, 0.0) * max(intensity * angleFactor, 0.0);
}
float4 luminanceCurve(float4 color, float4 factorAdjust, float mixFactor) {
  float3 scaledRgb = color.rgb / max(color.a, 0.00001);
  float luminance = clamp(dot(scaledRgb, float3(0.2125, 0.7153, 0.0721)), 0.0, 1.0);
  float adj = ((factorAdjust.r * luminance + factorAdjust.g) * luminance + factorAdjust.b) * luminance + factorAdjust.a;
  adj = clamp(adj, 0.0, 1.0);
  float3 mixed;
  if (uLuminanceCurveMode > 0.5) {
    float rayScale = adj / max(luminance, 0.01) * smoothstep(0.0, 0.1, luminance);
    float3 mappedRgb = scaledRgb * rayScale;
    mixed = mix(scaledRgb, mappedRgb, mixFactor);
  } else {
    mixed = mix(scaledRgb, float3(adj, adj, adj), mixFactor);
  }
  return float4(mixed * color.a, color.a);
}
float4 adjustColor(float4 color, float saturation, float brightness) {
  float luminance = dot(color.rgb, float3(0.2125, 0.7153, 0.0721));
  float3 adjustedSaturation = mix(float3(luminance, luminance, luminance), color.rgb, saturation);
  return float4(adjustedSaturation + float3(brightness, brightness, brightness) * color.a, color.a);
}
float4 processColor(float4 color, float4 luminanceValues, float luminanceAmount, float saturation, float brightness) {
  color = luminanceCurve(color, luminanceValues, luminanceAmount);
  color.rgb = adjustColor(color, saturation, brightness).rgb;
  return color;
}
float4 processGlassColor(float4 glassMaterial, float4 glassColor) {
  glassColor = adjustColor(glassColor, uInClrWhiteMix_bgClrSatBri.z, uInClrWhiteMix_bgClrSatBri.w);
  glassColor.rgb = mix(glassColor.rgb, float3(1.0, 1.0, 1.0), uInClrWhiteMix_bgClrSatBri.x);
  glassColor.rgb += float3(0.0, 0.2, 0.05) * uDarkRangeDark_hit.w;
  float lumin = clamp(luma3(glassColor.rgb), 0.0, 1.0);
  float tBurn = pow(lumin, max(uSatBri_burn_unshade.z, 0.5)) - 0.5;
  float colorRatio = 0.8 * mix(lumin, 1.0, (1.587 * tBurn * tBurn * tBurn) + 0.5);
  float3 glassColorRatio = mix(float3(1.0, 1.0, 1.0), glassColor.rgb, colorRatio);
  float meanGlassColorRatio = (glassColorRatio.r + glassColorRatio.g + glassColorRatio.b) * 0.33333333;
  float grayness = distance(glassColor.rgb, float3(meanGlassColorRatio, meanGlassColorRatio, meanGlassColorRatio));
  glassColorRatio = mix(float3(meanGlassColorRatio, meanGlassColorRatio, meanGlassColorRatio), glassColorRatio, smoothstep(0.0, 0.4, grayness));
  glassColorRatio.rgb = mix(glassColorRatio.rgb, uGlassColor.rgb, uGlassColor.a);
  glassMaterial.rgb *= glassColorRatio;
  glassMaterial.rgb = mix(glassMaterial.rgb, glassColorRatio, uInClrWhiteMix_bgClrSatBri.y * colorRatio);
  return glassMaterial;
}
float4 calculateInnerEdgeEffects(float4 glassMaterial, float2 vUv) {
  float tEdge = smoothstep(0.0, 1.0, vUv.y);
  glassMaterial.rgb += float3(tEdge * tEdge, tEdge * tEdge, tEdge * tEdge) * uDirLightIntensityOpp_inBottom_lumiAmount.z;
  return glassMaterial;
}
float4 processLighting(float4 color, float2 uvDisplayPx, float3 normal) {
  float3 lightNormal = float3(normal.x, -normal.y, normal.z);
  float lighting1 = calculateLighting(uvDisplayPx, lightNormal, uDirLightDir_angleRange.xyz, uDirLightIntensityOpp_inBottom_lumiAmount.x);
  float lighting2 = calculateLighting(uvDisplayPx, lightNormal, uDirLightDir_angleRange.xyz * float3(-1.0, -1.0, 1.0), uDirLightIntensityOpp_inBottom_lumiAmount.y);
  float lightRatio = dynamicAdd(color.rgb);
  float lightenParam = (lighting1 + lighting2) * 2.0 * lightRatio;
  return hsvv(color, lightenParam);
}
float cornerSdf(float2 c) {
  float2 q = max(float2(uCornerRadius, uCornerRadius) - c, float2(0.0, 0.0));
  return length(q) - uCornerRadius;
}
float shapedSdf(float2 c) {
  float d = cornerSdf(c);
  float e = max(uShapeEdgePx, 0.0001);
  if (d < -e) {
    return -e;
  }
  if (d < 0.0) {
    float per = -d / e;
    per = curveValue(per, max(uShapeEdgePow, 0.05));
    return -per * e;
  }
  return d;
}
float sampleCornerSdf(float2 p) {
  return shapedSdf(min(p, float2(uCornerRadius, uCornerRadius)));
}
float3 sdfNormal(float2 c) {
  // Wide stencil so the corner normal stays smooth at sub-full-res sampling;
  // 0.75 made the upscaled rim highlight zigzag along the corners.
  float eps = 1.5;
  float right = sampleCornerSdf(c + float2(eps, 0.0));
  float left = sampleCornerSdf(c - float2(eps, 0.0));
  float top = sampleCornerSdf(c + float2(0.0, eps));
  float bottom = sampleCornerSdf(c - float2(0.0, eps));
  float2 grad = float2((right - left) / (2.0 * eps), (top - bottom) / (2.0 * eps));
  return normalize(float3(grad, 1.0));
}
float4 renderGlassShape(float4 nmlData, float2 uvPx, float2 centerPx, float2 sizePx, float2 vUv) {
  float cornerMask = nmlData.z;
  float oneMinusW = clamp(1.0 - nmlData.w, 0.0, 1.0);
  float nmlZ = 1.0 - pow(oneMinusW, uRefractBlurScale_invEdgePow.y);
  float finalAlpha = smoothstep(0.0, uNmlZAAThreshold, nmlZ);
  if ((1.0 - uSatBri_burn_unshade.w) <= 0.000001) {
    return float4(uGlassColor.rgb, uAlpha_shapeEdgeThickReflect.x * cornerMask * finalAlpha);
  }
  float oneMinusZ = 1.0 - nmlZ;
  bool isInnerBoxW = oneMinusW <= 0.000001;
  float2 nlmXY = nmlData.xy * 2.0 - 1.0;
  float2 flipSign = 1.0 - 2.0 * step(sizePx * 0.5, uvPx);
  nlmXY *= flipSign;
  float3 normal = isInnerBoxW ? float3(0.0, 0.0, 1.0) : float3(nlmXY, sqrt(max(0.0, 1.0 - dot(nlmXY, nlmXY))));
  float2 offset = uvPx - centerPx;
  float cosAngle = uRot_Scale.x;
  float sinAngle = uRot_Scale.y;
  float2 uvDisplayPx = centerPx + float2(offset.x * cosAngle - offset.y * sinAngle, offset.x * sinAngle + offset.y * cosAngle) * uRefractBlurScale_invEdgePow.x;
  float4 refractionResult = calculateRefraction(uvDisplayPx, nmlData.w, normal, uAlpha_shapeEdgeThickReflect.y, uAlpha_shapeEdgeThickReflect.z);
  float4 reflectionResult = oneMinusZ <= 0.000001 ? float4(0.0, 0.0, 0.0, 0.0) : calculateReflection(uvDisplayPx, nmlData.w, normal, uAlpha_shapeEdgeThickReflect.w);
  float4 glassMaterial = mix(refractionResult, reflectionResult, oneMinusZ * uRefractIORStrengthLight_clrpow.y);
  glassMaterial = processColor(glassMaterial, uLuminanceValues, uDirLightIntensityOpp_inBottom_lumiAmount.w, uSatBri_burn_unshade.x, uSatBri_burn_unshade.y);
  // Interior pixels already sampled this exact spot for the refraction
  // result (identity transform, same dark mix), so reuse that fetch instead
  // of reading the wallpaper bitmap a second time. Only the rim needs the
  // extra sample at its own uv.
  float4 baseSample = isInnerBoxW ? refractionResult : textureBlur(uvDisplayPx);
  glassMaterial = processGlassColor(glassMaterial, baseSample);
  glassMaterial.rgb = mix(glassMaterial.rgb, uBaseTint.rgb, uBaseTint.a);
  glassMaterial.rgb = addLight(glassMaterial.rgb, reflectionResult.rgb, oneMinusW * uRefractIORStrengthLight_clrpow.z);
  glassMaterial = calculateInnerEdgeEffects(glassMaterial, vUv);
  glassMaterial = isInnerBoxW ? glassMaterial : processLighting(glassMaterial, uvDisplayPx, normal);
  glassMaterial = pow(glassMaterial, float4(uRefractIORStrengthLight_clrpow.w, uRefractIORStrengthLight_clrpow.w, uRefractIORStrengthLight_clrpow.w, uRefractIORStrengthLight_clrpow.w));
  glassMaterial.rgb = mix(clamp(glassMaterial.rgb, 0.0, 1.0), uGlassColor.rgb, uSatBri_burn_unshade.w);
  glassMaterial.a *= uAlpha_shapeEdgeThickReflect.x * cornerMask * finalAlpha;
  return glassMaterial;
}
half4 main(float2 xy) {
  float2 size = uGeoPx_pos_size.zw;
  float2 shapePos = xy - uShapeInset.xy;
  float2 shapeSize = size - uShapeInset.xy * 2.0;
  float2 cornerPos = min(shapePos, shapeSize - shapePos);
  float2 c = min(cornerPos, float2(uCornerRadius, uCornerRadius));
  float d = shapedSdf(c);
  float e = max(uShapeEdgePx, 0.0001);
  float3 n = d <= -e ? float3(0.0, 0.0, 1.0) : sdfNormal(cornerPos);
  float2 fade = float2(1.0, 1.0) - smoothstep(float2(uCornerRadius - 3.0, uCornerRadius - 3.0), float2(uCornerRadius - 1.0, uCornerRadius - 1.0), cornerPos);
  n.xy *= fade;
  float4 nmlData = float4(n.xy * 0.5 + 0.5, 1.0, -d / e);
  float2 screenUV = shapePos / shapeSize;
  float2 centerPx = size * 0.5;
  float4 color = (nmlData.w <= -2.0 / e) ? float4(0.0, 0.0, 0.0, 0.0) : renderGlassShape(nmlData, xy, centerPx, size, screenUV);
  return half4(half(color.r * color.a), half(color.g * color.a), half(color.b * color.a), half(color.a));
}
"""

// Interior pixels of the static glass sample a colour-baked underlay: the
// luminance curve, saturation/brightness and glass colour pipeline only
// depend on the sampled wallpaper colour, so baking that mapping into the
// bitmap leaves one texture fetch and a tint/gradient add per pixel. The rim
// keeps the full material on the raw texture.
private const val WyuStaticGlassInterior = """
  if (uUseBaked > 0.5 && nmlData.w >= 1.0) {
    float2 bakedUv = (xy - uBgOrigin_pad.xy) * uBgScale.xy;
    half4 bakedHalf = uGlassTex.eval(clamp(bakedUv, uSampleRect.xy, uSampleRect.zw));
    float4 baked = float4(bakedHalf.r, bakedHalf.g, bakedHalf.b, bakedHalf.a);
    baked.rgb = mix(baked.rgb, uBaseTint.rgb, uBaseTint.a);
    float tEdge = smoothstep(0.0, 1.0, screenUV.y);
    baked.rgb += float3(tEdge * tEdge, tEdge * tEdge, tEdge * tEdge) * uDirLightIntensityOpp_inBottom_lumiAmount.z;
    baked.rgb = clamp(baked.rgb, 0.0, 1.0);
    return half4(half(baked.r * baked.a), half(baked.g * baked.a), half(baked.b * baked.a), half(baked.a));
  }
"""

// Static-path variant of [WyuSoftGlassShader], assembled by text injection so
// the material stays single-sourced; the checks fail fast if the anchors
// drift. Only the static wallpaper glass uses this variant.
private val WyuStaticGlassShader: String by lazy {
    val inputAnchor = "uniform shader uRefractTex;"
    val branchAnchor = "  float2 screenUV = shapePos / shapeSize;"
    check(WyuSoftGlassShader.contains(inputAnchor)) { "uRefractTex anchor missing" }
    check(WyuSoftGlassShader.contains(branchAnchor)) { "screenUV anchor missing" }
    WyuSoftGlassShader
        .replace(inputAnchor, "$inputAnchor\nuniform shader uGlassTex;\nuniform float uUseBaked;")
        .replace(branchAnchor, branchAnchor + WyuStaticGlassInterior)
}

private const val RawShapeEdgePx = 128f
private const val RawShapeEdgePow = 3f
private const val RawRefractionThickness = 124f
private const val RawReflectionThickness = 800f
private const val RawReflectionLighten = 0.3f
private const val RawBgSaturation = 1.1f
private const val RawBgSaturationLight = 2f
private const val RawReflectionStrength = 0f
private const val RawRefractionIor = 4f
private const val RawBlurPercent = 15
private const val RawCardTintAlpha = 0.68f
// Dark cards get no tint by default (the old shader used a transparent base in
// dark mode); the slider raises a smoky black wash from zero.
private const val RawCardTintAlphaDark = 0f
// Half-resolution sampling for the live backdrop. The shader uniforms are
// scaled by the factor, so this only trades backdrop detail for GPU time.
private const val RawDownscale = 2
// The baked wallpaper underlay samples at 1/3 of the screen; the shader upscales
// with linear filtering and the baked blur hides the coarser lattice.
private const val UnderlayDownscale = 3
// HyperLight's px geometry targets a ~3x baseline; low-density panels need it
// scaled down so the glass rim keeps the same proportions in dp.
private const val ReferenceDensity = 3f

// Static wallpaper glass renders at 3/4 resolution into a GraphicsLayer and
// upscales; fragment cost drops ~44% and the blurred rim hides the softening.
private const val GlassDrawScale = 0.75f

/**
 * Runtime overrides for the soft-glass uniforms. Defaults mirror the shipped
 * material; the settings screen exposes them temporarily for tuning.
 * Saturation is split per mode (vivid in light, gentler in dark).
 */
data class WyuSoftGlassTuning(
    val refractionIor: Float = RawRefractionIor,
    val saturationLight: Float = RawBgSaturationLight,
    val saturationDark: Float = RawBgSaturation,
    val edgeThickness: Float = RawRefractionThickness,
    val shapeEdgePx: Float = RawShapeEdgePx,
    val shapeEdgePow: Float = RawShapeEdgePow,
    val reflectOffset: Float = RawReflectionThickness,
    val reflectionStrength: Float = RawReflectionStrength,
    val reflectLighten: Float = RawReflectionLighten,
    val cardTintAlphaLight: Float = RawCardTintAlpha,
    val cardTintAlphaDark: Float = RawCardTintAlphaDark,
) {
    fun saturation(dark: Boolean): Float = if (dark) saturationDark else saturationLight
    fun cardTintAlpha(dark: Boolean): Float = if (dark) cardTintAlphaDark else cardTintAlphaLight
}

private class WyuSoftGlassEffects(
    private val glassShape: WyuSoftGlassShape,
    private val cornerRadiusDp: Float,
    private val blurScale: Float,
    private val blurPercent: Int,
    private val baseTint: Color,
    private val shapeEdgePx: Float,
    private val shapeEdgePow: Float,
    private val refractionThicknessPx: Float,
    private val reflectionThicknessPx: Float,
    private val reflectionLighten: Float,
    private val bgSaturation: Float,
    private val reflectionStrength: Float,
    private val refractionIor: Float,
    private val downscale: Int,
) : (BackdropEffectScope) -> Unit {
    private var cached: RenderEffect? = null
    private var cachedW = -1f
    private var cachedH = -1f
    private var cachedPad = -1f
    private var cachedCorner = -1f
    private var cachedDensity = -1f

    override fun invoke(scope: BackdropEffectScope) {
        val density = scope.density
        val w = scope.size.width
        val h = scope.size.height
        if (!(w > 0f) || !(h > 0f) || !(density > 0f)) {
            scope.padding = 0f
            scope.renderEffect = null
            scope.downscaleFactor = 1
            scope.noiseCoefficient = 0f
            return
        }
        val pad = min(min(w, h), 128f).roundToInt().toFloat().coerceAtLeast(1f)
        // miuix-blur only implements 1/2/4/8/16; anything else throws inside
        // its draw pass, so snap arbitrary values onto a supported step.
        val factor = when {
            downscale <= 1 -> 1
            downscale <= 2 -> 2
            downscale <= 4 -> 4
            downscale <= 8 -> 8
            else -> 16
        }
        scope.padding = pad
        scope.downscaleFactor = factor
        scope.noiseCoefficient = 0f
        val densityScale = (density / ReferenceDensity).coerceIn(0.5f, 1f)
        val cornerPx = if (glassShape == WyuSoftGlassShape.Circle) {
            min(w, h) * 0.5f
        } else {
            (cornerRadiusDp * density).coerceAtLeast(1f)
        }
        if (cached != null && cachedW == w && cachedH == h && cachedPad == pad &&
            cachedCorner == cornerPx && cachedDensity == density
        ) {
            scope.renderEffect = cached
            return
        }
        cached = if (!wyuSoftGlassSupported) {
            null
        } else {
            val inv = 1f / factor
            val layerW = (w + pad * 2f) * inv
            val layerH = (h + pad * 2f) * inv
            val edgePx = (shapeEdgePx * densityScale).coerceAtLeast(0.5f)
            val edgePow = shapeEdgePow.coerceAtLeast(0.05f)
            val refractionPx = (refractionThicknessPx * densityScale).coerceAtLeast(0f)
            val reflectionPx = (reflectionThicknessPx * densityScale).coerceAtLeast(0f)
            val shader = android.graphics.RuntimeShader(WyuSoftGlassShader).apply {
                setFloatUniform("uGeoPx_pos_size", 0f, 0f, layerW, layerH)
                setFloatUniform("uRot_Scale", 1f, 0f, 1f, 1f)
                setFloatUniform("uBgScale", 1f, 1f, 1f, 1f)
                setFloatUniform("uBgOrigin_pad", 0f, 0f, 0f, 0f)
                setFloatUniform("uShapeInset", pad * inv, pad * inv, 0f, 0f)
                setFloatUniform("uSampleRect", 0.5f, 0.5f, layerW - 0.5f, layerH - 0.5f)
                setFloatUniform(
                    "uAlpha_shapeEdgeThickReflect",
                    1f,
                    edgePx * inv,
                    refractionPx * inv,
                    reflectionPx * inv,
                )
                // HyperLight "balanced" (平衡柔光玻璃) preset, tuned on-device.
                setFloatUniform("uRefractIORStrengthLight_clrpow", refractionIor, reflectionStrength, reflectionLighten, 1f)
                setFloatUniform("uGlassColor", 1f, 1f, 1f, 0.0943f)
                setFloatUniform("uInClrWhiteMix_bgClrSatBri", 0.2f, 0.3f, bgSaturation, 0.04f)
                setFloatUniform("uDarkRangeDark_hit", 0.6f, 1f, 0f, 0f)
                setFloatUniform("uDirLightDir_angleRange", -0.4f, 0.6f, -0.8f, 0.8f * Math.PI.toFloat())
                setFloatUniform("uRefractBlurScale_invEdgePow", 1f, 1f / edgePow, 0f, 0f)
                setFloatUniform("uDirLightIntensityOpp_inBottom_lumiAmount", 1.54f, 0.7f, 0.03f, 0.216f)
                setFloatUniform("uLuminanceValues", 0.67f, 0.16f, 0.09f, 0f)
                setFloatUniform("uSatBri_burn_unshade", 1.68f, -0.1f, 0f, 0f)
                setFloatUniform("uLuminanceCurveMode", 1f)
                setFloatUniform("uNmlZAAThreshold", nmlZThreshold(edgePx))
                setFloatUniform("uCornerRadius", cornerPx * inv)
                setFloatUniform("uShapeEdgePx", edgePx * inv)
                setFloatUniform("uShapeEdgePow", edgePow)
                setFloatUniform("uBaseTint", baseTint.red, baseTint.green, baseTint.blue, baseTint.alpha)
            }
            val glass = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "uRefractTex")
            val blurRadius = preBlurSigma(density, blurPercent) * blurScale * inv
            val blur = android.graphics.RenderEffect.createBlurEffect(
                blurRadius,
                blurRadius,
                android.graphics.Shader.TileMode.CLAMP,
            )
            android.graphics.RenderEffect.createChainEffect(glass, blur).asComposeRenderEffect()
        }
        cachedW = w
        cachedH = h
        cachedPad = pad
        cachedCorner = cornerPx
        cachedDensity = density
        scope.renderEffect = cached
    }
}

private fun nmlZThreshold(edgePx: Float): Float {
    val t = (Math.pow(min(3f / maxOf(edgePx, 1e-4f), 1f).toDouble(), 0.5) * 0.5 + 0.5)
        .toFloat()
        .coerceIn(0f, 1f)
    return ((((t * 6f - 15f) * t + 10f) * (t * t * t)) - 0.5f) * 2f
}

private fun preBlurSigma(density: Float, percent: Int): Float {
    val base = (density * 16f * percent.coerceIn(0, 100) / 100f).roundToInt().toFloat() + 1f
    return (((sqrt((base * base - 1f) / 6f) / 0.5f) - 0.5f) / 0.57735f).coerceAtLeast(0.1f)
}

@Composable
fun Modifier.wyuSoftGlass(
    backdrop: Backdrop?,
    shape: Shape,
    glassShape: WyuSoftGlassShape,
    cornerRadius: Dp = 0.dp,
    blurScale: Float = 1f,
    blurPercent: Int = RawBlurPercent,
    downscale: Int = RawDownscale,
    baseTint: Color = Color.Transparent,
    shapeEdgePx: Float = RawShapeEdgePx,
    shapeEdgePow: Float = RawShapeEdgePow,
    refractionThicknessPx: Float = RawRefractionThickness,
    reflectionThicknessPx: Float = RawReflectionThickness,
    reflectionLighten: Float = RawReflectionLighten,
    bgSaturation: Float = RawBgSaturation,
    reflectionStrength: Float = RawReflectionStrength,
    refractionIor: Float = RawRefractionIor,
): Modifier {
    if (backdrop == null || !wyuSoftGlassSupported) return this
    val effects = remember(
        glassShape,
        cornerRadius,
        blurScale,
        blurPercent,
        downscale,
        baseTint,
        shapeEdgePx,
        shapeEdgePow,
        refractionThicknessPx,
        reflectionThicknessPx,
        reflectionLighten,
        bgSaturation,
        reflectionStrength,
        refractionIor,
    ) {
        WyuSoftGlassEffects(
            glassShape = glassShape,
            cornerRadiusDp = cornerRadius.value,
            blurScale = blurScale,
            blurPercent = blurPercent,
            baseTint = baseTint,
            shapeEdgePx = shapeEdgePx,
            shapeEdgePow = shapeEdgePow,
            refractionThicknessPx = refractionThicknessPx,
            reflectionThicknessPx = reflectionThicknessPx,
            reflectionLighten = reflectionLighten,
            bgSaturation = bgSaturation,
            reflectionStrength = reflectionStrength,
            refractionIor = refractionIor,
            downscale = downscale,
        )
    }
    val backdropModifier = remember(backdrop, shape, effects) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = { effects(this) },
        )
    }
    return this.then(backdropModifier)
}

/**
 * A wallpaper cover image with the dark-mode dim and a Gaussian-like blur
 * baked in, cached in screen space so static surfaces can sample it directly
 * instead of recording a backdrop every frame. [scale] converts screen
 * pixels to bitmap pixels; [blurSigmaPx] is the baked screen-space sigma.
 */
internal class WyuGlassUnderlay(
    val bitmap: Bitmap,
    val scale: Float,
    val origin: Offset,
    val source: ImageBitmap,
    val dim: Float,
    val blurSigmaPx: Float,
    val widthPx: Int,
    val heightPx: Int,
) {
    fun matches(image: ImageBitmap, dim: Float, blurSigmaPx: Float, widthPx: Int, heightPx: Int): Boolean =
        source === image && this.dim == dim && this.blurSigmaPx == blurSigmaPx &&
            this.widthPx == widthPx && this.heightPx == heightPx
}

internal val LocalWyuGlassUnderlay = compositionLocalOf<WyuGlassUnderlay?> { null }

/** Cached Gaussian-blurred wallpaper for the non-soft-glass card path. */
internal val LocalWyuGaussianUnderlay = compositionLocalOf<WyuGlassUnderlay?> { null }

/**
 * Screen-space blur sigma (px) behind the static card glass. The bottom bar
 * reuses it so both surfaces blur by the same amount at every slider value.
 */
internal fun wyuSoftGlassScreenBlurPx(blurPercent: Int): Float {
    // 0% leaves the wallpaper nearly sharp; 20% (the tuned default) keeps the
    // previous 9.2 px sigma and the top of the range still reaches 22.
    val percent = blurPercent.coerceIn(0, 100)
    return if (percent <= 20) {
        0.46f * percent
    } else {
        9.2f + 0.16f * (percent - 20)
    }
}

/**
 * miuix's textureBlur treats its radius as dp and blurs with
 * sigma = radius × density × 0.45 px; the baked underlay mirrors that.
 */
internal fun wyuGaussianSigmaPx(radiusDp: Float, density: Float): Float =
    radiusDp * density * 0.45f

private object WyuGlassUnderlayCache {
    private const val MaxEntries = 3
    private var source: ImageBitmap? = null
    private var base: Bitmap? = null
    private val entries = ArrayDeque<WyuGlassUnderlay>()

    // Page scaffolds build underlays on a background dispatcher and two
    // scaffolds coexist during transitions, so the shared cache is locked.
    @Synchronized
    fun obtain(
        image: ImageBitmap,
        dim: Float,
        blurSigmaPx: Float,
        widthPx: Int,
        heightPx: Int,
        origin: Offset,
    ): WyuGlassUnderlay? {
        if (widthPx <= 0 || heightPx <= 0) return null
        if (image !== source) {
            source = image
            base = null
            entries.clear()
        }
        // Soft glass and the Gaussian fallback use different sigmas and can
        // both be requested while pages overlap, so keep a tiny LRU instead
        // of a single slot; the cap bounds half-res bitmap memory.
        val hit = entries.firstOrNull {
            it.matches(image, dim, blurSigmaPx, widthPx, heightPx) && it.origin == origin
        }
        if (hit != null) return hit
        val baked = base ?: downscaleWallpaper(image).also { base = it }
        val built = buildUnderlay(baked, dim, blurSigmaPx, widthPx, heightPx, origin, image)
        entries.addFirst(built)
        while (entries.size > MaxEntries) entries.removeLast()
        return built
    }

    // Step-halve so a big wallpaper averages down smoothly instead of aliasing
    // when it lands in the half-resolution underlay in one bilinear pass.
    // The ceiling must stay above half the largest screen side so the base
    // keeps at least the underlay's resolution.
    private fun downscaleWallpaper(image: ImageBitmap): Bitmap {
        var current = image.asAndroidBitmap()
        var w = current.width
        var h = current.height
        while (maxOf(w, h) > 2048) {
            val nextW = (w / 2).coerceAtLeast(1)
            val nextH = (h / 2).coerceAtLeast(1)
            current = Bitmap.createScaledBitmap(current, nextW, nextH, true)
            w = nextW
            h = nextH
        }
        return current
    }

    private fun buildUnderlay(
        base: Bitmap,
        dim: Float,
        blurSigmaPx: Float,
        widthPx: Int,
        heightPx: Int,
        origin: Offset,
        source: ImageBitmap,
    ): WyuGlassUnderlay {
        // The underlay samples at 1/3 resolution; the blur is baked in at this
        // scale instead of magnifying a tiny bitmap (the old divisor of ~22
        // still showed a faint mosaic grid at high blur).
        val outW = (widthPx / UnderlayDownscale).coerceAtLeast(1)
        val outH = (heightPx / UnderlayDownscale).coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cover = maxOf(outW.toFloat() / base.width, outH.toFloat() / base.height)
        val drawW = base.width * cover
        val drawH = base.height * cover
        val left = (outW - drawW) * 0.5f
        val top = (outH - drawH) * 0.5f
        canvas.drawBitmap(
            base,
            null,
            RectF(left, top, left + drawW, top + drawH),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG),
        )
        if (dim > 0f) {
            canvas.drawRect(
                0f,
                0f,
                outW.toFloat(),
                outH.toFloat(),
                Paint().apply {
                    color = android.graphics.Color.argb(
                        (dim.coerceIn(0f, 1f) * 255f).roundToInt(),
                        0,
                        0,
                        0,
                    )
                },
            )
        }
        // Blur radius in underlay pixels; upscaling back to screen space
        // multiplies it by the downscale, so divide the screen sigma by it.
        // Running-sum boxes cost the same at any radius.
        val radius = (blurSigmaPx / UnderlayDownscale).roundToInt().coerceIn(0, 64)
        repeat(3) { boxBlur(bitmap, radius) }
        return WyuGlassUnderlay(
            bitmap = bitmap,
            scale = outW.toFloat() / widthPx,
            origin = origin,
            source = source,
            dim = dim,
            blurSigmaPx = blurSigmaPx,
            widthPx = widthPx,
            heightPx = heightPx,
        )
    }

    // Separable running-sum box blur; three passes approximate a gaussian so
    // the cached underlay reads as real blur, not a low-resolution image.
    private fun boxBlur(bitmap: Bitmap, radius: Int) {
        if (radius < 1) return
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return
        val src = IntArray(w * h)
        bitmap.getPixels(src, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        val out = IntArray(w * h)
        val span = radius * 2 + 1
        for (y in 0 until h) {
            val row = y * w
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val c = src[row + i.coerceIn(0, w - 1)]
                a += (c ushr 24) and 0xFF
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
            }
            for (x in 0 until w) {
                tmp[row + x] = ((a / span) shl 24) or ((r / span) shl 16) or
                    ((g / span) shl 8) or (b / span)
                val drop = src[row + (x - radius).coerceIn(0, w - 1)]
                val add = src[row + (x + radius + 1).coerceIn(0, w - 1)]
                a += ((add ushr 24) and 0xFF) - ((drop ushr 24) and 0xFF)
                r += ((add shr 16) and 0xFF) - ((drop shr 16) and 0xFF)
                g += ((add shr 8) and 0xFF) - ((drop shr 8) and 0xFF)
                b += (add and 0xFF) - (drop and 0xFF)
            }
        }
        for (x in 0 until w) {
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val c = tmp[i.coerceIn(0, h - 1) * w + x]
                a += (c ushr 24) and 0xFF
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
            }
            for (y in 0 until h) {
                out[y * w + x] = ((a / span) shl 24) or ((r / span) shl 16) or
                    ((g / span) shl 8) or (b / span)
                val drop = tmp[(y - radius).coerceIn(0, h - 1) * w + x]
                val add = tmp[(y + radius + 1).coerceIn(0, h - 1) * w + x]
                a += ((add ushr 24) and 0xFF) - ((drop ushr 24) and 0xFF)
                r += ((add shr 16) and 0xFF) - ((drop shr 16) and 0xFF)
                g += ((add shr 8) and 0xFF) - ((drop shr 8) and 0xFF)
                b += (add and 0xFF) - (drop and 0xFF)
            }
        }
        bitmap.setPixels(out, 0, w, 0, 0, w, h)
    }
}

private fun smoothstepF(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * Offline bake of the static glass interior colour pipeline. Kotlin mirror of
 * processColor + processGlassColor with the static uniforms inlined:
 * luminance 0.67/0.16/0.09 at 0.216, saturation/brightness 1.68/-0.1, white
 * mix 0.2, glass colour (1,1,1) at 0.0943; the dark-hit, burn and unshade
 * terms are zero on this path. Only [bgSaturation] varies, so the bake is
 * cached per underlay and saturation (light/dark).
 */
private object WyuGlassColorCache {
    private const val MaxEntries = 4

    private class Entry(
        val source: Bitmap,
        val saturation: Float,
        val baked: Bitmap,
    )

    private val entries = ArrayDeque<Entry>()

    @Synchronized
    fun obtain(underlay: WyuGlassUnderlay, saturation: Float): Bitmap {
        entries.firstOrNull { it.source === underlay.bitmap && it.saturation == saturation }
            ?.let { return it.baked }
        val baked = bake(underlay.bitmap, saturation)
        entries.addFirst(Entry(underlay.bitmap, saturation, baked))
        // Bitmaps are left to the GC on eviction; shaders may still hold them
        // for a frame or two, so recycling here would be unsafe.
        while (entries.size > MaxEntries) entries.removeLast()
        return baked
    }

    private fun bake(source: Bitmap, bgSaturation: Float): Bitmap {
        val w = source.width
        val h = source.height
        val src = IntArray(w * h)
        source.getPixels(src, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        for (i in src.indices) {
            val c = src[i]
            val a = ((c ushr 24) and 0xFF) / 255f
            val r = ((c shr 16) and 0xFF) / 255f
            val g = ((c shr 8) and 0xFF) / 255f
            val b = (c and 0xFF) / 255f
            // processColor: luminance curve, then saturation/brightness.
            val invA = 1f / max(a, 0.00001f)
            val sr = r * invA
            val sg = g * invA
            val sb = b * invA
            val lum = (0.2125f * sr + 0.7153f * sg + 0.0721f * sb).coerceIn(0f, 1f)
            val adj = (((0.67f * lum + 0.16f) * lum + 0.09f) * lum).coerceIn(0f, 1f)
            val rayScale = adj / max(lum, 0.01f) * smoothstepF(0f, 0.1f, lum)
            var mR = (sr + (sr * rayScale - sr) * 0.216f) * a
            var mG = (sg + (sg * rayScale - sg) * 0.216f) * a
            var mB = (sb + (sb * rayScale - sb) * 0.216f) * a
            val lum2 = 0.2125f * mR + 0.7153f * mG + 0.0721f * mB
            mR = lum2 + (mR - lum2) * 1.68f - 0.1f * a
            mG = lum2 + (mG - lum2) * 1.68f - 0.1f * a
            mB = lum2 + (mB - lum2) * 1.68f - 0.1f * a
            // processGlassColor on the raw sample.
            val gBase = 0.2125f * r + 0.7153f * g + 0.0721f * b
            var gR = gBase + (r - gBase) * bgSaturation + 0.04f * a
            var gG = gBase + (g - gBase) * bgSaturation + 0.04f * a
            var gB = gBase + (b - gBase) * bgSaturation + 0.04f * a
            gR += (1f - gR) * 0.2f
            gG += (1f - gG) * 0.2f
            gB += (1f - gB) * 0.2f
            val lumin = (0.2125f * gR + 0.7153f * gG + 0.0721f * gB).coerceIn(0f, 1f)
            val tBurn = sqrt(lumin) - 0.5f
            val ratio = 0.8f * (lumin + (1f - lumin) * (1.587f * tBurn * tBurn * tBurn + 0.5f))
            var rR = 1f + (gR - 1f) * ratio
            var rG = 1f + (gG - 1f) * ratio
            var rB = 1f + (gB - 1f) * ratio
            val mean = (rR + rG + rB) / 3f
            // The shader measures grayness on the glass colour, not the ratio.
            val grayness = sqrt(
                (gR - mean) * (gR - mean) + (gG - mean) * (gG - mean) +
                    (gB - mean) * (gB - mean),
            )
            val grayMix = smoothstepF(0f, 0.4f, grayness)
            rR = mean + (rR - mean) * grayMix
            rG = mean + (rG - mean) * grayMix
            rB = mean + (rB - mean) * grayMix
            rR += (1f - rR) * 0.0943f
            rG += (1f - rG) * 0.0943f
            rB += (1f - rB) * 0.0943f
            mR *= rR
            mG *= rG
            mB *= rB
            val k = 0.3f * ratio
            val oR = mR + (rR - mR) * k
            val oG = mG + (rG - mG) * k
            val oB = mB + (rB - mB) * k
            fun byte(v: Float): Int = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            out[i] = (byte(a) shl 24) or (byte(oR) shl 16) or (byte(oG) shl 8) or byte(oB)
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }
}

internal fun wyuGlassUnderlay(
    image: ImageBitmap,
    dim: Float,
    blurSigmaPx: Float,
    widthPx: Int,
    heightPx: Int,
    origin: Offset,
): WyuGlassUnderlay? =
    WyuGlassUnderlayCache.obtain(image, dim, blurSigmaPx, widthPx, heightPx, origin)

/**
 * Static variant of [wyuSoftGlass]: the same shader draws straight onto the
 * shape path and samples [underlay] (a cached, pre-blurred wallpaper) instead
 * of a live backdrop layer. One shader pass per surface, no recording, no
 * offscreen blur — cards stop costing a backdrop pipeline each frame.
 */
@Composable
internal fun Modifier.wyuWallpaperGlass(
    underlay: WyuGlassUnderlay?,
    shape: Shape,
    glassShape: WyuSoftGlassShape,
    cornerRadius: Dp = 0.dp,
    baseTint: Color = Color.Transparent,
    shapeEdgePx: Float = RawShapeEdgePx,
    shapeEdgePow: Float = RawShapeEdgePow,
    refractionThicknessPx: Float = RawRefractionThickness,
    reflectionThicknessPx: Float = RawReflectionThickness,
    reflectionLighten: Float = RawReflectionLighten,
    bgSaturation: Float = RawBgSaturation,
    reflectionStrength: Float = RawReflectionStrength,
    refractionIor: Float = RawRefractionIor,
): Modifier {
    if (underlay == null || !wyuSoftGlassSupported) return this
    val density = LocalDensity.current
    // Interior pixels get the whole colour pipeline baked into a copy of the
    // underlay. Until the background bake lands the shader runs the full
    // pipeline, which is pixel-identical bar 8-bit rounding.
    val bakedInterior = produceState<Bitmap?>(null, underlay, bgSaturation) {
        value = null
        value = withContext(Dispatchers.Default) {
            runCatching { WyuGlassColorCache.obtain(underlay, bgSaturation) }.getOrNull()
        }
    }.value
    val runtimeShader = remember(
        underlay,
        glassShape,
        cornerRadius,
        baseTint,
        shapeEdgePx,
        shapeEdgePow,
        refractionThicknessPx,
        reflectionThicknessPx,
        reflectionLighten,
        bgSaturation,
        reflectionStrength,
        refractionIor,
        density,
        bakedInterior,
    ) {
        val densityScale = (density.density / ReferenceDensity).coerceIn(0.5f, 1f)
        val edgePx = (shapeEdgePx * densityScale).coerceAtLeast(0.5f)
        val edgePow = shapeEdgePow.coerceAtLeast(0.05f)
        val refractionPx = (refractionThicknessPx * densityScale).coerceAtLeast(0f)
        val reflectionPx = (reflectionThicknessPx * densityScale).coerceAtLeast(0f)
        android.graphics.RuntimeShader(WyuStaticGlassShader).apply {
            val sampleScale = underlay.scale / GlassDrawScale
            setFloatUniform("uRot_Scale", 1f, 0f, sampleScale, sampleScale)
            setFloatUniform("uBgScale", sampleScale, sampleScale, 1f, 1f)
            setFloatUniform("uShapeInset", 0f, 0f, 0f, 0f)
            setFloatUniform(
                "uAlpha_shapeEdgeThickReflect",
                1f,
                edgePx * GlassDrawScale,
                refractionPx * GlassDrawScale,
                reflectionPx * GlassDrawScale,
            )
            setFloatUniform(
                "uRefractIORStrengthLight_clrpow",
                refractionIor,
                reflectionStrength,
                reflectionLighten,
                1f,
            )
            setFloatUniform("uGlassColor", 1f, 1f, 1f, 0.0943f)
            setFloatUniform("uInClrWhiteMix_bgClrSatBri", 0.2f, 0.3f, bgSaturation, 0.04f)
            setFloatUniform("uDarkRangeDark_hit", 0.6f, 1f, 0f, 0f)
            setFloatUniform("uDirLightDir_angleRange", -0.4f, 0.6f, -0.8f, 0.8f * Math.PI.toFloat())
            setFloatUniform("uRefractBlurScale_invEdgePow", 1f, 1f / edgePow, 0f, 0f)
            setFloatUniform("uDirLightIntensityOpp_inBottom_lumiAmount", 1.54f, 0.7f, 0.03f, 0.216f)
            setFloatUniform("uLuminanceValues", 0.67f, 0.16f, 0.09f, 0f)
            setFloatUniform("uSatBri_burn_unshade", 1.68f, -0.1f, 0f, 0f)
            setFloatUniform("uLuminanceCurveMode", 1f)
            setFloatUniform("uNmlZAAThreshold", nmlZThreshold(edgePx))
            setFloatUniform(
                "uCornerRadius",
                with(density) { cornerRadius.toPx() * GlassDrawScale }.coerceAtLeast(1f),
            )
            setFloatUniform("uShapeEdgePx", edgePx * GlassDrawScale)
            setFloatUniform("uShapeEdgePow", edgePow)
            setFloatUniform("uBaseTint", baseTint.red, baseTint.green, baseTint.blue, baseTint.alpha)
            setFloatUniform(
                "uSampleRect",
                0.5f,
                0.5f,
                underlay.bitmap.width - 0.5f,
                underlay.bitmap.height - 0.5f,
            )
            setFloatUniform("uUseBaked", if (bakedInterior != null) 1f else 0f)
            setInputShader(
                "uGlassTex",
                BitmapShader(
                    bakedInterior ?: underlay.bitmap,
                    Shader.TileMode.CLAMP,
                    Shader.TileMode.CLAMP,
                ).apply {
                    filterMode = BitmapShader.FILTER_MODE_LINEAR
                },
            )
            setInputShader(
                "uRefractTex",
                BitmapShader(underlay.bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                    // Nearest sampling turned the reduced wallpaper into mosaic
                    // blocks; the shader relies on filtered magnification for blur.
                    filterMode = BitmapShader.FILTER_MODE_LINEAR
                },
            )
        }
    }
    val paint = remember(runtimeShader) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            shader = runtimeShader
        }
    }
    val originState = remember { mutableStateOf(Offset.Zero) }
    val layer = rememberGraphicsLayer()
    val drawGlass = remember(runtimeShader, paint, shape, glassShape, underlay, layer) {
        Modifier.drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = Path()
            when (outline) {
                is Outline.Generic -> path.addPath(outline.path)
                is Outline.Rounded -> path.addRoundRect(outline.roundRect)
                is Outline.Rectangle -> path.addRect(outline.rect)
            }
            val scaledPath = android.graphics.Path(path.asAndroidPath()).apply {
                transform(android.graphics.Matrix().apply { setScale(GlassDrawScale, GlassDrawScale) })
            }
            onDrawBehind {
                // The shader samples in shape-local coordinates; shift by this
                // widget's position inside the cached wallpaper underlay. All
                // geometry runs in the reduced layer's pixel space.
                val local = originState.value
                val shiftX = (underlay.origin.x - local.x) * GlassDrawScale
                val shiftY = (underlay.origin.y - local.y) * GlassDrawScale
                val layerW = (size.width * GlassDrawScale).roundToInt().coerceAtLeast(1)
                val layerH = (size.height * GlassDrawScale).roundToInt().coerceAtLeast(1)
                layer.record(
                    density = this,
                    layoutDirection = layoutDirection,
                    size = IntSize(layerW, layerH),
                ) {
                    runtimeShader.setFloatUniform(
                        "uGeoPx_pos_size",
                        shiftX,
                        shiftY,
                        layerW.toFloat(),
                        layerH.toFloat(),
                    )
                    runtimeShader.setFloatUniform("uBgOrigin_pad", shiftX, shiftY, 0f, 0f)
                    if (glassShape == WyuSoftGlassShape.Circle) {
                        runtimeShader.setFloatUniform(
                            "uCornerRadius",
                            min(layerW, layerH) * 0.5f,
                        )
                    }
                    drawIntoCanvas { it.nativeCanvas.drawPath(scaledPath, paint) }
                }
                // Upscale the reduced layer; linear filtering keeps the blurred
                // rim smooth instead of blocky.
                scale(1f / GlassDrawScale, 1f / GlassDrawScale, pivot = Offset.Zero) {
                    drawLayer(layer)
                }
            }
        }
    }
    return this
        .onGloballyPositioned { originState.value = it.positionInRoot() }
        .then(drawGlass)
}

/**
 * Plain Gaussian-blurred wallpaper behind a surface. The blur is baked into
 * [underlay], so each frame is one bitmap-sampled path instead of miuix's
 * live textureBlur pipeline (backdrop recording plus an offscreen blur).
 */
@Composable
internal fun Modifier.wyuWallpaperBlur(
    underlay: WyuGlassUnderlay?,
    shape: Shape,
): Modifier {
    if (underlay == null) return this
    val originState = remember { mutableStateOf(Offset.Zero) }
    val paint = remember(underlay) {
        android.graphics.Paint(
            android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.DITHER_FLAG,
        ).apply {
            isAntiAlias = true
            shader = BitmapShader(underlay.bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                filterMode = BitmapShader.FILTER_MODE_LINEAR
            }
        }
    }
    val matrix = remember { android.graphics.Matrix() }
    val drawBlur = remember(underlay, shape, paint, matrix) {
        Modifier.drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = Path()
            when (outline) {
                is Outline.Generic -> path.addPath(outline.path)
                is Outline.Rounded -> path.addRoundRect(outline.roundRect)
                is Outline.Rectangle -> path.addRect(outline.rect)
            }
            val androidPath = path.asAndroidPath()
            onDrawBehind {
                // The bitmap spans the whole page; shift it so its top-left
                // lands where the page does in this widget's coordinates.
                val local = originState.value
                val shiftX = underlay.origin.x - local.x
                val shiftY = underlay.origin.y - local.y
                matrix.setScale(1f / underlay.scale, 1f / underlay.scale)
                matrix.postTranslate(shiftX, shiftY)
                (paint.shader as BitmapShader).setLocalMatrix(matrix)
                drawIntoCanvas { it.nativeCanvas.drawPath(androidPath, paint) }
            }
        }
    }
    return this
        .onGloballyPositioned { originState.value = it.positionInRoot() }
        .then(drawBlur)
}
