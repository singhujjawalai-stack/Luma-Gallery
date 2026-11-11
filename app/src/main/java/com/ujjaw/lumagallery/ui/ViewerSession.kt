package com.ujjaw.lumagallery.ui

import androidx.compose.runtime.mutableStateListOf
import com.ujjaw.lumagallery.data.MediaItem

/** Holds the photo list the viewer is paging through (set when opening from a grid). */
object ViewerSession {
    val items = mutableStateListOf<MediaItem>()
}
