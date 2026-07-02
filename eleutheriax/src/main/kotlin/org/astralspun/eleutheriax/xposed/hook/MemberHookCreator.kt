package org.astralspun.eleutheriax.xposed.hook

import io.github.libxposed.api.XposedInterface
import org.astralspun.eleutheriax.xposed.param.HookParam
import java.lang.reflect.Executable

class MemberHookCreator internal constructor(
    private val module: XposedInterface,
    private val member: Executable? = null
) {

    var priority: Int = XposedInterface.PRIORITY_DEFAULT

    var exceptionMode: XposedInterface.ExceptionMode = XposedInterface.ExceptionMode.DEFAULT

    var id: String? = null

    private var beforeCallback: (HookParam.() -> Unit)? = null

    private var afterCallback: (HookParam.() -> Unit)? = null

    private var replaceCallback: (HookParam.() -> Any?)? = null

    fun before(initiate: HookParam.() -> Unit) {
        beforeCallback = initiate
    }

    fun after(initiate: HookParam.() -> Unit) {
        afterCallback = initiate
    }

    fun replace(initiate: HookParam.() -> Any?) {
        replaceCallback = initiate
    }

    fun replaceTo(value: Any?) {
        replaceCallback = { value }
    }

    fun replaceToTrue() = replaceTo(true)

    fun replaceToFalse() = replaceTo(false)

    fun intercept() = replaceTo(null)

    internal fun createHooker() = XposedInterface.Hooker { chain ->
        replaceCallback?.let {
            return@Hooker it(HookParam(module, chain))
        }
        val param = HookParam(module, chain, chain.args.toTypedArray())
        beforeCallback?.invoke(param)
        param.proceed()
        afterCallback?.invoke(param)
        param.throwable?.let { throw it }
        param.result
    }

    internal fun build(): XposedInterface.HookHandle {
        val executable = member ?: error("Hook member is null")
        return module.hook(executable)
            .setPriority(priority)
            .setExceptionMode(exceptionMode)
            .setId(id)
            .intercept(createHooker())
    }
}
