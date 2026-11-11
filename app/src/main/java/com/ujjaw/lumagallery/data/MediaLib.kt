package com.ujjaw.lumagallery.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.app.PendingIntent
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ujjaw.lumagallery.data.db.AppDb
import com.ujjaw.lumagallery.data.db.FavoriteEntity
import com.ujjaw.lumagallery.data.db.HiddenFolderEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MediaLib {

    var items by mutableStateOf<List<MediaItem>>(emptyList())
    var allItems by mutableStateOf<List<MediaItem>>(emptyList())
    var trashed by mutableStateOf<List<MediaItem>>(emptyList())
    var loaded by mutableStateOf(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun refresh(context: Context) {
        scope.launch { loadSync(context) }
    }

    suspend fun loadSync(context: Context) {
        val hiddenFolders = try {
            AppDb.db.hiddenDao().allOnce().map { it.bucketId }.toSet()
        } catch (e: Exception) {
            emptySet<Long>()
        }
        val hiddenItems = try {
            AppDb.db.hiddenItemDao().allOnce().map { norm(it.uri) }.toSet()
        } catch (e: Exception) {
            emptySet<String>()
        }
        allItems = queryMedia(context, null)
        items = allItems.filter { it.bucketId !in hiddenFolders && norm(it.uri) !in hiddenItems }
        trashed = if (Build.VERSION.SDK_INT >= 30) {
            queryMedia(context, "1")
        } else emptyList()
        android.util.Log.d(
            "MediaLib",
            "loaded ${allItems.size} items, ${trashed.size} trashed"
        )
        loaded = true
    }

    fun find(uri: String): MediaItem? = items.firstOrNull { norm(it.uri) == norm(uri) }

    /**
     * Older app versions stored generic "content://media/external/..." URIs in the DB
     * (hidden items, favorites). Items now carry volume-specific URIs, so normalize both
     * sides before comparing.
     */
    fun norm(uri: String): String =
        if (uri.startsWith("content://media/external/"))
            uri.replaceFirst("content://media/external/", "content://media/external_primary/")
        else uri

    fun albumsOf(list: List<MediaItem>): List<Album> {
        return list.groupBy { it.bucketId }
            .map { (id, group) ->
                val newest = group.first()
                Album(
                    bucketId = id,
                    name = group.first().bucketName,
                    coverUri = newest.uri,
                    coverIsVideo = newest.isVideo,
                    count = group.size,
                    lastDate = group.maxOf { it.dateTaken }
                )
            }
            .sortedByDescending { it.lastDate }
    }

    private fun queryMedia(context: Context, trashedValue: String?): List<MediaItem> {
        val out = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.Files.FileColumns.VOLUME_NAME
        )
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val selection = if (trashedValue == null) {
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (1,3)"
        } else {
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (1,3) AND ${MediaStore.MediaColumns.IS_TRASHED}=?"
        }
        val args = trashedValue?.let { arrayOf(it) }
        try {
            // Trashed items are hidden by default: MediaStore only returns is_trashed=1 rows
            // when the caller explicitly opts in with QUERY_ARG_MATCH_TRASHED = MATCH_INCLUDE.
            val res = if (trashedValue != null && Build.VERSION.SDK_INT >= 30) {
                val bundle = android.os.Bundle().apply {
                    putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
                    putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putStringArray(
                        android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                        args
                    )
                }
                context.contentResolver.query(collection, projection, bundle, null)
            } else {
                context.contentResolver.query(collection, projection, selection, args, null)
            }
            res?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val iType = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val iBucket = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
                val iBucketName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val iTaken = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
                val iAdded = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val iDur = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
                val iW = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val iH = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                val iVol = c.getColumnIndex(MediaStore.Files.FileColumns.VOLUME_NAME)
                while (c.moveToNext()) {
                    val type = c.getInt(iType)
                    val id = c.getLong(iId)
                    val taken = c.getLong(iTaken)
                    val added = c.getLong(iAdded)
                    val date = if (taken > 0) taken else added * 1000
                    val isVideo = type == 3
                    var name = c.getString(iName) ?: "IMG_$id"
                    if (trashedValue != null) {
                        name = name.replaceFirst(Regex("^\\.trashed-\\d+-"), "")
                    }
                    // Volume-specific URIs (content://media/external_primary/...) are required for
                    // MediaStore.createTrashRequest to actually trash instead of deleting
                    // (generic "external" URIs are broken: b/350540990).
                    val volume = if (iVol >= 0 && !c.isNull(iVol) && !c.getString(iVol).isNullOrBlank())
                        c.getString(iVol) else MediaStore.VOLUME_EXTERNAL
                    val base = if (isVideo) MediaStore.Video.Media.getContentUri(volume)
                    else MediaStore.Images.Media.getContentUri(volume)
                    out += MediaItem(
                        id = id,
                        uri = ContentUris.withAppendedId(base, id).toString(),
                        isVideo = isVideo,
                        name = name,
                        bucketId = c.getLong(iBucket),
                        bucketName = c.getString(iBucketName) ?: "Other",
                        dateTaken = date,
                        size = c.getLong(iSize),
                        durationMs = c.getLong(iDur),
                        width = c.getInt(iW),
                        height = c.getInt(iH)
                    )
                }
            }
        } catch (e: SecurityException) {
            // Permission not granted yet
        } catch (e: Exception) {
            // Ignore malformed rows
        }
        return out.sortedByDescending { it.dateTaken }
    }

    fun trashRequest(context: Context, uris: List<Uri>): PendingIntent? {
        if (uris.isEmpty()) return null
        return when {
            Build.VERSION.SDK_INT >= 30 ->
                MediaStore.createTrashRequest(context.contentResolver, uris, true)
            Build.VERSION.SDK_INT >= 29 ->
                MediaStore.createDeleteRequest(context.contentResolver, uris)
            else -> {
                uris.forEach { runCatching { context.contentResolver.delete(it, null, null) } }
                null
            }
        }
    }

    fun restoreRequest(context: Context, uris: List<Uri>): PendingIntent? {
        if (uris.isEmpty() || Build.VERSION.SDK_INT < 30) return null
        return MediaStore.createTrashRequest(context.contentResolver, uris, false)
    }

    fun deleteForeverRequest(context: Context, uris: List<Uri>): PendingIntent? {
        if (uris.isEmpty()) return null
        return when {
            Build.VERSION.SDK_INT >= 29 ->
                MediaStore.createDeleteRequest(context.contentResolver, uris)
            else -> {
                uris.forEach { runCatching { context.contentResolver.delete(it, null, null) } }
                null
            }
        }
    }

    fun saveEditedImage(context: Context, bitmap: Bitmap): Boolean {
        return try {
            val name = "Luma_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.jpg")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Luma Gallery")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values
                ) ?: return false
                context.contentResolver.openOutputStream(uri)?.use {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            } else {
                val dir = File(
                    java.io.File(
                        android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_PICTURES
                        ), "Luma Gallery"
                    ).apply { mkdirs() }, ""
                )
                val file = File(dir, "$name.jpg")
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.DATA, file.absolutePath)
                }
                context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun toggleFavorite(uri: String) {
        val dao = AppDb.db.favoriteDao()
        val key = norm(uri)
        if (dao.count(key) > 0) dao.remove(key) else dao.add(FavoriteEntity(key))
    }
}
