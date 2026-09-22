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
        // Record crashes in the field log before the default handler kills the process;
        // the foreground service is START_STICKY so Android restarts it afterwards.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { container.fieldLog.event("CRASH ${thread.name}: ${e::class.java.simpleName}: ${e.message?.take(200)}") }
            Thread.sleep(300) // give the log writer a moment to flush
            previous?.uncaughtException(thread, e)
        }
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
