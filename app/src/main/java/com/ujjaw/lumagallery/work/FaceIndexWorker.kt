package com.ujjaw.lumagallery.work

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.ujjaw.lumagallery.data.MediaLib
import com.ujjaw.lumagallery.data.db.AppDb
import com.ujjaw.lumagallery.data.db.FaceEntity
import com.ujjaw.lumagallery.data.db.PersonEntity
import com.ujjaw.lumagallery.util.averageHash
import com.ujjaw.lumagallery.util.decodeScaled
import com.ujjaw.lumagallery.util.hammingDistance
import kotlinx.coroutines.tasks.await

/**
 * On-device face indexing with improved clustering:
 *  1. Detects faces (min 15% of image side, so tiny/poster faces are ignored).
 *  2. Assigns a face to a person if it is close to ANY face already assigned to
 *     that person (min-linkage, not just a single centroid) — this keeps photos
 *     of the same person taken in very different conditions in one group.
 *  3. After the first pass, runs a merge pass (union-find over persons) that
 *     joins groups which are near-identical, fixing split clusters.
 * Everything runs locally — no network.
 */
class FaceIndexWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

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
        val already = db.faceDao().allOnce().map { MediaLib.norm(it.mediaUri) }.toSet()

        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(0.15f)
            .build()
        val detector = FaceDetection.getClient(options)

        // personId -> member hashes (for min-linkage matching)
        val members = HashMap<Long, MutableList<Long>>()
        val counts = HashMap<Long, Int>()

        try {
            for (m in images) {
                if (MediaLib.norm(m.uri) in already) continue

                val bmp = decodeScaled(ctx, Uri.parse(m.uri), 640) ?: continue
                try {
                    val faces = detector.process(InputImage.fromBitmap(bmp, 0)).await()
                    for (f in faces) {
                        val bb = f.boundingBox
                        val left = bb.left.coerceAtLeast(0)
                        val top = bb.top.coerceAtLeast(0)
                        val w = bb.width().coerceAtMost(bmp.width - left)
                        val h = bb.height().coerceAtMost(bmp.height - top)
                        if (w < 48 || h < 48) continue

                        val faceBmp = Bitmap.createBitmap(bmp, left, top, w, h)
                        val hash = averageHash(Bitmap.createScaledBitmap(faceBmp, 8, 8, true))

                        // min-linkage: distance to the closest member of every person
                        var bestId = -1L
                        var bestDist = Int.MAX_VALUE
                        for ((pid, hashes) in members) {
                            for (h2 in hashes) {
                                val d = hammingDistance(h2, hash)
                                if (d < bestDist) {
                                    bestDist = d
                                    bestId = pid
                                }
                            }
                        }

                        val personId: Long
                        if (bestId != -1L && bestDist <= 12) {
                            personId = bestId
                            counts[personId] = (counts[personId] ?: 0) + 1
                        } else {
                            personId = db.personDao().insert(
                                PersonEntity(
                                    name = "Person ${(members.size + 1)}",
                                    centroid = hash,
                                    count = 1,
                                    coverUri = m.uri
                                )
                            )
                            counts[personId] = 1
                        }
                        members.getOrPut(personId) { mutableListOf() }.add(hash)

                        db.faceDao().insertAll(
                            listOf(
                                FaceEntity(
                                    mediaUri = m.uri,
                                    personId = personId,
                                    hash = hash,
                                    l = left / bmp.width.toFloat(),
                                    t = top / bmp.height.toFloat(),
                                    r = (left + w) / bmp.width.toFloat(),
                                    b = (top + h) / bmp.height.toFloat()
                                )
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Skip unreadable images
                } finally {
                    bmp.recycle()
                }
            }
        } finally {
            detector.close()
        }

        // Persist updated counts
        for ((pid, c) in counts) {
            db.personDao().byId(pid)?.let { db.personDao().update(it.copy(count = c)) }
        }

        mergeSplitGroups(db)

        return Result.success()
    }

    /** Union-find pass that joins near-identical person groups. */
    private suspend fun mergeSplitGroups(db: com.ujjaw.lumagallery.data.db.AppDatabase) {
        val persons = db.personDao().allOnce()
        if (persons.size < 2) return
        val faces = db.faceDao().allOnce()
        val byPerson = faces.groupBy { it.personId }.mapValues { v -> v.value.map { it.hash } }

        val parent = HashMap<Long, Long>()
        persons.forEach { parent[it.id] = it.id }
        val size = persons.associate { it.id to it.count }.toMutableMap()

        fun find(x: Long): Long {
            var r = x
            while (parent[r] != r) {
                parent[r] = parent[parent[r]]!!
                r = parent[r]!!
            }
            return r
        }

        for (i in persons.indices) {
            for (j in i + 1 until persons.size) {
                val r1 = find(persons[i].id)
                val r2 = find(persons[j].id)
                if (r1 == r2) continue
                val h1 = byPerson[r1] ?: continue
                val h2 = byPerson[r2] ?: continue
                var minD = Int.MAX_VALUE
                outer@ for (a in h1) {
                    for (b in h2) {
                        val d = hammingDistance(a, b)
                        if (d < minD) {
                            minD = d
                            if (minD <= 10) break@outer
                        }
                    }
                }
                if (minD <= 10) {
                    val (small, big) = if ((size[r1] ?: 0) <= (size[r2] ?: 0)) r1 to r2 else r2 to r1
                    parent[small] = big
                    size[big] = (size[big] ?: 0) + (size[small] ?: 0)
                    db.faceDao().moveFaces(small, big)
                    db.personDao().delete(small)
                }
            }
        }
    }
}
