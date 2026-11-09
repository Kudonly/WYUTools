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
    Default("默认"),
    Mist("晨雾"),
    Jade("青岚"),
    Dusk("晚霞"),
    Aurora("极光"),
    Custom("自定义");

    fun backgroundStops(dark: Boolean, surface: Color): List<Pair<Float, Color>>? {
        val stops = when (this) {
            Default, Custom -> return null
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

/** Slider percentage is the wallpaper's actual brightness; dark theme still adds a floor. */
fun wyuCustomWallpaperDim(brightness: Float, dark: Boolean): Float =
    ((1f - brightness.coerceIn(0.8f, 1f)) + if (dark) 0.35f else 0f).coerceIn(0f, 0.92f)

object WyuAppearance {
    private const val PrefsName = "appearance"
    private const val KeyWallpaper = "wallpaper"
    private const val KeyThemeMode = "theme_mode"
    private const val KeyCustomBrightness = "custom_brightness"
    private const val KeySoftGlass = "soft_glass"
    private const val KeySoftGlassBlur = "soft_glass_blur_radius"
    private const val KeySoftGlassDefaultsVersion = "soft_glass_defaults_version"
    private const val SoftGlassDefaultsVersion = 2
    private const val KeyMonetSeed = "monet_seed"
    private const val CustomFileName = "custom_wallpaper.jpg"
    private const val MaxWallpaperSide = 2560

    private val _wallpaper = MutableStateFlow(WyuWallpaper.Default)
    val wallpaper: StateFlow<WyuWallpaper> = _wallpaper.asStateFlow()

    private val _themeMode = MutableStateFlow(WyuThemeMode.System)
    val themeMode: StateFlow<WyuThemeMode> = _themeMode.asStateFlow()

    private val _customWallpaper = MutableStateFlow<ImageBitmap?>(null)
    val customWallpaper: StateFlow<ImageBitmap?> = _customWallpaper.asStateFlow()

    private val _hasCustomWallpaper = MutableStateFlow(false)
    val hasCustomWallpaper: StateFlow<Boolean> = _hasCustomWallpaper.asStateFlow()

    private val _customBrightness = MutableStateFlow(1f)
    val customBrightness: StateFlow<Float> = _customBrightness.asStateFlow()

    private val _softGlass = MutableStateFlow(false)
    val softGlass: StateFlow<Boolean> = _softGlass.asStateFlow()

    private val _softGlassBlur = MutableStateFlow(20)
    val softGlassBlur: StateFlow<Int> = _softGlassBlur.asStateFlow()

    private val _monetSeed = MutableStateFlow<Int?>(null)
    val monetSeed: StateFlow<Int?> = _monetSeed.asStateFlow()

    private var prefs: SharedPreferences? = null
    @Volatile
    private var loadingCustom = false

    fun ensureLoaded(context: Context) {
        if (prefs != null) return
        val appContext = context.applicationContext
        val loaded = appContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
        prefs = loaded
        if (loaded.getInt(KeySoftGlassDefaultsVersion, 0) < SoftGlassDefaultsVersion) {
            val brightness = loaded.getFloat(KeyCustomBrightness, 1f).coerceIn(0.8f, 1f)
            loaded.edit()
                .putInt(KeySoftGlassBlur, 20)
                .putFloat(KeyCustomBrightness, brightness)
                .putInt(KeySoftGlassDefaultsVersion, SoftGlassDefaultsVersion)
                .apply()
        }
        _hasCustomWallpaper.value = customFile(appContext).let { it.isFile && it.length() > 0L }
        _customBrightness.value = loaded.getFloat(KeyCustomBrightness, 1f).coerceIn(0.8f, 1f)
        _softGlass.value = loaded.getBoolean(KeySoftGlass, false)
        _softGlassBlur.value = loaded.getInt(KeySoftGlassBlur, 20).coerceIn(0, 100)
        _monetSeed.value = loaded.getInt(KeyMonetSeed, 0).takeIf { it != 0 }
        val saved = WyuWallpaper.entries
            .firstOrNull { it.name == loaded.getString(KeyWallpaper, null) }
        _wallpaper.value = when {
            saved == WyuWallpaper.Custom && !_hasCustomWallpaper.value -> WyuWallpaper.Default
            saved != null -> saved
            else -> WyuWallpaper.Default
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

    fun setCustomBrightness(context: Context, brightness: Float) {
        ensureLoaded(context)
        val clamped = brightness.coerceIn(0.8f, 1f)
        if (_customBrightness.value == clamped) return
        _customBrightness.value = clamped
        prefs?.edit()?.putFloat(KeyCustomBrightness, clamped)?.apply()
    }

    fun setSoftGlassBlur(context: Context, percent: Int) {
        ensureLoaded(context)
        val clamped = percent.coerceIn(0, 100)
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

    suspend fun loadCustomWallpaper(context: Context) {
        if (_customWallpaper.value != null || loadingCustom) return
        val appContext = context.applicationContext
        val file = customFile(appContext)
        if (!file.isFile) {
            _hasCustomWallpaper.value = false
            return
        }
        loadingCustom = true
        try {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@runCatching null
                    val seed = WyuMonet.seedFromBitmap(bitmap)
                    bitmap.asImageBitmap() to seed
                }.getOrNull()
            }
            if (loaded != null) {
                _customWallpaper.value = loaded.first
                applyMonetSeed(loaded.second)
            } else {
                _hasCustomWallpaper.value = false
                if (_wallpaper.value == WyuWallpaper.Custom) {
                    prefs?.edit()?.putString(KeyWallpaper, WyuWallpaper.Default.name)?.apply()
                    _wallpaper.value = WyuWallpaper.Default
                }
            }
        } finally {
            loadingCustom = false
        }
    }

    suspend fun importCustomWallpaper(context: Context, uri: Uri): Boolean {
        ensureLoaded(context)
        val appContext = context.applicationContext
        val metrics = appContext.resources.displayMetrics
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    decodeModern(appContext, uri)
                } else {
                    decodeLegacy(appContext, uri)
                }
                cropToScreen(decoded, metrics.widthPixels, metrics.heightPixels)
            }.getOrNull()
        } ?: return false

        val saved = withContext(Dispatchers.IO) {
            runCatching {
                val seed = WyuMonet.seedFromBitmap(bitmap)
                saveCustomWallpaper(appContext, bitmap) to seed
            }.getOrNull()
        }
        if (saved == null || !saved.first) return false

        _customWallpaper.value = bitmap.asImageBitmap()
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

    private fun cropToScreen(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        if (targetWidth <= 0 || targetHeight <= 0) return source
        val targetAspect = targetWidth.toFloat() / targetHeight
        val sourceAspect = source.width.toFloat() / source.height
        val cropWidth: Int
        val cropHeight: Int
        if (sourceAspect > targetAspect) {
            cropHeight = source.height
            cropWidth = (cropHeight * targetAspect).roundToInt().coerceIn(1, source.width)
        } else {
            cropWidth = source.width
            cropHeight = (cropWidth / targetAspect).roundToInt().coerceIn(1, source.height)
        }
        val left = (source.width - cropWidth) / 2
        val top = (source.height - cropHeight) / 2
        val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        canvas.drawBitmap(
            source,
            Rect(left, top, left + cropWidth, top + cropHeight),
            Rect(0, 0, targetWidth, targetHeight),
            paint,
        )
        if (result !== source) source.recycle()
        return result
    }
}

val LocalWyuWallpaper = staticCompositionLocalOf { WyuWallpaper.Default }
val LocalWyuCustomWallpaper = staticCompositionLocalOf<ImageBitmap?> { null }
val LocalWyuDarkTheme = staticCompositionLocalOf { false }
