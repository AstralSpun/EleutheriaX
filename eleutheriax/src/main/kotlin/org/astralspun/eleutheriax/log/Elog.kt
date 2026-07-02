package org.astralspun.eleutheriax.log

import android.util.Log
import io.github.libxposed.api.XposedInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Elog {

    internal var isDebug = false
    private var module: XposedInterface? = null

    object Configs {

        var tag = "EleutheriaX"

        var isEnable = true
            set(value) {
                field = value
                if (value.not()) isDebug = false
            }

        var isRecord = false

        internal fun build() = Unit
    }

    private const val MAX_RECORD_SIZE = 300
    private val recordLock = Any()
    private val records = mutableListOf<ElogData>()

    val inMemoryData: List<ElogData>
        get() = synchronized(recordLock) { records.toList() }

    val contents get() = contents()

    fun D(msg: Any? = null, e: Throwable? = null, tag: String = Configs.tag) =
        print(Log.DEBUG, msg, e, tag)

    fun I(msg: Any? = null, e: Throwable? = null, tag: String = Configs.tag) =
        print(Log.INFO, msg, e, tag)

    fun W(msg: Any? = null, e: Throwable? = null, tag: String = Configs.tag) =
        print(Log.WARN, msg, e, tag)

    fun E(msg: Any? = null, e: Throwable? = null, tag: String = Configs.tag) =
        print(Log.ERROR, msg, e, tag)

    fun contents(data: List<ElogData> = inMemoryData): String =
        buildString {
            data.forEach {
                append(it.head).append(it).append('\n')
                it.throwable?.also { throwable ->
                    append(it.head).append("Dump stack trace for \"").append(throwable.javaClass.name).append("\":\n")
                    append(throwable.stackTraceToString())
                }
            }
        }

    fun clear() {
        synchronized(recordLock) { records.clear() }
    }

    fun saveToFile(fileName: String, data: List<ElogData> = inMemoryData) {
        if (Configs.isRecord && data.isNotEmpty()) File(fileName).appendText(contents(data))
    }

    internal fun attach(module: XposedInterface) {
        this.module = module
    }

    internal fun log(
        module: XposedInterface,
        priority: Int,
        message: String,
        throwable: Throwable? = null,
        tag: String? = Configs.tag
    ) {
        print(priority, message, throwable, tag ?: Configs.tag, module)
    }

    internal fun inner(
        module: XposedInterface,
        priority: Int,
        message: String,
        throwable: Throwable? = null,
        tag: String? = Configs.tag
    ) {
        if (Configs.isEnable.not() || (priority == Log.DEBUG && isDebug.not())) return
        print(priority, message, throwable, tag ?: Configs.tag, module)
    }

    internal fun innerD(message: String, throwable: Throwable? = null, tag: String? = Configs.tag) {
        if (Configs.isEnable.not() || isDebug.not()) return
        print(Log.DEBUG, message, throwable, tag ?: Configs.tag)
    }

    private fun print(
        priority: Int,
        msg: Any?,
        throwable: Throwable? = null,
        tag: String = Configs.tag,
        module: XposedInterface? = this.module
    ) {
        val message = msg?.toString().orEmpty()
        if (message.isBlank() && throwable == null) return
        when (priority) {
            Log.DEBUG -> Log.d(tag, message, throwable)
            Log.INFO -> Log.i(tag, message, throwable)
            Log.WARN -> Log.w(tag, message, throwable)
            Log.ERROR -> Log.e(tag, message, throwable)
            else -> Log.println(priority, tag, message)
        }
        module?.also {
            if (throwable == null) {
                it.log(priority, tag, message)
            } else {
                it.log(priority, tag, message, throwable)
            }
        }
        record(priority, tag, message, throwable)
    }

    private fun record(priority: Int, tag: String, message: String, throwable: Throwable?) {
        if (Configs.isRecord.not()) return
        synchronized(recordLock) {
            if (records.size >= MAX_RECORD_SIZE) records.removeAt(0)
            records.add(ElogData(priority = priorityName(priority), tag = tag, msg = message, throwable = throwable))
        }
    }

    private fun priorityName(priority: Int) = when (priority) {
        Log.DEBUG -> "D"
        Log.INFO -> "I"
        Log.WARN -> "W"
        Log.ERROR -> "E"
        else -> priority.toString()
    }
}

class ElogData internal constructor(
    var timestamp: Long = 0L,
    var time: String = "",
    var tag: String = Elog.Configs.tag,
    var priority: String = "",
    var msg: String = "",
    var throwable: Throwable? = null
) {

    init {
        timestamp = System.currentTimeMillis()
        time = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.ROOT).format(Date(timestamp))
    }

    internal val head get() = "$time ------ "

    override fun toString() = "[$tag][$priority] $msg"
}
