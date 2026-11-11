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
import kotlinx.coroutines.tasks.await

/**
 * On-device content indexing so photos can be searched by *what's inside them*:
 *  - text recognition (OCR) picks up any text in the image (signs, screenshots, notes)
 *  - image labeling detects objects/scenes ("Ball", "Red", "Sky", "Dog", "Beach"…)
 * Both models are bundled and run fully offline. Results are stored in Room and
 * matched locally by the search screen.
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
        val images = media.filter { !it.isVideo }.take(1500)
        val indexed = db.contentDao().allOnce().map { MediaLib.norm(it.mediaUri) }.toSet()

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

        try {
            for (m in images) {
                if (MediaLib.norm(m.uri) in indexed) continue

                val bmp = decodeScaled(ctx, Uri.parse(m.uri), 1024) ?: continue
                try {
                    val input = InputImage.fromBitmap(bmp, 0)

                    val text = try {
                        recognizer.process(input).await()
                            .text
                            .replace(Regex("\\s+"), " ")
                            .trim()
                            .take(2000)
                    } catch (e: Exception) {
                        ""
                    }

                    val labels = try {
                        labeler.process(input).await()
                            .sortedByDescending { it.confidence }
                            .take(8)
                            .joinToString(",") { "${it.text}:${(it.confidence * 100).toInt()}" }
                    } catch (e: Exception) {
                        ""
                    }

                    if (text.isNotBlank() || labels.isNotBlank()) {
                        db.contentDao().upsert(ContentEntity(mediaUri = m.uri, text = text, labels = labels))
                    }
                } catch (e: Exception) {
                    // Skip unreadable images
                } finally {
                    bmp.recycle()
                }
            }
        } finally {
            recognizer.close()
            labeler.close()
        }

        return Result.success()
    }
}
