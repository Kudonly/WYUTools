package com.wyutools.app.ui

import android.graphics.Bitmap
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.score.Score
import kotlin.math.max
import kotlin.math.roundToInt

/** Android Monet wallpaper seed extraction (Celebi quantizer + Score ranking). */
object WyuMonet {
    private const val MaxSampleSide = 128
    private const val MaxColors = 128

    fun seedFromBitmap(bitmap: Bitmap): Int? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        val longSide = max(bitmap.width, bitmap.height)
        val scaled = if (longSide > MaxSampleSide) {
            val scale = MaxSampleSide.toFloat() / longSide
            runCatching {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                    (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                    true,
                )
            }.getOrNull() ?: return null
        } else {
            bitmap
        }
        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        if (scaled !== bitmap) scaled.recycle()
        val ranked = Score.score(QuantizerCelebi.quantize(pixels, MaxColors))
        return ranked.firstOrNull()
    }
}
