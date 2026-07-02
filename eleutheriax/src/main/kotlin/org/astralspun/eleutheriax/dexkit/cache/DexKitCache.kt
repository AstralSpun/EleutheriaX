package org.astralspun.eleutheriax.dexkit.cache

import android.content.Context
import android.os.Build
import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import io.fastkv.FastKV
import io.fastkv.interfaces.FastCipher
import org.astralspun.eleutheriax.reflect.ReflectionUtils
import java.io.File
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

internal object DexKitCache {

    private const val STORE_DIR = "EleutheriaX"
    private const val CACHE_ID = "DexKitCache"
    private const val VERSION_KEY = "version"
    private const val MAX_KEY_LENGTH = 128
    private const val PREFIX_LENGTH = 64
    private const val SUFFIX_LENGTH = 64

    private val proxies = ConcurrentHashMap<String, CacheProxy>()
    private var cachePassword: String = ""
    private var moduleApkPath: String = ""

    fun get(context: Context?, packageName: String, apkPath: String): CacheProxy? {
        val appContext = context?.applicationContext ?: context ?: return null
        val storePath = File(appContext.filesDir, STORE_DIR).absolutePath
        val password = cachePassword
        val key = "$storePath:${secureHash(password)}"
        return proxies.getOrPut(key) { CacheProxy(storePath, password) }
            .also { it.checkCacheExpired(appContext, packageName, apkPath) }
    }

    @Synchronized
    fun setPassword(password: String) {
        if (cachePassword == password) return
        closeKnown()
        proxies.clear()
        cachePassword = password
    }

    @Synchronized
    fun clearPassword() {
        setPassword("")
    }

    fun clearKnown() {
        proxies.values.forEach { it.clearCache() }
    }

    fun setModuleApkPath(apkPath: String?): Boolean {
        val path = apkPath.orEmpty()
        if (moduleApkPath == path) return false
        moduleApkPath = path
        return true
    }

    private fun closeKnown() {
        proxies.values.forEach { it.close() }
    }

    private fun buildFlag(context: Context, packageName: String, apkPath: String): String {
        File(apkPath).lastModified().takeIf { it > 0L }?.also {
            return "buildTime:$it"
        }
        return runCatching {
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            "version:${packageInfo.versionName}_$versionCode"
        }.getOrDefault("source:$apkPath")
    }

    private fun buildCacheFlag(context: Context, packageName: String, apkPath: String): String =
        "target:${buildFlag(context, packageName, apkPath)};module:${buildModuleFlag()}"

    private fun buildModuleFlag(): String {
        val apkPath = moduleApkPath
        File(apkPath).lastModified().takeIf { it > 0L }?.also {
            return "buildTime:$it"
        }
        return "source:$apkPath"
    }

    private fun compressKey(key: String): String {
        if (key.length <= MAX_KEY_LENGTH) return key
        val prefix = key.substring(0, min(PREFIX_LENGTH, key.length))
        val suffix = key.substring(key.length - min(SUFFIX_LENGTH, key.length))
        return prefix + suffix + secureHash(key)
    }

    private fun secureHash(key: String): String {
        var hash1 = 0L
        var hash2 = 0L
        key.forEach {
            val char = it.code.toLong()
            hash1 = (((hash1 shl 5) - hash1) + char) and 0xFFFFFFFFL
            hash2 = (((hash2 shl 7) - hash2) + (char shl 1)) and 0xFFFFFFFFL
        }
        return String.format("%012x", (hash1 xor hash2) and 0xFFFFFFFFFFFFL)
    }

    internal class CacheProxy internal constructor(storePath: String, password: String) {

        private val kv = if (password.isEmpty()) {
            FastKV.Builder(storePath, CACHE_ID).build()
        } else {
            FastKV.Builder(storePath, CACHE_ID).cipher(AesGcmCipher(password)).build()
        }

        @Synchronized
        fun checkCacheExpired(context: Context, packageName: String, apkPath: String) {
            val flag = buildCacheFlag(context, packageName, apkPath)
            if (kv.getString(VERSION_KEY, "") == flag) return
            clearCache()
            kv.putString(VERSION_KEY, flag)
        }

        @Synchronized
        fun clearCache() {
            kv.clear()
        }

        @Synchronized
        fun close() {
            kv.close()
        }

        @Synchronized
        fun putClassList(key: String, value: List<Class<*>>) {
            putStringList(key, value.map { encodeClass(it) })
        }

        @Synchronized
        fun getClassList(key: String, loader: ClassLoader): List<Class<*>>? =
            getStringList(key)?.restoreList(key) { decodeClass(it, loader) }

        @Synchronized
        fun putMethodList(key: String, value: List<Method>) {
            putStringList(key, value.map { encodeMethod(it) })
        }

        @Synchronized
        fun getMethodList(key: String, loader: ClassLoader): List<Method>? =
            getStringList(key)?.restoreList(key) { decodeMethod(it, loader) }

        @Synchronized
        fun putConstructorList(key: String, value: List<Constructor<*>>) {
            putStringList(key, value.map { encodeConstructor(it) })
        }

        @Synchronized
        fun getConstructorList(key: String, loader: ClassLoader): List<Constructor<*>>? =
            getStringList(key)?.restoreList(key) { decodeConstructor(it, loader) }

        @Synchronized
        fun putFieldList(key: String, value: List<Field>) {
            putStringList(key, value.map { encodeField(it) })
        }

        @Synchronized
        fun getFieldList(key: String, loader: ClassLoader): List<Field>? =
            getStringList(key)?.restoreList(key) { decodeField(it, loader) }

        @Synchronized
        fun putClassMap(key: String, value: Map<String, List<Class<*>>>) {
            putStringList(key, value.map { (group, classes) ->
                encodeGroup(group, classes.map { encodeClass(it) })
            })
        }

        @Synchronized
        fun getClassMap(key: String, loader: ClassLoader): Map<String, List<Class<*>>>? =
            getStringList(key)?.restoreMap(key) { decodeClass(it, loader) }

        @Synchronized
        fun putMethodMap(key: String, value: Map<String, List<Method>>) {
            putStringList(key, value.map { (group, methods) ->
                encodeGroup(group, methods.map { encodeMethod(it) })
            })
        }

        @Synchronized
        fun getMethodMap(key: String, loader: ClassLoader): Map<String, List<Method>>? =
            getStringList(key)?.restoreMap(key) { decodeMethod(it, loader) }

        private fun putStringList(key: String, value: List<String>) {
            kv.putString(compressKey(key), JSON.toJSONString(value))
        }

        private fun getStringList(key: String): List<String>? {
            val fixKey = compressKey(key)
            if (kv.contains(fixKey).not()) return null
            return runCatching {
                JSON.parseArray(kv.getString(fixKey, "[]") ?: "[]", String::class.java)
            }.getOrElse {
                kv.remove(fixKey)
                null
            }
        }

        private inline fun <T> List<String>.restoreList(key: String, block: (String) -> T): List<T>? =
            runCatching { map(block) }.getOrElse {
                kv.remove(compressKey(key))
                null
            }

        private inline fun <T> List<String>.restoreMap(key: String, block: (String) -> T): Map<String, List<T>>? =
            runCatching {
                associate { raw ->
                    val item = JSON.parseObject(raw)
                    item.string("Key") to item.array("Values").toStringList().map(block)
                }
            }.getOrElse {
                kv.remove(compressKey(key))
                null
            }

        private fun encodeClass(clazz: Class<*>): String =
            JSON.toJSONString(JSONObject().apply {
                put("ClassName", clazz.name)
            })

        private fun decodeClass(value: String, loader: ClassLoader): Class<*> =
            ReflectionUtils.findClass(JSON.parseObject(value).string("ClassName"), loader)

        private fun encodeMethod(method: Method): String =
            JSON.toJSONString(JSONObject().apply {
                put("DeclareClass", method.declaringClass.name)
                put("MethodName", method.name)
                put("Params", method.parameterTypes.map { it.name })
                put("ReturnType", method.returnType.name)
            })

        private fun decodeMethod(value: String, loader: ClassLoader): Method {
            val item = JSON.parseObject(value)
            val declaredClass = ReflectionUtils.findClass(item.string("DeclareClass"), loader)
            val params = item.array("Params").toClassArray(loader)
            val returnType = ReflectionUtils.findClass(item.string("ReturnType"), loader)
            return declaredClass.getDeclaredMethod(item.string("MethodName"), *params).also {
                if (it.returnType != returnType) throw NoSuchMethodException(value)
                it.isAccessible = true
            }
        }

        private fun encodeConstructor(constructor: Constructor<*>): String =
            JSON.toJSONString(JSONObject().apply {
                put("DeclareClass", constructor.declaringClass.name)
                put("Params", constructor.parameterTypes.map { it.name })
            })

        private fun decodeConstructor(value: String, loader: ClassLoader): Constructor<*> {
            val item = JSON.parseObject(value)
            val declaredClass = ReflectionUtils.findClass(item.string("DeclareClass"), loader)
            return declaredClass.getDeclaredConstructor(*item.array("Params").toClassArray(loader)).also {
                it.isAccessible = true
            }
        }

        private fun encodeField(field: Field): String =
            JSON.toJSONString(JSONObject().apply {
                put("DeclareClass", field.declaringClass.name)
                put("FieldName", field.name)
                put("FieldType", field.type.name)
            })

        private fun decodeField(value: String, loader: ClassLoader): Field {
            val item = JSON.parseObject(value)
            val declaredClass = ReflectionUtils.findClass(item.string("DeclareClass"), loader)
            val fieldType = ReflectionUtils.findClass(item.string("FieldType"), loader)
            return declaredClass.getDeclaredField(item.string("FieldName")).also {
                if (it.type != fieldType) throw NoSuchFieldException(value)
                it.isAccessible = true
            }
        }

        private fun encodeGroup(key: String, values: List<String>): String =
            JSON.toJSONString(JSONObject().apply {
                put("Key", key)
                put("Values", values)
            })

        private fun JSONObject.string(key: String): String =
            getString(key)

        private fun JSONObject.array(key: String): JSONArray =
            getJSONArray(key)

        private fun JSONArray.toStringList(): List<String> =
            List(size) { getString(it) }

        private fun JSONArray.toClassArray(loader: ClassLoader): Array<Class<*>> =
            Array(size) { ReflectionUtils.findClass(getString(it), loader) }
    }

    private class AesGcmCipher(password: String) : FastCipher {

        private val key = SecretKeySpec(
            MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8)),
            "AES"
        )

        override fun encrypt(src: ByteArray): ByteArray {
            val iv = ByteArray(12).also(SecureRandom()::nextBytes)
            val encrypted = Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
                doFinal(src)
            }
            return iv + encrypted
        }

        override fun decrypt(dst: ByteArray): ByteArray {
            if (dst.size <= 12) return ByteArray(0)
            return Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, dst.copyOfRange(0, 12)))
                doFinal(dst.copyOfRange(12, dst.size))
            }
        }

        override fun encrypt(src: Int): Int = src

        override fun decrypt(dst: Int): Int = dst

        override fun encrypt(src: Long): Long = src

        override fun decrypt(dst: Long): Long = dst
    }
}
