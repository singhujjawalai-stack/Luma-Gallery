package com.ujjaw.lumagallery.data

data class MediaItem(
    val id: Long,
    val uri: String,
    val isVideo: Boolean,
    val name: String,
    val bucketId: Long,
    val bucketName: String,
    val dateTaken: Long,
    val size: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int
)

data class Album(
    val bucketId: Long,
    val name: String,
    val coverUri: String,
    val coverIsVideo: Boolean,
    val count: Int,
    val lastDate: Long
)
