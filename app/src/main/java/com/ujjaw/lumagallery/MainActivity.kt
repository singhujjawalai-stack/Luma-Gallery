package com.ujjaw.lumagallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.ujjaw.lumagallery.data.db.AppDb
import com.ujjaw.lumagallery.ui.App
import com.ujjaw.lumagallery.ui.theme.LumaTheme
import com.ujjaw.lumagallery.work.IndexScheduler

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppDb.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            LumaTheme {
                App()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Keep content search and People up to date with newly added media.
        // Workers skip already-indexed items, so this is cheap when nothing is new.
        IndexScheduler.content(this)
        IndexScheduler.faces(this)
    }
}
