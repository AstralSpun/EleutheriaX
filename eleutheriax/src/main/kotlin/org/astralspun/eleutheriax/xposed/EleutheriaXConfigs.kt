package org.astralspun.eleutheriax.xposed

import org.astralspun.eleutheriax.dexkit.DexResolver
import org.astralspun.eleutheriax.log.Elog

class EleutheriaXConfigs internal constructor() {

    var cachePassword: String
        get() = currentCachePassword
        set(value) {
            currentCachePassword = value
            if (value.isEmpty()) {
                DexResolver.clearCachePassword()
            } else {
                DexResolver.setCachePassword(value)
            }
        }

    var isDebug: Boolean
        get() = Elog.isDebug
        set(value) {
            Elog.isDebug = value && Elog.Configs.isEnable
        }

    fun debugLog(initiate: Elog.Configs.() -> Unit) =
        Elog.Configs.apply(initiate).build()

    private companion object {
        var currentCachePassword = ""
    }
}
