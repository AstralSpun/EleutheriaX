package org.astralspun.eleutheriax.xposed.hooker

import org.astralspun.eleutheriax.log.logInnerD
import org.astralspun.eleutheriax.log.logInnerE
import org.astralspun.eleutheriax.xposed.param.PackageParam

abstract class EleutheriaXBaseHooker : PackageParam() {

    internal fun assignInstance(packageParam: PackageParam) {
        assign(packageParam.module, packageParam.wrapper ?: error("PackageParam wrapper is null"))
        runCatching {
            module.logInnerD("Load hooker ${this::class.java.name} in $packageName")
            onHook()
        }.onFailure {
            module.logInnerE("An exception occurred in $this", it)
        }
    }

    abstract fun onHook()
}
