package com.wyutools.app.elec

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object QrCodes {
    private const val SIZE = 720

    fun encode(content: String): Bitmap {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.MARGIN to 2,
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, SIZE, SIZE, hints)
        val pixels = IntArray(SIZE * SIZE)
        for (y in 0 until SIZE) {
            val offset = y * SIZE
            for (x in 0 until SIZE) {
                pixels[offset + x] = if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        return Bitmap.createBitmap(pixels, SIZE, SIZE, Bitmap.Config.ARGB_8888)
    }

    fun saveToGallery(
        context: Context,
        bitmap: Bitmap,
        prefix: String = "WYUtools-电费充值",
    ): String {
        val fileName = "$prefix-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.CHINA).format(Date())}.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/WYUtools")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("无法创建图片文件")
            try {
                val stream = resolver.openOutputStream(uri) ?: throw IOException("无法写入图片文件")
                stream.use { output ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                        throw IOException("二维码保存失败")
                    }
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            return "已保存到相册 Pictures/WYUtools"
        }

        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "WYUtools")
        if (!dir.exists() && !dir.mkdirs()) throw IOException("无法创建保存目录")
        val file = File(dir, fileName)
        FileOutputStream(file).use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw IOException("二维码保存失败")
            }
        }
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
        return "已保存到 ${file.absolutePath}"
    }
}
