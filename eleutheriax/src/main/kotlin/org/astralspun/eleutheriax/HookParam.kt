package org.astralspun.eleutheriax

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method

class HookParam internal constructor(
    private val module: XposedInterface,
    private val chain: XposedInterface.Chain,
    internal var argsArray: Array<Any?>
) {

    val member: Executable get() = chain.executable

    val method: Method
        get() = member as? Method ?: error("Current hooked member is not a Method")

    val constructor: Constructor<*>
        get() = member as? Constructor<*> ?: error("Current hooked member is not a Constructor")

    val instance: Any
        get() = chain.thisObject ?: error("HookParam instance got null. Is this a static member?")

    val instanceOrNull: Any? get() = chain.thisObject

    val args: Array<Any?> get() = argsArray

    var result: Any? = null

    var throwable: Throwable? = null

    val hasThrowable get() = throwable != null

    fun proceed(): Any? {
        result = chain.proceed(argsArray)
        return result
    }

    fun proceed(vararg args: Any?): Any? {
        argsArray = arrayOf(*args)
        return proceed()
    }

    fun callOriginal(vararg args: Any?): Any? {
        val callArgs = if (args.isEmpty()) argsArray else arrayOf(*args)
        return when (val executable = member) {
            is Method -> module.getInvoker(executable)
                .setType(XposedInterface.Invoker.Type.ORIGIN)
                .invoke(instanceOrNull, *callArgs)
            is Constructor<*> -> module.getInvoker(executable)
                .setType(XposedInterface.Invoker.Type.ORIGIN)
                .newInstance(*callArgs)
            else -> error("Unsupported executable $executable")
        }
    }

    inline fun <reified T> resultAs() = result as? T

    inline fun <reified T> instanceAs() =
        instance as? T ?: error("HookParam instance cannot cast to ${T::class.java.name}")

    fun resultTrue() {
        result = true
    }

    fun resultFalse() {
        result = false
    }

    fun resultNull() {
        result = null
    }
}
