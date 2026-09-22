package com.firefly.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.firefly.app.di.AppContainer

class FireflyApp : Application() {

    /** Single manual-DI container for the whole process. No Hilt/Dagger/Koin in v1. */
    lateinit var container: AppContainer
        private set

    /** True while any Activity is started; used to decide between in-app banner and system notification. */
    @Volatile
    var inForeground: Boolean = false
        private set

    private var startedActivities = 0

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) { startedActivities++; inForeground = true }
            override fun onActivityStopped(activity: Activity) { startedActivities--; inForeground = startedActivities > 0 }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
