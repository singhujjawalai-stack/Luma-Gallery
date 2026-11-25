package com.ujjaw.lumagallery.work

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.ujjaw.lumagallery.data.MediaLib
import com.ujjaw.lumagallery.data.db.AppDb
import com.ujjaw.lumagallery.data.db.ContentEntity
import com.ujjaw.lumagallery.util.decodeScaled
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * On-device content indexing so photos can be searched by *what's inside them*:
 *  - text recognition (OCR) picks up any text in the image (signs, screenshots, notes)
 *  - image labeling detects objects/scenes ("Ball", "Red", "Sky", "Dog", "Beach"…)
 * Both models come from Google Play Services (downloaded once, updated by Play) and
 * run fully on-device — no photo or result ever leaves the phone.
 */
class ContentIndexWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = AppDb.db
        val ctx = applicationContext

        val media = try {
            MediaLib.loadSync(ctx)
            MediaLib.items
        } catch (e: Exception) {
            emptyList()
        }
        val indexed = db.contentDao().allOnce().map { MediaLib.norm(it.mediaUri) }.toSet()

        // Take the newest images that still need indexing (older photos are reached
        // in later runs instead of being permanently skipped by the 1500 cap).
        val images = media
            .filter { !it.isVideo && MediaLib.norm(it.uri) !in indexed }
            .take(1500)

        android.util.Log.d(
            "ContentIndexWorker",
            "start: ${media.size} media, ${images.size} new images to index, ${indexed.size} already indexed"
        )

        if (images.isEmpty()) {
            android.util.Log.d("ContentIndexWorker", "nothing new to index")
            return Result.success()
        }

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

        Indexing.contentRunning = true
        try {
            for (m in images) {
                coroutineContext.ensureActive()
                if (MediaLib.norm(m.uri) in indexed) continue

                val bmp = decodeScaled(ctx, Uri.parse(m.uri), 1024)
                if (bmp == null) {
                    // Unreadable (e.g. cloud-only) — record an empty entry so it isn't
                    // retried on every launch (which would keep the spinner alive).
                    db.contentDao().upsert(ContentEntity(mediaUri = m.uri, text = "", labels = ""))
                    continue
                }
                try {
                    val input = InputImage.fromBitmap(bmp, 0)

                    val text = try {
                        recognizer.process(input).await()
                            .text
                            .replace(Regex("\\s+"), " ")
                            .trim()
                            .take(2000)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        ""
                    }

                    val labels = try {
                        labeler.process(input).await()
                            .sortedByDescending { it.confidence }
                            .take(8)
                            .joinToString(",") { "${it.text}:${(it.confidence * 100).toInt()}" }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        ""
                    }

                    // Store even when nothing was recognized, so images without text or
                    // recognizable objects aren't re-processed on every launch.
                    db.contentDao().upsert(ContentEntity(mediaUri = m.uri, text = text, labels = labels))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Unreadable/failed image — still record it, otherwise it would be
                    // retried on every launch and keep the "indexing" spinner alive.
                    try {
                        db.contentDao().upsert(
                            ContentEntity(mediaUri = m.uri, text = "", labels = "")
                        )
                    } catch (ignored: Exception) {
                    }
                } finally {
                    // Never recycle while cancelled work may still be reading the bitmap.
                    if (coroutineContext.isActive) bmp.recycle()
                }
            }
        } finally {
            recognizer.close()
            labeler.close()
            Indexing.contentRunning = false
        }

        android.util.Log.d("ContentIndexWorker", "done: ${images.size} images scanned, ${indexed.size} already indexed")
        return Result.success()
    }
}
