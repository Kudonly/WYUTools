package com.wyutools.app.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

enum class WyuWallpaper(val label: String) {
    // 晨雾 is the stock look: the app ships with it, so there is no "no
    // wallpaper" entry any more — every non-custom choice is a gradient.
    Mist("晨雾"),
    Jade("青岚"),
    Dusk("晚霞"),
    Aurora("极光"),
    Custom("自定义");

    fun backgroundStops(dark: Boolean, surface: Color): List<Pair<Float, Color>>? {
        val stops = when (this) {
            Custom -> return null
            Mist -> if (dark) {
                listOf(Color(0xFF24324A), Color(0xFF1D2737), Color(0xFF181E2A))
            } else {
                listOf(Color(0xFFC9DDF4), Color(0xFFE2EDF9), Color(0xFFF4F7FC))
            }

            Jade -> if (dark) {
                listOf(Color(0xFF1E352C), Color(0xFF192822), Color(0xFF16201C))
            } else {
                listOf(Color(0xFFCBE8DB), Color(0xFFE0F1E9), Color(0xFFF0F7F3))
            }

            Dusk -> if (dark) {
                listOf(Color(0xFF3B2732), Color(0xFF2B2029), Color(0xFF221C22))
            } else {
                listOf(Color(0xFFF6D2C2), Color(0xFFF7E4DA), Color(0xFFFBF1F3))
            }

            Aurora -> if (dark) {
                listOf(Color(0xFF2F2650), Color(0xFF23203A), Color(0xFF1C1A2C))
            } else {
                listOf(Color(0xFFDCD2F6), Color(0xFFE4E8FA), Color(0xFFF2EEFB))
            }
        }
        return listOf(
            0f to stops[0],
            0.38f to stops[1],
            0.72f to stops[2],
            1f to surface,
        )
    }

    fun background(dark: Boolean, surface: Color): Brush? {
        val stops = backgroundStops(dark, surface) ?: return null
        return Brush.verticalGradient(*stops.toTypedArray())
    }
}

enum class WyuThemeMode(val label: String) {
    Dark("深色"),
    Light("浅色"),
    System("跟随系统"),
}

fun wyuHomeBackgroundStops(
    dark: Boolean,
    surface: Color,
    primary: Color,
): List<Pair<Float, Color>> {
    val tint = lerp(surface, primary, if (dark) 0.12f else 0.16f)
    return listOf(
        0f to tint,
        0.34f to surface,
        1f to surface,
    )
}

fun wyuHomeBackground(dark: Boolean, surface: Color, primary: Color): Brush {
    return Brush.verticalGradient(*wyuHomeBackgroundStops(dark, surface, primary).toTypedArray())
}

fun DrawScope.drawWyuImageCover(image: ImageBitmap, dim: Float = 0f) {
    if (image.width <= 0 || image.height <= 0 || size.width <= 0f || size.height <= 0f) return
    val scale = max(size.width / image.width, size.height / image.height)
    val width = image.width * scale
    val height = image.height * scale
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(
            ((size.width - width) / 2f).roundToInt(),
            ((size.height - height) / 2f).roundToInt(),
        ),
        dstSize = IntSize(width.roundToInt(), height.roundToInt()),
        filterQuality = FilterQuality.Medium,
    )
    if (dim > 0f) {
        drawRect(Color.Black.copy(alpha = dim.coerceAtMost(0.92f)))
    }
}

/** Slider percentage is the wallpaper's actual brightness; each mode stores its own. */
fun wyuCustomWallpaperDim(brightness: Float): Float =
    (1f - brightness.coerceIn(0.5f, 1f)).coerceIn(0f, 0.92f)

internal const val WyuSoftGlassBlurMin = 0

object WyuAppearance {
    private const val PrefsName = "appearance"
    private const val KeyWallpaper = "wallpaper"
    private const val KeyThemeMode = "theme_mode"
    // Retired single-value key; still read as the fallback for the light value.
    private const val KeyCustomBrightness = "custom_brightness"
    private const val KeyCustomBrightnessLight = "custom_brightness_light"
    private const val KeyCustomBrightnessDark = "custom_brightness_dark"
    private const val BrightnessDarkDefault = 0.6f
    private const val KeySoftGlass = "soft_glass"
    private const val KeySoftGlassBlur = "soft_glass_blur_radius"
    private const val KeySoftGlassTuningIor = "soft_glass_tuning_refraction_ior"
    private const val KeySoftGlassTuningSaturationLight = "soft_glass_tuning_saturation_light"
    private const val KeySoftGlassTuningSaturationDark = "soft_glass_tuning_saturation_dark"
    private const val KeySoftGlassTuningEdge = "soft_glass_tuning_edge_thickness"
    private const val KeySoftGlassTuningShapeEdgePx = "soft_glass_tuning_shape_edge_px"
    private const val KeySoftGlassTuningShapeEdgePow = "soft_glass_tuning_shape_edge_pow"
    private const val KeySoftGlassTuningReflectOffset = "soft_glass_tuning_reflect_offset"
    private const val KeySoftGlassTuningReflection = "soft_glass_tuning_reflection_strength"
    private const val KeySoftGlassTuningReflectLighten = "soft_glass_tuning_reflection_lighten"
    // Retired single-value key; still read once if the split keys are absent.
    private const val KeySoftGlassTuningCardTint = "soft_glass_tuning_card_tint_alpha"
    private const val KeySoftGlassTuningCardTintLight = "soft_glass_tuning_card_tint_light"
    private const val KeySoftGlassTuningCardTintDark = "soft_glass_tuning_card_tint_dark"
    private const val KeySoftGlassDefaultsVersion = "soft_glass_defaults_version"
    private const val SoftGlassDefaultsVersion = 5
    private const val SoftGlassBlurDefault = 15
    private const val KeyMonetSeed = "monet_seed"
    private const val CustomFileName = "custom_wallpaper.jpg"
    private const val MaxWallpaperSide = 2560

    private val _wallpaper = MutableStateFlow(WyuWallpaper.Mist)
    val wallpaper: StateFlow<WyuWallpaper> = _wallpaper.asStateFlow()

    private val _themeMode = MutableStateFlow(WyuThemeMode.System)
    val themeMode: StateFlow<WyuThemeMode> = _themeMode.asStateFlow()

    private val _customWallpaper = MutableStateFlow<ImageBitmap?>(null)
    val customWallpaper: StateFlow<ImageBitmap?> = _customWallpaper.asStateFlow()

    private val _hasCustomWallpaper = MutableStateFlow(false)
    val hasCustomWallpaper: StateFlow<Boolean> = _hasCustomWallpaper.asStateFlow()

    private val _customBrightnessLight = MutableStateFlow(1f)
    val customBrightnessLight: StateFlow<Float> = _customBrightnessLight.asStateFlow()

    // Dark mode no longer forces a dim, so its slider starts at 80%.
    private val _customBrightnessDark = MutableStateFlow(BrightnessDarkDefault)
    val customBrightnessDark: StateFlow<Float> = _customBrightnessDark.asStateFlow()

    private val _softGlass = MutableStateFlow(false)
    val softGlass: StateFlow<Boolean> = _softGlass.asStateFlow()

    private val _softGlassBlur = MutableStateFlow(SoftGlassBlurDefault)
    val softGlassBlur: StateFlow<Int> = _softGlassBlur.asStateFlow()

    private val _softGlassTuning = MutableStateFlow(WyuSoftGlassTuning())
    val softGlassTuning: StateFlow<WyuSoftGlassTuning> = _softGlassTuning.asStateFlow()

    private val _monetSeed = MutableStateFlow<Int?>(null)
    val monetSeed: StateFlow<Int?> = _monetSeed.asStateFlow()

    private val _customWallpaperResolved = MutableStateFlow(false)

    /** True once the custom wallpaper bitmap is available (or known absent). */
    val customWallpaperResolved: StateFlow<Boolean> = _customWallpaperResolved.asStateFlow()

    private var prefs: SharedPreferences? = null
    @Volatile
    private var loadingCustom = false

    fun ensureLoaded(context: Context) {
        if (prefs != null) return
        val appContext = context.applicationContext
        val loaded = appContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
        prefs = loaded
        // v5 ships the tuned soft-glass defaults, so drop any saved slider
        // overrides (including the retired card-tint keys) and reset blur.
        if (loaded.getInt(KeySoftGlassDefaultsVersion, 0) < SoftGlassDefaultsVersion) {
            val brightnessLight = loaded
                .getFloat(KeyCustomBrightnessLight, loaded.getFloat(KeyCustomBrightness, 1f))
                .coerceIn(0.5f, 1f)
            val brightnessDark = loaded
                .getFloat(KeyCustomBrightnessDark, BrightnessDarkDefault)
                .coerceIn(0.5f, 1f)
            loaded.edit()
                .putInt(KeySoftGlassBlur, SoftGlassBlurDefault)
                .putFloat(KeyCustomBrightnessLight, brightnessLight)
                .putFloat(KeyCustomBrightnessDark, brightnessDark)
                .remove(KeyCustomBrightness)
                .remove(KeySoftGlassTuningIor)
                .remove(KeySoftGlassTuningSaturationLight)
                .remove(KeySoftGlassTuningSaturationDark)
                .remove(KeySoftGlassTuningEdge)
                .remove(KeySoftGlassTuningShapeEdgePx)
                .remove(KeySoftGlassTuningShapeEdgePow)
                .remove(KeySoftGlassTuningReflectOffset)
                .remove(KeySoftGlassTuningReflection)
                .remove(KeySoftGlassTuningReflectLighten)
                .remove(KeySoftGlassTuningCardTint)
                .remove(KeySoftGlassTuningCardTintLight)
                .remove(KeySoftGlassTuningCardTintDark)
                .putInt(KeySoftGlassDefaultsVersion, SoftGlassDefaultsVersion)
                .apply()
        }
        _hasCustomWallpaper.value = customFile(appContext).let { it.isFile && it.length() > 0L }
        if (!_hasCustomWallpaper.value) _customWallpaperResolved.value = true
        _customBrightnessLight.value = loaded
            .getFloat(KeyCustomBrightnessLight, loaded.getFloat(KeyCustomBrightness, 1f))
            .coerceIn(0.5f, 1f)
        _customBrightnessDark.value = loaded
            .getFloat(KeyCustomBrightnessDark, BrightnessDarkDefault)
            .coerceIn(0.5f, 1f)
        _softGlass.value = loaded.getBoolean(KeySoftGlass, false)
        _softGlassBlur.value = loaded.getInt(KeySoftGlassBlur, SoftGlassBlurDefault)
            .coerceIn(WyuSoftGlassBlurMin, 100)
        val tuningDefaults = WyuSoftGlassTuning()
        _softGlassTuning.value = WyuSoftGlassTuning(
            refractionIor = loaded.getFloat(KeySoftGlassTuningIor, tuningDefaults.refractionIor)
                .coerceIn(0.1f, 10f),
            saturationLight = loaded
                .getFloat(KeySoftGlassTuningSaturationLight, tuningDefaults.saturationLight)
                .coerceIn(0f, 2f),
            saturationDark = loaded
                .getFloat(KeySoftGlassTuningSaturationDark, tuningDefaults.saturationDark)
                .coerceIn(0f, 2f),
            edgeThickness = loaded.getFloat(KeySoftGlassTuningEdge, tuningDefaults.edgeThickness)
                .coerceIn(0f, 800f),
            shapeEdgePx = loaded.getFloat(KeySoftGlassTuningShapeEdgePx, tuningDefaults.shapeEdgePx)
                .coerceIn(0.5f, 432f),
            shapeEdgePow = loaded
                .getFloat(KeySoftGlassTuningShapeEdgePow, tuningDefaults.shapeEdgePow)
                .coerceIn(0.05f, 22.8f),
            reflectOffset = loaded
                .getFloat(KeySoftGlassTuningReflectOffset, tuningDefaults.reflectOffset)
                .coerceIn(-3200f, 4800f),
            reflectionStrength = loaded
                .getFloat(KeySoftGlassTuningReflection, tuningDefaults.reflectionStrength)
                .coerceIn(0f, 2f),
            reflectLighten = loaded
                .getFloat(KeySoftGlassTuningReflectLighten, tuningDefaults.reflectLighten)
                .coerceIn(-4.8f, 7.2f),
            cardTintAlphaLight = loaded
                .getFloat(
                    KeySoftGlassTuningCardTintLight,
                    loaded.getFloat(KeySoftGlassTuningCardTint, tuningDefaults.cardTintAlphaLight),
                )
                .coerceIn(0f, 1f),
            // The retired key never governed dark default cards, so it must not
            // seed the dark value; keeping the dark default preserves the look.
            cardTintAlphaDark = loaded
                .getFloat(KeySoftGlassTuningCardTintDark, tuningDefaults.cardTintAlphaDark)
                .coerceIn(0f, 1f),
        )
        _monetSeed.value = loaded.getInt(KeyMonetSeed, 0).takeIf { it != 0 }
        val saved = WyuWallpaper.entries
            .firstOrNull { it.name == loaded.getString(KeyWallpaper, null) }
        // A stored custom choice that no longer has a file falls back to the
        // stock 晨雾; the retired "Default" entry resolves to the same thing.
        _wallpaper.value = when {
            saved == WyuWallpaper.Custom && !_hasCustomWallpaper.value -> WyuWallpaper.Mist
            saved != null -> saved
            else -> WyuWallpaper.Mist
        }
        _themeMode.value = WyuThemeMode.entries
            .firstOrNull { it.name == loaded.getString(KeyThemeMode, null) }
            ?: WyuThemeMode.System
    }

    fun setThemeMode(context: Context, mode: WyuThemeMode) {
        ensureLoaded(context)
        if (_themeMode.value == mode) return
        _themeMode.value = mode
        prefs?.edit()?.putString(KeyThemeMode, mode.name)?.apply()
    }

    fun setWallpaper(context: Context, wallpaper: WyuWallpaper) {
        ensureLoaded(context)
        if (wallpaper == WyuWallpaper.Custom && !_hasCustomWallpaper.value) return
        if (_wallpaper.value == wallpaper) return
        prefs?.edit()?.putString(KeyWallpaper, wallpaper.name)?.apply()
        _wallpaper.value = wallpaper
    }

    fun setCustomBrightness(context: Context, brightness: Float, dark: Boolean) {
        ensureLoaded(context)
        val clamped = brightness.coerceIn(0.5f, 1f)
        val target = if (dark) _customBrightnessDark else _customBrightnessLight
        if (target.value == clamped) return
        target.value = clamped
        prefs?.edit()
            ?.putFloat(if (dark) KeyCustomBrightnessDark else KeyCustomBrightnessLight, clamped)
            ?.apply()
    }

    fun setSoftGlassBlur(context: Context, percent: Int) {
        ensureLoaded(context)
        val clamped = percent.coerceIn(WyuSoftGlassBlurMin, 100)
        if (_softGlassBlur.value == clamped) return
        _softGlassBlur.value = clamped
        prefs?.edit()?.putInt(KeySoftGlassBlur, clamped)?.apply()
    }

    fun setSoftGlass(context: Context, enabled: Boolean) {
        ensureLoaded(context)
        if (_softGlass.value == enabled) return
        _softGlass.value = enabled
        prefs?.edit()?.putBoolean(KeySoftGlass, enabled)?.apply()
    }

    fun setSoftGlassTuning(context: Context, tuning: WyuSoftGlassTuning) {
        ensureLoaded(context)
        val clamped = tuning.copy(
            refractionIor = ((tuning.refractionIor.coerceIn(0.1f, 10f) * 10f).roundToInt() / 10f),
            saturationLight = ((tuning.saturationLight.coerceIn(0f, 2f) * 100f).roundToInt() / 100f),
            saturationDark = ((tuning.saturationDark.coerceIn(0f, 2f) * 100f).roundToInt() / 100f),
            edgeThickness = tuning.edgeThickness.coerceIn(0f, 800f).roundToInt().toFloat(),
            shapeEdgePx = tuning.shapeEdgePx.coerceIn(0.5f, 432f).roundToInt().toFloat(),
            shapeEdgePow = ((tuning.shapeEdgePow.coerceIn(0.05f, 22.8f) * 20f).roundToInt() / 20f),
            reflectOffset = ((tuning.reflectOffset.coerceIn(-3200f, 4800f) / 10f).roundToInt() * 10f),
            reflectionStrength = ((tuning.reflectionStrength.coerceIn(0f, 2f) * 100f).roundToInt() / 100f),
            reflectLighten = ((tuning.reflectLighten.coerceIn(-4.8f, 7.2f) * 20f).roundToInt() / 20f),
            cardTintAlphaLight = ((tuning.cardTintAlphaLight.coerceIn(0f, 1f) * 100f).roundToInt() / 100f),
            cardTintAlphaDark = ((tuning.cardTintAlphaDark.coerceIn(0f, 1f) * 100f).roundToInt() / 100f),
        )
        if (_softGlassTuning.value == clamped) return
        _softGlassTuning.value = clamped
        prefs?.edit()
            ?.putFloat(KeySoftGlassTuningIor, clamped.refractionIor)
            ?.putFloat(KeySoftGlassTuningSaturationLight, clamped.saturationLight)
            ?.putFloat(KeySoftGlassTuningSaturationDark, clamped.saturationDark)
            ?.putFloat(KeySoftGlassTuningEdge, clamped.edgeThickness)
            ?.putFloat(KeySoftGlassTuningShapeEdgePx, clamped.shapeEdgePx)
            ?.putFloat(KeySoftGlassTuningShapeEdgePow, clamped.shapeEdgePow)
            ?.putFloat(KeySoftGlassTuningReflectOffset, clamped.reflectOffset)
            ?.putFloat(KeySoftGlassTuningReflection, clamped.reflectionStrength)
            ?.putFloat(KeySoftGlassTuningReflectLighten, clamped.reflectLighten)
            ?.putFloat(KeySoftGlassTuningCardTintLight, clamped.cardTintAlphaLight)
            ?.putFloat(KeySoftGlassTuningCardTintDark, clamped.cardTintAlphaDark)
            ?.remove(KeySoftGlassTuningCardTint)
            ?.apply()
    }

    suspend fun loadCustomWallpaper(context: Context) {
        if (_customWallpaper.value != null) {
            _customWallpaperResolved.value = true
            return
        }
        if (loadingCustom) return
        val appContext = context.applicationContext
        val file = customFile(appContext)
        if (!file.isFile) {
            _hasCustomWallpaper.value = false
            _customWallpaperResolved.value = true
            return
        }
        loadingCustom = true
        try {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
            }
            if (bitmap == null) {
                _hasCustomWallpaper.value = false
                if (_wallpaper.value == WyuWallpaper.Custom) {
                    prefs?.edit()?.putString(KeyWallpaper, WyuWallpaper.Mist.name)?.apply()
                    _wallpaper.value = WyuWallpaper.Mist
                }
                return
            }
            // Publish the decoded frame before quantizing: the seed is normally
            // already restored from prefs and only tints the theme, so it must
            // not hold the wallpaper back during startup.
            _customWallpaper.value = bitmap.asImageBitmap()
            _customWallpaperResolved.value = true
            if (_monetSeed.value == null) {
                val seed = withContext(Dispatchers.IO) {
                    runCatching { WyuMonet.seedFromBitmap(bitmap) }.getOrNull()
                }
                if (seed != null) applyMonetSeed(seed)
            }
        } finally {
            loadingCustom = false
            _customWallpaperResolved.value = true
        }
    }

    suspend fun decodeWallpaperForCrop(context: Context, uri: Uri): Bitmap? {
        val appContext = context.applicationContext
        return withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    decodeModern(appContext, uri)
                } else {
                    decodeLegacy(appContext, uri)
                }
            }.getOrNull()
        }
    }

    suspend fun applyCustomWallpaper(context: Context, source: Bitmap, crop: Rect): Boolean {
        ensureLoaded(context)
        val appContext = context.applicationContext
        val metrics = appContext.resources.displayMetrics
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                cropToScreen(source, crop, metrics.widthPixels, metrics.heightPixels)
            }.getOrNull()
        } ?: return false
        return publishCustomWallpaper(appContext, bitmap)
    }

    private suspend fun publishCustomWallpaper(context: Context, bitmap: Bitmap): Boolean {
        val saved = withContext(Dispatchers.IO) {
            runCatching {
                val seed = WyuMonet.seedFromBitmap(bitmap)
                saveCustomWallpaper(context, bitmap) to seed
            }.getOrNull()
        }
        if (saved == null || !saved.first) return false

        _customWallpaper.value = bitmap.asImageBitmap()
        _customWallpaperResolved.value = true
        _hasCustomWallpaper.value = true
        prefs?.edit()?.putString(KeyWallpaper, WyuWallpaper.Custom.name)?.apply()
        _wallpaper.value = WyuWallpaper.Custom
        applyMonetSeed(saved.second)
        return true
    }

    private fun applyMonetSeed(seed: Int?) {
        if (_monetSeed.value == seed) return
        _monetSeed.value = seed
        prefs?.edit()?.putInt(KeyMonetSeed, seed ?: 0)?.apply()
    }

    private fun customFile(context: Context): File =
        File(context.applicationContext.filesDir, CustomFileName)

    private fun saveCustomWallpaper(context: Context, bitmap: Bitmap): Boolean {
        val target = customFile(context)
        val temp = File(target.parentFile, "${target.name}.tmp")
        FileOutputStream(temp).use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)) return false
            out.fd.sync()
        }
        if (temp.renameTo(target)) return true
        return runCatching {
            temp.copyTo(target, overwrite = true)
            temp.delete()
            true
        }.getOrDefault(false)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeModern(context: Context, uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val width = info.size.width
            val height = info.size.height
            val longSide = max(width, height)
            if (longSide > MaxWallpaperSide) {
                val scale = MaxWallpaperSide.toFloat() / longSide
                decoder.setTargetSize(
                    (width * scale).roundToInt().coerceAtLeast(1),
                    (height * scale).roundToInt().coerceAtLeast(1),
                )
            }
        }
    }

    private fun decodeLegacy(context: Context, uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val width = bounds.outWidth
        val height = bounds.outHeight
        require(width > 0 && height > 0)

        var sample = 1
        while (max(width / sample, height / sample) > MaxWallpaperSide) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: error("Unable to decode wallpaper")

        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        return rotateByExif(bitmap, orientation)
    }

    private fun rotateByExif(source: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }

            else -> return source
        }
        return runCatching {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
                .also { if (it !== source) source.recycle() }
        }.getOrDefault(source)
    }

    private fun cropToScreen(source: Bitmap, crop: Rect, targetWidth: Int, targetHeight: Int): Bitmap {
        if (targetWidth <= 0 || targetHeight <= 0) return source
        val src = Rect(crop)
        src.intersect(0, 0, source.width, source.height)
        require(!src.isEmpty)
        val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        canvas.drawBitmap(
            source,
            src,
            Rect(0, 0, targetWidth, targetHeight),
            paint,
        )
        if (result !== source) source.recycle()
        return result
    }
}

val LocalWyuWallpaper = staticCompositionLocalOf { WyuWallpaper.Mist }
val LocalWyuCustomWallpaper = staticCompositionLocalOf<ImageBitmap?> { null }
val LocalWyuDarkTheme = staticCompositionLocalOf { false }
