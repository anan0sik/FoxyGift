package com.foxygift.pos

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FoxyGiftApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
