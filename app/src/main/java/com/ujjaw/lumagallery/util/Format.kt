package com.ujjaw.lumagallery.util

import com.ujjaw.lumagallery.data.MediaItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun dayLabel(ms: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = ms }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    fun same(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    return when {
        same(cal, today) -> "Today"
        same(cal, yesterday) -> "Yesterday"
        else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(ms))
    }
}

fun fullDate(ms: Long): String =
    SimpleDateFormat("EEE, d MMM yyyy  HH:mm", Locale.getDefault()).format(Date(ms))

fun formatDuration(ms: Long): String =
    String.format(Locale.US, "%d:%02d", ms / 60000, (ms / 1000) % 60)

fun formatSize(bytes: Long): String = when {
    bytes >= 1 shl 30 -> String.format(Locale.US, "%.1f GB", bytes.toDouble() / (1 shl 30))
    bytes >= 1 shl 20 -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1 shl 20))
    bytes >= 1 shl 10 -> String.format(Locale.US, "%.0f KB", bytes.toDouble() / (1 shl 10))
    else -> "$bytes B"
}

data class MemoryGroup(val title: String, val subtitle: String, val items: List<MediaItem>)

fun onThisDay(items: List<MediaItem>): List<MemoryGroup> {
    val now = Calendar.getInstance()
    return items.asSequence()
        .filter {
            val c = Calendar.getInstance().apply { timeInMillis = it.dateTaken }
            c.get(Calendar.YEAR) < now.get(Calendar.YEAR) &&
                c.get(Calendar.MONTH) == now.get(Calendar.MONTH) &&
                c.get(Calendar.DAY_OF_MONTH) == now.get(Calendar.DAY_OF_MONTH)
        }
        .groupBy {
            val c = Calendar.getInstance().apply { timeInMillis = it.dateTaken }
            c.get(Calendar.YEAR)
        }
        .map { (year, list) ->
            MemoryGroup("${now.get(Calendar.YEAR) - year} years ago", "${list.size} photos", list)
        }
        .sortedByDescending { it.items.firstOrNull()?.dateTaken ?: 0L }
}

fun monthMemories(items: List<MediaItem>): List<MemoryGroup> {
    return items.asSequence()
        .groupBy {
            val c = Calendar.getInstance().apply { timeInMillis = it.dateTaken }
            c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH)
        }
        .filter { it.value.size >= 6 }
        .map { (_, list) ->
            val fmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            val title = fmt.format(Date(list.first().dateTaken))
            MemoryGroup(title, "${list.size} photos", list)
        }
        .sortedByDescending { it.items.firstOrNull()?.dateTaken ?: 0L }
        .take(6)
}
