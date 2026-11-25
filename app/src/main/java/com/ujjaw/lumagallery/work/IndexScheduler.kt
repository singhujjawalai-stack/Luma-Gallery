package com.ujjaw.lumagallery.work

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager

/**
 * Single place that schedules the incremental indexing workers.
 *
 * Uses KEEP so a running scan is never restarted (repeated taps are a no-op), but
 * detects the one case KEEP gets wrong: work left in RUNNING by a process that was
 * killed mid-scan. The in-process [Indexing] flags prove whether a worker is really
 * alive, so a RUNNING request without a live worker is cancelled and re-queued.
 */
object IndexScheduler {

    fun content(context: Context) {
        schedule(context, "content_index", Indexing.contentRunning) {
            OneTimeWorkRequestBuilder<ContentIndexWorker>()
        }
    }

    fun faces(context: Context) {
        schedule(context, "face_index", Indexing.faceRunning) {
            OneTimeWorkRequestBuilder<FaceIndexWorker>()
        }
    }

    private inline fun schedule(
        context: Context,
        name: String,
        live: Boolean,
        request: () -> OneTimeWorkRequest.Builder
    ) {
        val wm = WorkManager.getInstance(context.applicationContext)
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()
        val req = request().setConstraints(constraints).build()

        val future = wm.getWorkInfosForUniqueWork(name)
        future.addListener({
            val infos = try {
                future.get()
            } catch (e: Exception) {
                emptyList<WorkInfo>()
            }
            val executor = ContextCompat.getMainExecutor(context)
            if (!live && infos.any { it.state == WorkInfo.State.RUNNING }) {
                // Stale request from a dead process — clear it, then queue a fresh one
                // (waiting for the cancel to land, otherwise KEEP would still see it).
                wm.cancelUniqueWork(name).result.addListener({
                    wm.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, req)
                }, executor)
            } else {
                wm.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, req)
            }
        }, ContextCompat.getMainExecutor(context))
    }
}
