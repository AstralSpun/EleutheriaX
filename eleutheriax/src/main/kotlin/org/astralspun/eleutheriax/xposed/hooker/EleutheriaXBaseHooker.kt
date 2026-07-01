package org.astralspun.eleutheriax.xposed.hooker

import org.astralspun.eleutheriax.log.logE
import org.astralspun.eleutheriax.xposed.param.PackageParam

abstract class EleutheriaXBaseHooker : PackageParam() {

    internal fun assignInstance(packageParam: PackageParam) {
        assign(packageParam.module, packageParam.wrapper ?: error("PackageParam wrapper is null"))
        runCatching { onHook() }.onFailure {
            module.logE("An exception occurred in $this", it)
        }
    }

    abstract fun onHook()
}
