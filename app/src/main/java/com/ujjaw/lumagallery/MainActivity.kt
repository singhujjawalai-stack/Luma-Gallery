package com.ujjaw.lumagallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.ujjaw.lumagallery.data.db.AppDb
import com.ujjaw.lumagallery.ui.App
import com.ujjaw.lumagallery.ui.theme.LumaTheme

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
}
