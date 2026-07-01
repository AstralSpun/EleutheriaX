package org.astralspun.eleutheriax.dexkit

import org.astralspun.eleutheriax.xposed.param.PackageParam
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.BatchFindClassUsingStrings
import org.luckypray.dexkit.query.BatchFindMethodUsingStrings
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindField
import org.luckypray.dexkit.query.FindMethod
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object DexResolver {

    private val isLibraryLoaded = AtomicBoolean(false)
    private val packageParamContext = ThreadLocal<PackageParam?>()
    private val defaultSessionKeys = ConcurrentHashMap<String, String>()
    private val resolvers = ConcurrentHashMap<String, Session>()

    fun findClass(init: DexClassQuery.() -> Unit): Class<*> =
        findClasses(DexClassQuery().apply(init).apply { findFirst = true }).first()

    fun findClassOrNull(init: DexClassQuery.() -> Unit): Class<*>? =
        findClasses(DexClassQuery().apply(init).apply { findFirst = true }).firstOrNull()

    fun findClasses(init: DexClassQuery.() -> Unit): DexResult<Class<*>> =
        current().findClasses(DexClassQuery().apply(init))

    private fun findClasses(query: DexClassQuery): DexResult<Class<*>> =
        current().findClasses(query)

    fun findMethod(init: DexMethodQuery.() -> Unit): Method =
        findMethods(DexMethodQuery().apply(init).apply { findFirst = true }).first()

    fun findMethodOrNull(init: DexMethodQuery.() -> Unit): Method? =
        findMethods(DexMethodQuery().apply(init).apply { findFirst = true }).firstOrNull()

    fun findMethods(init: DexMethodQuery.() -> Unit): DexResult<Method> =
        current().findMethods(DexMethodQuery().apply(init))

    private fun findMethods(query: DexMethodQuery): DexResult<Method> =
        current().findMethods(query)

    fun findConstructor(init: DexMethodQuery.() -> Unit): Constructor<*> =
        findConstructors(DexMethodQuery().apply(init).apply { findFirst = true }).first()

    fun findConstructorOrNull(init: DexMethodQuery.() -> Unit): Constructor<*>? =
        findConstructors(DexMethodQuery().apply(init).apply { findFirst = true }).firstOrNull()

    fun findConstructors(init: DexMethodQuery.() -> Unit): DexResult<Constructor<*>> =
        current().findConstructors(DexMethodQuery().apply(init))

    private fun findConstructors(query: DexMethodQuery): DexResult<Constructor<*>> =
        current().findConstructors(query)

    fun findField(init: DexFieldQuery.() -> Unit): Field =
        findFields(DexFieldQuery().apply(init).apply { findFirst = true }).first()

    fun findFieldOrNull(init: DexFieldQuery.() -> Unit): Field? =
        findFields(DexFieldQuery().apply(init).apply { findFirst = true }).firstOrNull()

    fun findFields(init: DexFieldQuery.() -> Unit): DexResult<Field> =
        current().findFields(DexFieldQuery().apply(init))

    private fun findFields(query: DexFieldQuery): DexResult<Field> =
        current().findFields(query)

    internal fun findMethodsInClasses(classes: Collection<Class<*>>, init: DexMethodQuery.() -> Unit): DexResult<Method> =
        current().findMethodsInClasses(classes, DexMethodQuery().apply(init))

    internal fun findClassesInClasses(classes: Collection<Class<*>>, init: DexClassQuery.() -> Unit): DexResult<Class<*>> =
        current().findClassesInClasses(classes, DexClassQuery().apply(init))

    internal fun findConstructorsInClasses(classes: Collection<Class<*>>, init: DexMethodQuery.() -> Unit): DexResult<Constructor<*>> =
        current().findConstructorsInClasses(classes, DexMethodQuery().apply(init))

    internal fun findFieldsInClasses(classes: Collection<Class<*>>, init: DexFieldQuery.() -> Unit): DexResult<Field> =
        current().findFieldsInClasses(classes, DexFieldQuery().apply(init))

    internal fun findFieldsInMethods(methods: Collection<Method>, usingType: DexUsingType, init: DexFieldQuery.() -> Unit): DexResult<Field> =
        current().findFieldsInMethods(methods, usingType, DexFieldQuery().apply(init))

    fun batchFindClassesUsingStrings(init: DexBatchUsingStringsQuery.() -> Unit): Map<String, DexResult<Class<*>>> =
        current().batchFindClassesUsingStrings(DexBatchUsingStringsQuery().apply(init))

    fun batchFindMethodsUsingStrings(init: DexBatchUsingStringsQuery.() -> Unit): Map<String, DexResult<Method>> =
        current().batchFindMethodsUsingStrings(DexBatchUsingStringsQuery().apply(init))

    fun clearCache() {
        current().clearCache()
    }

    fun close() {
        current().close()
    }

    fun closeAll() {
        resolvers.values.forEach { it.close() }
        resolvers.clear()
        defaultSessionKeys.clear()
    }

    internal fun <T> withPackageParam(packageParam: PackageParam, block: () -> T): T {
        val last = packageParamContext.get()
        packageParamContext.set(packageParam)
        packageParam.sessionKeyOrNull()?.also {
            defaultSessionKeys[packageParam.processName] = it
            getOrCreateSession(packageParam, it)
        }
        return try {
            block()
        } finally {
            if (last == null) {
                packageParamContext.remove()
            } else {
                packageParamContext.set(last)
            }
        }
    }

    private fun current(): Session {
        val packageParam = packageParamContext.get()
        if (packageParam == null) {
            if (resolvers.size == 1) return resolvers.values.first()
            defaultSessionKeys.values.distinct().singleOrNull()?.also { key ->
                resolvers[key]?.also { return it }
            }
            throw DexResolverException("DexResolver must be called after PackageParam scope is initialized")
        }
        val key = packageParam.sessionKeyOrNull()
            ?: throw DexResolverException("DexResolver requires package scope ApplicationInfo.sourceDir")
        defaultSessionKeys[packageParam.processName] = key
        return getOrCreateSession(packageParam, key)
    }

    private fun PackageParam.sessionKeyOrNull(): String? {
        if (isSystemServer) {
            throw DexResolverException("DexResolver does not support system server scope")
        }
        val apkPath = appInfo?.sourceDir ?: return null
        return "$packageName:$apkPath"
    }

    private fun getOrCreateSession(packageParam: PackageParam, key: String): Session =
        resolvers.getOrPut(key) {
            Session(
                packageName = packageParam.packageName,
                apkPath = packageParam.appInfo?.sourceDir
                    ?: throw DexResolverException("DexResolver requires package scope ApplicationInfo.sourceDir"),
                classLoader = packageParam.appClassLoader
            )
        }

    private fun loadLibrary() {
        if (isLibraryLoaded.getAndSet(true)) return
        runCatching { System.loadLibrary("dexkit") }.onFailure {
            isLibraryLoaded.set(false)
            throw DexResolverException("Failed to load dexkit native library", it)
        }
    }

    private class Session(
        private val packageName: String,
        private val apkPath: String,
        private val classLoader: ClassLoader
    ) {
        private val cache = ConcurrentHashMap<String, Any>()
        private val lock = Any()
        private var bridge: DexKitBridge? = null

        fun findClasses(query: DexClassQuery): DexResult<Class<*>> {
            val sign = query.sign()
            return DexResult(sign, cached("class:$sign") {
                withBridge { bridge ->
                    bridge.findClass(query.buildFindClass() as FindClass).map { it.getInstance(classLoader) }
                }
            })
        }

        fun findClassesInClasses(classes: Collection<Class<*>>, query: DexClassQuery): DexResult<Class<*>> {
            val sign = "${classes.joinToString { it.name }}:${query.sign()}"
            if (classes.isEmpty()) return DexResult(sign, emptyList())
            return DexResult(sign, cached("classIn:$sign") {
                withBridge { bridge ->
                    (query.buildFindClass() as FindClass).also {
                        it.searchIn(classes.mapNotNull(bridge::getClassData))
                    }.let(bridge::findClass)
                        .map { it.getInstance(classLoader) }
                }
            })
        }

        fun findMethods(query: DexMethodQuery): DexResult<Method> {
            val sign = query.sign()
            return DexResult(sign, cached("method:$sign") {
                withBridge { bridge ->
                    bridge.findMethod(query.buildFindMethod() as FindMethod)
                        .filter { it.isMethod }
                        .map { it.getMethodInstance(classLoader).apply { isAccessible = true } }
                }
            })
        }

        fun findMethodsInClasses(classes: Collection<Class<*>>, query: DexMethodQuery): DexResult<Method> {
            val sign = "${classes.joinToString { it.name }}:${query.sign()}"
            if (classes.isEmpty()) return DexResult(sign, emptyList())
            return DexResult(sign, cached("methodIn:$sign") {
                withBridge { bridge ->
                    (query.buildFindMethod() as FindMethod).also {
                        it.searchInClass(classes.mapNotNull(bridge::getClassData))
                    }.let(bridge::findMethod)
                        .filter { it.isMethod }
                        .map { it.getMethodInstance(classLoader).apply { isAccessible = true } }
                }
            })
        }

        fun findConstructors(query: DexMethodQuery): DexResult<Constructor<*>> {
            val sign = query.sign()
            return DexResult(sign, cached("constructor:$sign") {
                withBridge { bridge ->
                    bridge.findMethod(query.buildFindMethod() as FindMethod)
                        .filter { it.isConstructor }
                        .map { it.getConstructorInstance(classLoader).apply { isAccessible = true } }
                }
            })
        }

        fun findConstructorsInClasses(classes: Collection<Class<*>>, query: DexMethodQuery): DexResult<Constructor<*>> {
            val sign = "${classes.joinToString { it.name }}:${query.sign()}"
            if (classes.isEmpty()) return DexResult(sign, emptyList())
            return DexResult(sign, cached("constructorIn:$sign") {
                withBridge { bridge ->
                    (query.buildFindMethod() as FindMethod).also {
                        it.searchInClass(classes.mapNotNull(bridge::getClassData))
                    }.let(bridge::findMethod)
                        .filter { it.isConstructor }
                        .map { it.getConstructorInstance(classLoader).apply { isAccessible = true } }
                }
            })
        }

        fun findFields(query: DexFieldQuery): DexResult<Field> {
            val sign = query.sign()
            return DexResult(sign, cached("field:$sign") {
                withBridge { bridge ->
                    bridge.findField(query.buildFindField() as FindField)
                        .map { it.getFieldInstance(classLoader).apply { isAccessible = true } }
                }
            })
        }

        fun findFieldsInClasses(classes: Collection<Class<*>>, query: DexFieldQuery): DexResult<Field> {
            val sign = "${classes.joinToString { it.name }}:${query.sign()}"
            if (classes.isEmpty()) return DexResult(sign, emptyList())
            return DexResult(sign, cached("fieldIn:$sign") {
                withBridge { bridge ->
                    (query.buildFindField() as FindField).also {
                        it.searchInClass(classes.mapNotNull(bridge::getClassData))
                    }.let(bridge::findField)
                        .map { it.getFieldInstance(classLoader).apply { isAccessible = true } }
                }
            })
        }

        fun findFieldsInMethods(methods: Collection<Method>, usingType: DexUsingType, query: DexFieldQuery): DexResult<Field> {
            val sign = "${methods.joinToString { it.toString() }}:$usingType:${query.sign()}"
            if (methods.isEmpty()) return DexResult(sign, emptyList())
            return DexResult(sign, cached("fieldInMethod:$sign") {
                withBridge { bridge ->
                    (query.buildFindField() as FindField).also {
                        it.searchInField(
                            methods.mapNotNull(bridge::getMethodData)
                                .flatMap { method ->
                                    method.usingFields.filter { field ->
                                        usingType == DexUsingType.Any ||
                                            (usingType == DexUsingType.Read && field.usingType.isRead()) ||
                                            (usingType == DexUsingType.Write && field.usingType.isWrite())
                                    }.map { field -> field.field }
                                }
                                .distinct()
                        )
                    }.let(bridge::findField)
                        .map { it.getFieldInstance(classLoader).apply { isAccessible = true } }
                }
            })
        }

        fun batchFindClassesUsingStrings(query: DexBatchUsingStringsQuery): Map<String, DexResult<Class<*>>> {
            val sign = query.sign()
            return cached("batchClass:$sign") {
                withBridge { bridge ->
                    bridge.batchFindClassUsingStrings(query.buildClassQuery() as BatchFindClassUsingStrings).mapValues { (_, value) ->
                        value.map { it.getInstance(classLoader) }
                    }
                }
            }.mapValues { (key, value) -> DexResult("$sign:$key", value) }
        }

        fun batchFindMethodsUsingStrings(query: DexBatchUsingStringsQuery): Map<String, DexResult<Method>> {
            val sign = query.sign()
            return cached("batchMethod:$sign") {
                withBridge { bridge ->
                    bridge.batchFindMethodUsingStrings(query.buildMethodQuery() as BatchFindMethodUsingStrings).mapValues { (_, value) ->
                        value.filter { it.isMethod }
                            .map { it.getMethodInstance(classLoader).apply { isAccessible = true } }
                    }
                }
            }.mapValues { (key, value) -> DexResult("$sign:$key", value) }
        }

        fun clearCache() {
            cache.clear()
        }

        fun close() {
            synchronized(lock) {
                bridge?.close()
                bridge = null
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun <T> cached(key: String, block: () -> T): T =
            cache.computeIfAbsent(key) { block() as Any } as T

        private fun <T> withBridge(block: (DexKitBridge) -> T): T = synchronized(lock) {
            loadLibrary()
            val current = bridge?.takeIf { it.isValid } ?: DexKitBridge.create(apkPath).also { bridge = it }
            runCatching { block(current) }.getOrElse {
                throw DexResolverException("DexResolver query failed in $packageName", it)
            }
        }
    }
}
