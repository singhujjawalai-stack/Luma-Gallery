package com.ujjaw.lumagallery.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object SettingsStore {
    private lateinit var prefs: android.content.SharedPreferences

    var slideshowMs by mutableStateOf(3000L)
        private set
    var gridColumns by mutableIntStateOf(4)
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("luma_settings", Context.MODE_PRIVATE)
        slideshowMs = prefs.getLong("slideshow_ms", 3000L)
        gridColumns = prefs.getInt("grid_columns", 4)
    }

    fun updateSlideshow(ms: Long) {
        slideshowMs = ms
        if (::prefs.isInitialized) prefs.edit().putLong("slideshow_ms", ms).apply()
    }

    fun updateGridColumns(n: Int) {
        gridColumns = n
        if (::prefs.isInitialized) prefs.edit().putInt("grid_columns", n).apply()
    }
}
