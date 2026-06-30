package org.astralspun.eleutheriax

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

fun String.toClass(loader: ClassLoader? = null): Class<*> =
    Class.forName(this, false, loader ?: ClassLoader.getSystemClassLoader())

fun String.toClassOrNull(loader: ClassLoader? = null): Class<*>? =
    runCatching { toClass(loader) }.getOrNull()

fun PackageParam.findClass(name: String): Class<*> = name.toClass(appClassLoader)

fun PackageParam.findClassOrNull(name: String): Class<*>? = name.toClassOrNull(appClassLoader)

fun Class<*>.method(name: String, vararg parameterTypes: Class<*>): Method =
    getDeclaredMethod(name, *parameterTypes).apply { isAccessible = true }

fun Class<*>.methodOrNull(name: String, vararg parameterTypes: Class<*>): Method? =
    runCatching { method(name, *parameterTypes) }.getOrNull()

fun Class<*>.constructor(vararg parameterTypes: Class<*>): Constructor<*> =
    getDeclaredConstructor(*parameterTypes).apply { isAccessible = true }

fun Class<*>.constructorOrNull(vararg parameterTypes: Class<*>): Constructor<*>? =
    runCatching { constructor(*parameterTypes) }.getOrNull()

fun Class<*>.field(name: String): Field =
    getDeclaredField(name).apply { isAccessible = true }

fun Class<*>.fieldOrNull(name: String): Field? =
    runCatching { field(name) }.getOrNull()
