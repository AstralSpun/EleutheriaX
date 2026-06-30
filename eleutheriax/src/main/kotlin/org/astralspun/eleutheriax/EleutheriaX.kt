package org.astralspun.eleutheriax

import android.annotation.SuppressLint
import android.app.Application

object EleutheriaX {

    private var currentApplication: Application? = null

    val appContext: Application?
        get() = currentApplication ?: findCurrentApplication()?.also { currentApplication = it }

    val requireAppContext: Application
        get() = appContext ?: error("Failed to got AppContext")

    internal fun refreshAppContext() {
        findCurrentApplication()?.also { currentApplication = it }
    }

    internal fun assignAppContext(application: Application) {
        currentApplication = application
    }

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    private fun findCurrentApplication(): Application? = runCatching {
        Class.forName("android.app.ActivityThread")
            .getDeclaredMethod("currentApplication")
            .apply { isAccessible = true }
            .invoke(null) as? Application
    }.getOrNull()
}
