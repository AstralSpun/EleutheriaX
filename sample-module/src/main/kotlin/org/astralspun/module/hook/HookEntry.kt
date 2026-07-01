package org.astralspun.module.hook

import org.astralspun.eleutheriax.xposed.EleutheriaXModule
import org.astralspun.module.hook.items.HookTest

class HookEntry : EleutheriaXModule() {

    override fun onHook() = encase {
        loadApp("org.astralspun.sampleapp") {
            loadHooker(HookTest)
        }
    }
}
