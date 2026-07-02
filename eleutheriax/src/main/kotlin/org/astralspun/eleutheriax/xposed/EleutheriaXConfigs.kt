package org.astralspun.eleutheriax.xposed

import org.astralspun.eleutheriax.dexkit.DexResolver

class EleutheriaXConfigs internal constructor() {

    var cachePassword: String = ""
        set(value) {
            field = value
            if (value.isEmpty()) {
                DexResolver.clearCachePassword()
            } else {
                DexResolver.setCachePassword(value)
            }
        }
}
