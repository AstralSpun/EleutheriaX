package org.astralspun.eleutheriax.xposed.param.wrapper

import android.content.pm.ApplicationInfo
import org.astralspun.eleutheriax.xposed.HookEntryType
import org.astralspun.eleutheriax.xposed.HookStage
import org.astralspun.eleutheriax.xposed.param.PackageParam

internal class PackageParamWrapper internal constructor(
    var type: HookEntryType,
    var stage: HookStage,
    var packageName: String,
    var processName: String,
    var appClassLoader: ClassLoader,
    var defaultClassLoader: ClassLoader = appClassLoader,
    var appInfo: ApplicationInfo? = null,
    var isFirstPackage: Boolean = false,
    var isSystemServer: Boolean = false
) {

    internal val wrapperNameId get() = if (type == HookEntryType.SYSTEM) PackageParam.SYSTEM_PACKAGE_NAME else packageName
}
