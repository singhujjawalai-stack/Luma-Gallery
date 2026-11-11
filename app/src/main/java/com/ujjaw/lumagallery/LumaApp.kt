package com.ujjaw.lumagallery

import android.app.Application
import com.ujjaw.lumagallery.data.SettingsStore
import com.ujjaw.lumagallery.data.db.AppDb

class LumaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppDb.init(this)
        SettingsStore.init(this)
    }
}
