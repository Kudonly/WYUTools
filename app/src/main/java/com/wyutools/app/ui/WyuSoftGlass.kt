package com.wyutools.app.ui

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.drawBackdrop

enum class WyuSoftGlassShape { Circle, RoundedRect }

internal val wyuSoftGlassSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * HyperOS 4 soft glass, ported from HyperLight 1.2.3, with uniforms resolved
 * from its "clarity" (平衡可读玻璃) preset. HyperLight samples a separate
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
  half4 hc = uRefractTex.eval(clamp(uv, float2(0.5, 0.5), uGeoPx_pos_size.zw - 0.5));
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
float4 processGlassColor(float4 glassMaterial, float2 uvPx) {
  float4 glassColor = textureBlur(uvPx);
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
  float eps = 0.75;
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
  glassMaterial = processGlassColor(glassMaterial, uvDisplayPx);
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

private const val RawShapeEdgePx = 117f
private const val RawShapeEdgePow = 3.65f
private const val RawRefractionThickness = 196f
private const val RawReflectionThickness = 0f
private const val RawReflectionLighten = -0.15f
private const val RawBgSaturation = 1.1f
private const val RawReflectionStrength = 0f
private const val RawRefractionIor = 6.1f
private const val RawBlurPercent = 20
private const val RawDownscale = 1
// HyperLight's px geometry targets a ~3x baseline; low-density panels need it
// scaled down so the glass rim keeps the same proportions in dp.
private const val ReferenceDensity = 3f

// HyperLight-style default: vivid in light mode, gentler in dark mode.
internal fun softGlassBgSaturation(dark: Boolean): Float = if (dark) RawBgSaturation else 2f

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
                setFloatUniform(
                    "uAlpha_shapeEdgeThickReflect",
                    1f,
                    edgePx * inv,
                    refractionPx * inv,
                    reflectionPx * inv,
                )
                // HyperLight "clarity" (平衡可读玻璃) preset, resolved to shader uniforms.
                setFloatUniform("uRefractIORStrengthLight_clrpow", refractionIor, reflectionStrength, reflectionLighten, 1f)
                setFloatUniform("uGlassColor", 1f, 1f, 1f, 0.0943f)
                setFloatUniform("uInClrWhiteMix_bgClrSatBri", 0.2f, 0.3f, bgSaturation, -0.1f)
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
    enabled: Boolean = true,
): Modifier {
    if (backdrop == null || !enabled || !wyuSoftGlassSupported) return this
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
