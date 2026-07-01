package org.astralspun.eleutheriax

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

fun String.toClass(loader: ClassLoader? = null): Class<*> =
    ReflectionUtils.findClass(this, loader)

fun String.toClassOrNull(loader: ClassLoader? = null): Class<*>? =
    runCatching { toClass(loader) }.getOrNull()

fun PackageParam.findClass(name: String): Class<*> = name.toClass(appClassLoader)

fun PackageParam.findClassOrNull(name: String): Class<*>? = name.toClassOrNull(appClassLoader)

fun Class<*>.method(name: String, vararg parameterTypes: Class<*>): Method =
    ReflectionUtils.findMethod {
        declaredClass = this@method
        methodName = name
        parameterTypes(*parameterTypes)
    }

fun Class<*>.methodOrNull(name: String, vararg parameterTypes: Class<*>): Method? =
    runCatching { method(name, *parameterTypes) }.getOrNull()

fun Class<*>.constructor(vararg parameterTypes: Class<*>): Constructor<*> =
    ReflectionUtils.findConstructor {
        declaredClass = this@constructor
        parameterTypes(*parameterTypes)
    }

fun Class<*>.constructorOrNull(vararg parameterTypes: Class<*>): Constructor<*>? =
    runCatching { constructor(*parameterTypes) }.getOrNull()

fun Class<*>.field(name: String): Field =
    ReflectionUtils.findField {
        declaredClass = this@field
        fieldName = name
    }

fun Class<*>.fieldOrNull(name: String): Field? =
    runCatching { field(name) }.getOrNull()
