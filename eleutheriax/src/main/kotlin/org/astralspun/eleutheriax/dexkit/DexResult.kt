package org.astralspun.eleutheriax.dexkit

import java.lang.reflect.AccessibleObject

class DexResult<T> internal constructor(
    private val sign: String,
    values: List<T>
) : AbstractList<T>() {

    private val values = values.onEach {
        if (it is AccessibleObject) it.isAccessible = true
    }

    override val size: Int get() = values.size

    fun all(): List<T> = values

    override operator fun get(index: Int): T {
        if (values.isEmpty()) throw DexResolverException("can not find $sign")
        if (index !in values.indices) {
            throw DexResolverException("The resulting number is ${values.size} and the index is $index: $sign")
        }
        return values[index]
    }

    fun first(): T = get(0)

    fun firstOrNull(): T? = values.firstOrNull()

    fun last(): T {
        if (values.isEmpty()) throw DexResolverException("can not find $sign")
        return values.last()
    }

    fun lastOrNull(): T? = values.lastOrNull()
}

class DexResolverException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
