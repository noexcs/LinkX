package com.noexcs.indolent

import android.app.Application
import com.noexcs.indolent.logging.Lumberjack

class LinkXApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Lumberjack.init(cacheDir.resolve("logs"))
    }
}
