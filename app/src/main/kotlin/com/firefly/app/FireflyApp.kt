package com.firefly.app

import android.app.Application
import com.firefly.app.di.AppContainer

class FireflyApp : Application() {

    /** Single manual-DI container for the whole process. No Hilt/Dagger/Koin in v1. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
