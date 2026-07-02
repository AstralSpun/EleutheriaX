package org.astralspun.eleutheriax.dexkit

import org.astralspun.eleutheriax.EleutheriaX
import org.astralspun.eleutheriax.dexkit.cache.DexKitCache
import org.astralspun.eleutheriax.log.Elog
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

    fun setCachePassword(password: String) {
        resolvers.values.forEach { it.clearMemoryCache() }
        DexKitCache.setPassword(password)
    }

    fun clearCachePassword() {
        resolvers.values.forEach { it.clearMemoryCache() }
        DexKitCache.clearPassword()
    }

    internal fun setModuleApkPath(apkPath: String?) {
        if (DexKitCache.setModuleApkPath(apkPath)) {
            resolvers.values.forEach { it.clearMemoryCache() }
        }
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
            Elog.innerD("Create DexResolver session for ${packageParam.packageName}")
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
            val key = "class:$sign"
            return DexResult(sign, cached(
                key,
                read = { it.getClassList(key, classLoader) },
                write = { cache, value -> cache.putClassList(key, value) }
            ) {
                withBridge { bridge ->
                    bridge.findClass(query.buildFindClass() as FindClass).map { it.getInstance(classLoader) }
                }
            })
        }

        fun findClassesInClasses(classes: Collection<Class<*>>, query: DexClassQuery): DexResult<Class<*>> {
            val sign = "${classes.joinToString { it.name }}:${query.sign()}"
            if (classes.isEmpty()) return DexResult(sign, emptyList())
            val key = "classIn:$sign"
            return DexResult(sign, cached(
                key,
                read = { it.getClassList(key, classLoader) },
                write = { cache, value -> cache.putClassList(key, value) }
            ) {
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
            val key = "method:$sign"
            return DexResult(sign, cached(
                key,
                read = { it.getMethodList(key, classLoader) },
                write = { cache, value -> cache.putMethodList(key, value) }
            ) {
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
            val key = "methodIn:$sign"
            return DexResult(sign, cached(
                key,
                read = { it.getMethodList(key, classLoader) },
                write = { cache, value -> cache.putMethodList(key, value) }
            ) {
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
            val key = "constructor:$sign"
            return DexResult(sign, cached(
                key,
                read = { it.getConstructorList(key, classLoader) },
                write = { cache, value -> cache.putConstructorList(key, value) }
            ) {
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
            val key = "constructorIn:$sign"
            return DexResult(sign, cached(
                key,
                read = { it.getConstructorList(key, classLoader) },
                write = { cache, value -> cache.putConstructorList(key, value) }
            ) {
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
            val key = "field:$sign"
            return DexResult(sign, cached(
                key,
                read = { it.getFieldList(key, classLoader) },
                write = { cache, value -> cache.putFieldList(key, value) }
            ) {
                withBridge { bridge ->
                    bridge.findField(query.buildFindField() as FindField)
                        .map { it.getFieldInstance(classLoader).apply { isAccessible = true } }
                }
            })
        }

        fun findFieldsInClasses(classes: Collection<Class<*>>, query: DexFieldQuery): DexResult<Field> {
            val sign = "${classes.joinToString { it.name }}:${query.sign()}"
            if (classes.isEmpty()) return DexResult(sign, emptyList())
            val key = "fieldIn:$sign"
            return DexResult(sign, cached(
                key,
                read = { it.getFieldList(key, classLoader) },
                write = { cache, value -> cache.putFieldList(key, value) }
            ) {
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
            val key = "fieldInMethod:$sign"
            return DexResult(sign, cached(
                key,
                read = { it.getFieldList(key, classLoader) },
                write = { cache, value -> cache.putFieldList(key, value) }
            ) {
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
            val key = "batchClass:$sign"
            return cached(
                key,
                read = { it.getClassMap(key, classLoader) },
                write = { cache, value -> cache.putClassMap(key, value) }
            ) {
                withBridge { bridge ->
                    bridge.batchFindClassUsingStrings(query.buildClassQuery() as BatchFindClassUsingStrings).mapValues { (_, value) ->
                        value.map { it.getInstance(classLoader) }
                    }
                }
            }.mapValues { (key, value) -> DexResult("$sign:$key", value) }
        }

        fun batchFindMethodsUsingStrings(query: DexBatchUsingStringsQuery): Map<String, DexResult<Method>> {
            val sign = query.sign()
            val key = "batchMethod:$sign"
            return cached(
                key,
                read = { it.getMethodMap(key, classLoader) },
                write = { cache, value -> cache.putMethodMap(key, value) }
            ) {
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
            cacheProxy()?.clearCache() ?: DexKitCache.clearKnown()
        }

        fun clearMemoryCache() {
            cache.clear()
        }

        fun close() {
            synchronized(lock) {
                bridge?.close()
                bridge = null
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun <T> cached(
            key: String,
            read: (DexKitCache.CacheProxy) -> T?,
            write: (DexKitCache.CacheProxy, T) -> Unit,
            block: () -> T
        ): T {
            cache[key]?.also {
                Elog.innerD("DexKit memory cache hit [${key.debugKey()}]")
                return it as T
            }
            val cacheProxy = cacheProxy()
            cacheProxy?.let { read(it) }?.also {
                Elog.innerD("DexKit disk cache hit [${key.debugKey()}]")
                cache[key] = it as Any
                return it
            }
            Elog.innerD("DexKit query [${key.debugKey()}]")
            val value = block()
            cache[key] = value as Any
            cacheProxy?.also {
                write(it, value)
                Elog.innerD("DexKit disk cache write [${key.debugKey()}]")
            }
            return value
        }

        private fun String.debugKey() = if (length > 180) take(180) + "..." else this

        private fun cacheProxy(): DexKitCache.CacheProxy? =
            DexKitCache.get(EleutheriaX.appContext, packageName, apkPath)

        private fun <T> withBridge(block: (DexKitBridge) -> T): T = synchronized(lock) {
            loadLibrary()
            val current = bridge?.takeIf { it.isValid } ?: DexKitBridge.create(apkPath).also { bridge = it }
            runCatching { block(current) }.getOrElse {
                throw DexResolverException("DexResolver query failed in $packageName", it)
            }
        }
    }
}
