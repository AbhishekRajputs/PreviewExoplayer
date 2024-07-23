package com.abhishek.exodownload

import android.app.Application
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadService

class MyApp:Application() {

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        try {
            DownloadService.start(this, MyDownloadService::class.java)
        } catch (e: IllegalStateException) {
            DownloadService.startForeground(this, MyDownloadService::class.java)
        }
    }
}