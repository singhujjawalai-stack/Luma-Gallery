package com.ujjaw.lumagallery.util

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Computes which fraction of the source image the user currently sees in the
 * zoomed viewer, so the wallpaper can match the visible view exactly.
 *
 * Returns null when the parameters don't form a valid crop (or the image isn't
 * zoomed at all), in which case the full image is used.
 *
 * @param scale   viewer zoom (1f = fit whole image)
 * @param pan     viewer pan offset in container px
 * @param cw/ch   container (screen) size the image was fit into
 * @param iw/ih   intrinsic image size
 */
fun visibleCropFraction(
    scale: Float,
    panX: Float,
    panY: Float,
    cw: Int,
    ch: Int,
    iw: Int,
    ih: Int
): RectF? {
    if (cw <= 0 || ch <= 0 || iw <= 0 || ih <= 0) return null
    // Size of the fitted image inside the container (ContentScale.Fit)
    val fitScale = minOf(cw.toFloat() / iw, ch.toFloat() / ih)
    val fitW = iw * fitScale
    val fitH = ih * fitScale
    // Displayed size after zooming
    val dispW = fitW * scale
    val dispH = fitH * scale
    if (dispW < 1f || dispH < 1f) return null
    if (dispW <= cw + 1f && dispH <= ch + 1f) return null // whole image visible

    // Center of the displayed image, offset by pan
    val centerX = cw / 2f - panX
    val centerY = ch / 2f - panY
    val left = centerX - dispW / 2f
    val top = centerY - dispH / 2f

    // Visible window in image fraction
    val fL = ((-left) / dispW).coerceIn(0f, 1f)
    val fT = ((-top) / dispH).coerceIn(0f, 1f)
    val fR = ((cw - left) / dispW).coerceIn(0f, 1f)
    val fB = ((ch - top) / dispH).coerceIn(0f, 1f)
    if (fR - fL < 0.02f || fB - fT < 0.02f) return null
    return RectF(fL, fT, fR, fB)
}

/** Applies an image as wallpaper. `which` = WallpaperManager.FLAG_SYSTEM / FLAG_LOCK. */
suspend fun applyWallpaper(
    context: Context,
    uri: Uri,
    which: Int,
    crop: RectF? = null
): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val bmp = decodeScaled(context, uri, 2560) ?: return@withContext false
            val finalBmp = if (crop != null && crop.width() > 0.02f && crop.height() > 0.02f) {
                val x = (crop.left * bmp.width).toInt().coerceIn(0, bmp.width - 1)
                val y = (crop.top * bmp.height).toInt().coerceIn(0, bmp.height - 1)
                val w = (crop.width() * bmp.width).toInt()
                    .coerceIn(1, bmp.width - x)
                val h = (crop.height() * bmp.height).toInt()
                    .coerceIn(1, bmp.height - y)
                Bitmap.createBitmap(bmp, x, y, w, h).also {
                    if (it != bmp) bmp.recycle()
                }
            } else bmp
            val wm = WallpaperManager.getInstance(context)
            if (which and WallpaperManager.FLAG_SYSTEM != 0) {
                wm.setBitmap(finalBmp, null, true, WallpaperManager.FLAG_SYSTEM)
            }
            if (which and WallpaperManager.FLAG_LOCK != 0) {
                wm.setBitmap(finalBmp, null, true, WallpaperManager.FLAG_LOCK)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
