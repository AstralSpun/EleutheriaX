package org.astralspun.module.hook.items

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import org.astralspun.eleutheriax.dexkit.DexResolver
import org.astralspun.eleutheriax.dexkit.findMethod
import org.astralspun.eleutheriax.reflect.ReflectionUtils
import org.astralspun.module.hook.base.XBridge

object HookTest : XBridge() {

    override fun onHook(ctx: Context, loader: ClassLoader) {
        DexResolver.findClass {
            searchPackages("org.astralspun.sampleapp")
            usingStrings("binding", "menu")
        }.findMethod {
            methodName("onCreate")
            returnType(Void.TYPE)
            parameterTypes(Bundle::class.java)
            parameterCount(1)
        }.hook {
            after {
                Toast.makeText(ctx, "Test", Toast.LENGTH_SHORT).show()
            }
        }
        ReflectionUtils.findMethod {
            declaredClass = Snackbar::class.java
            methodName = "make"
            parameterTypes(
                ReflectionUtils.Ignore::class.java,
                CharSequence::class.java,
                Int::class.java
            )
            parameterCount = 3
            superClass()
        }.hook {
            before {
                val text = args[1] as CharSequence
                if (text.toString() == "Replace with your own action") {
                    args[1] = "Hook the text successfully!"
                }
            }
        }
    }
}
