package com.ujjaw.lumagallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ujjaw.lumagallery.data.db.AppDb
import com.ujjaw.lumagallery.ui.App
import com.ujjaw.lumagallery.ui.theme.LumaTheme
import com.ujjaw.lumagallery.work.ContentIndexWorker
import com.ujjaw.lumagallery.work.FaceIndexWorker

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
        enqueueIncrementalIndexing()
    }

    /**
     * Keep content search and People up to date with newly added media.
     * Both workers skip items that are already indexed, so this is cheap when
     * nothing is new. KEEP policy means a running/queued scan is never restarted.
     */
    private fun enqueueIncrementalIndexing() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()
        val wm = WorkManager.getInstance(applicationContext)
        wm.enqueueUniqueWork(
            "content_index",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ContentIndexWorker>()
                .setConstraints(constraints)
                .build()
        )
        wm.enqueueUniqueWork(
            "face_index",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<FaceIndexWorker>()
                .setConstraints(constraints)
                .build()
        )
    }
}
