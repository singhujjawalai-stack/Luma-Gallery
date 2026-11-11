package com.ujjaw.lumagallery.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import kotlin.math.max

fun decodeScaled(context: Context, uri: Uri, maxDim: Int): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = context.contentResolver.openInputStream(uri) ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val maxSide = max(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (maxSide / (sample * 2) >= maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decodeStream = context.contentResolver.openInputStream(uri) ?: return null
        decodeStream.use { BitmapFactory.decodeStream(it, null, opts) }
    } catch (e: Exception) {
        null
    }
}

fun rotateBitmap(src: Bitmap, degrees: Float): Bitmap {
    val m = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}

/** 8x8 average hash of a bitmap (perceptual fingerprint). */
fun averageHash(src: Bitmap): Long {
    val tiny = Bitmap.createScaledBitmap(src, 8, 8, true)
    val pixels = IntArray(64)
    tiny.getPixels(pixels, 0, 8, 0, 0, 8, 8)
    var avg = 0.0
    val lum = DoubleArray(64)
    for (i in 0 until 64) {
        val p = pixels[i]
        val l = 0.299 * ((p shr 16) and 0xFF) + 0.587 * ((p shr 8) and 0xFF) + 0.114 * (p and 0xFF)
        lum[i] = l
        avg += l
    }
    avg /= 64.0
    var bits = 0L
    for (i in 0 until 64) {
        if (lum[i] > avg) bits = bits or (1L shl i)
    }
    return bits
}

fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)
