package org.astralspun.module.hook.base

import android.content.Context
import org.astralspun.eleutheriax.xposed.hooker.EleutheriaXBaseHooker

abstract class XBridge : EleutheriaXBaseHooker() {

    override fun onHook() {
        onAppLifecycle {
            onCreate {
                onHook(appContext!!, appClassLoader)
            }
        }
    }

    open fun onHook(ctx: Context, loader: ClassLoader) {}
}
