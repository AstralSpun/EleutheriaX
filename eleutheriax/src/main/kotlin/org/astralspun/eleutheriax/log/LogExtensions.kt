package org.astralspun.eleutheriax.log

import android.util.Log
import io.github.libxposed.api.XposedInterface

val XposedInterface.hasSystemCapability
    get() = frameworkProperties and XposedInterface.PROP_CAP_SYSTEM != 0L

val XposedInterface.hasRemoteCapability
    get() = frameworkProperties and XposedInterface.PROP_CAP_REMOTE != 0L

val XposedInterface.hasApiProtection
    get() = frameworkProperties and XposedInterface.PROP_RT_API_PROTECTION != 0L

fun XposedInterface.logD(message: String, tag: String? = Elog.Configs.tag) =
    Elog.log(this, Log.DEBUG, message, tag = tag)

internal fun XposedInterface.logInnerD(message: String, tag: String? = Elog.Configs.tag) =
    Elog.inner(this, Log.DEBUG, message, tag = tag)

fun XposedInterface.logI(message: String, tag: String? = Elog.Configs.tag) =
    Elog.log(this, Log.INFO, message, tag = tag)

internal fun XposedInterface.logInnerI(message: String, tag: String? = Elog.Configs.tag) =
    Elog.inner(this, Log.INFO, message, tag = tag)

fun XposedInterface.logW(message: String, throwable: Throwable? = null, tag: String? = Elog.Configs.tag) =
    Elog.log(this, Log.WARN, message, throwable, tag)

internal fun XposedInterface.logInnerW(message: String, throwable: Throwable? = null, tag: String? = Elog.Configs.tag) =
    Elog.inner(this, Log.WARN, message, throwable, tag)

fun XposedInterface.logE(message: String, throwable: Throwable? = null, tag: String? = Elog.Configs.tag) =
    Elog.log(this, Log.ERROR, message, throwable, tag)

internal fun XposedInterface.logInnerE(message: String, throwable: Throwable? = null, tag: String? = Elog.Configs.tag) =
    Elog.inner(this, Log.ERROR, message, throwable, tag)
