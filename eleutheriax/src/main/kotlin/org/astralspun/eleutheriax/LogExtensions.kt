package org.astralspun.eleutheriax

import android.util.Log
import io.github.libxposed.api.XposedInterface

val XposedInterface.hasSystemCapability
    get() = getFrameworkProperties() and XposedInterface.PROP_CAP_SYSTEM != 0L

val XposedInterface.hasRemoteCapability
    get() = getFrameworkProperties() and XposedInterface.PROP_CAP_REMOTE != 0L

val XposedInterface.hasApiProtection
    get() = getFrameworkProperties() and XposedInterface.PROP_RT_API_PROTECTION != 0L

fun XposedInterface.logD(message: String, tag: String? = "EleutheriaX") =
    log(Log.DEBUG, tag, message)

fun XposedInterface.logI(message: String, tag: String? = "EleutheriaX") =
    log(Log.INFO, tag, message)

fun XposedInterface.logW(message: String, throwable: Throwable? = null, tag: String? = "EleutheriaX") =
    log(Log.WARN, tag, message, throwable)

fun XposedInterface.logE(message: String, throwable: Throwable? = null, tag: String? = "EleutheriaX") =
    log(Log.ERROR, tag, message, throwable)
