package org.astralspun.eleutheriax

abstract class EleutheriaXBaseHooker : PackageParam() {

    internal fun assignInstance(packageParam: PackageParam) {
        assign(packageParam.module, packageParam.wrapper ?: error("PackageParam wrapper is null"))
        runCatching { onHook() }.onFailure {
            module.logE("An exception occurred in $this", it)
        }
    }

    abstract fun onHook()
}
