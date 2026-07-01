package org.astralspun.eleutheriax

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import io.github.libxposed.api.XposedInterface

open class PackageParam internal constructor(
    private var currentModule: EleutheriaXModule? = null,
    internal var wrapper: PackageParamWrapper? = null
) {

    internal val module get() = currentModule ?: error("PackageParam module is null")

    private val currentWrapper get() = wrapper ?: error("PackageParam wrapper is null")

    val stage get() = currentWrapper.stage

    val packageName get() = currentWrapper.packageName

    val processName get() = currentWrapper.processName

    val appClassLoader get() = currentWrapper.appClassLoader

    val defaultClassLoader get() = currentWrapper.defaultClassLoader

    val appInfo: ApplicationInfo? get() = currentWrapper.appInfo

    val isFirstPackage get() = currentWrapper.isFirstPackage

    val isSystemServer get() = currentWrapper.isSystemServer

    val isMainProcess get() = packageName == processName

    val appContext get() = EleutheriaX.appContext

    val requireAppContext get() = EleutheriaX.requireAppContext

    fun onAppLifecycle(isOnFailureThrowToApp: Boolean = true, initiate: AppLifecycle.() -> Unit) =
        AppLifecycle(isOnFailureThrowToApp).apply(initiate).build()

    fun loadApp(name: String, initiate: PackageParam.() -> Unit) {
        if (currentWrapper.type == HookEntryType.PACKAGE && (packageName == name || name.isBlank())) initiate(this)
    }

    fun loadApp(vararg name: String, initiate: PackageParam.() -> Unit) {
        if (name.isEmpty()) return allApps(initiate = initiate)
        if (currentWrapper.type != HookEntryType.PACKAGE) return
        if (name.any { it == packageName }) initiate(this)
    }

    fun loadApp(name: String, hooker: EleutheriaXBaseHooker) {
        if (currentWrapper.type == HookEntryType.PACKAGE && (packageName == name || name.isBlank())) loadHooker(hooker)
    }

    fun loadApp(name: String, vararg hooker: EleutheriaXBaseHooker) {
        if (hooker.isEmpty()) error("loadApp method need a \"hooker\" param")
        if (currentWrapper.type == HookEntryType.PACKAGE && (packageName == name || name.isBlank())) {
            hooker.forEach { loadHooker(it) }
        }
    }

    fun allApps(isExcludeSelf: Boolean = false, initiate: PackageParam.() -> Unit) {
        if (currentWrapper.type == HookEntryType.PACKAGE && (isExcludeSelf.not() || packageName != module.modulePackageName)) initiate(this)
    }

    fun allApps(isExcludeSelf: Boolean = false, hooker: EleutheriaXBaseHooker) {
        if (currentWrapper.type == HookEntryType.PACKAGE && (isExcludeSelf.not() || packageName != module.modulePackageName)) loadHooker(hooker)
    }

    fun allApps(isExcludeSelf: Boolean = false, vararg hooker: EleutheriaXBaseHooker) {
        if (hooker.isEmpty()) error("allApps method need a \"hooker\" param")
        if (currentWrapper.type == HookEntryType.PACKAGE && (isExcludeSelf.not() || packageName != module.modulePackageName)) {
            hooker.forEach { loadHooker(it) }
        }
    }

    fun loadSystem(initiate: PackageParam.() -> Unit) {
        if (currentWrapper.type == HookEntryType.SYSTEM) initiate(this)
    }

    fun loadSystem(hooker: EleutheriaXBaseHooker) {
        if (currentWrapper.type == HookEntryType.SYSTEM) loadHooker(hooker)
    }

    fun loadSystem(vararg hooker: EleutheriaXBaseHooker) {
        if (hooker.isEmpty()) error("loadSystem method need a \"hooker\" param")
        if (currentWrapper.type == HookEntryType.SYSTEM) hooker.forEach { loadHooker(it) }
    }

    fun withProcess(name: String, initiate: PackageParam.() -> Unit) {
        if (processName == name) initiate(this)
    }

    fun withProcess(vararg name: String, initiate: PackageParam.() -> Unit) {
        if (name.isEmpty()) error("withProcess method need a \"name\" param")
        if (name.any { it == processName }) initiate(this)
    }

    fun withProcess(name: String, hooker: EleutheriaXBaseHooker) {
        if (processName == name) loadHooker(hooker)
    }

    fun withProcess(name: String, vararg hooker: EleutheriaXBaseHooker) {
        if (hooker.isEmpty()) error("withProcess method need a \"hooker\" param")
        if (processName == name) hooker.forEach { loadHooker(it) }
    }

    fun mainProcess(initiate: PackageParam.() -> Unit) {
        if (isMainProcess) initiate(this)
    }

    fun mainProcess(hooker: EleutheriaXBaseHooker) {
        if (isMainProcess) loadHooker(hooker)
    }

    fun mainProcess(vararg hooker: EleutheriaXBaseHooker) {
        if (hooker.isEmpty()) error("mainProcess method need a \"hooker\" param")
        if (isMainProcess) hooker.forEach { loadHooker(it) }
    }

    fun loadHooker(hooker: EleutheriaXBaseHooker) {
        hooker.wrapper?.also {
            if (it.packageName.isNotBlank() && it.type != HookEntryType.SYSTEM) {
                if (it.packageName == currentWrapper.packageName) {
                    hooker.assignInstance(this)
                } else {
                    module.logW(
                        "This Hooker \"${hooker::class.java.name}\" is singleton or reused, " +
                            "but the current process has multiple package name \"${currentWrapper.packageName}\", " +
                            "the original is \"${it.packageName}\""
                    )
                }
            } else {
                hooker.assignInstance(this)
            }
        } ?: hooker.assignInstance(this)
    }

    fun detach() = module.detach()

    fun java.lang.reflect.Executable.hook(
        initiate: MemberHookCreator.() -> Unit
    ): XposedInterface.HookHandle = module.hookMember(this, initiate)

    internal fun assign(module: EleutheriaXModule, wrapper: PackageParamWrapper): PackageParam {
        this.currentModule = module
        this.wrapper = wrapper
        return this
    }

    inner class AppLifecycle internal constructor(private val isOnFailureThrowToApp: Boolean) {

        private val isCurrentScope get() = currentWrapper.type == HookEntryType.PACKAGE

        fun attachBaseContext(result: (baseContext: Context, hasCalledSuper: Boolean) -> Unit) {
            if (isCurrentScope) AppLifecycleManager.get(this@PackageParam).attachBaseContextCallback = result
        }

        fun onCreate(initiate: Application.() -> Unit) {
            if (isCurrentScope) AppLifecycleManager.get(this@PackageParam).onCreateCallback = initiate
        }

        fun onTerminate(initiate: Application.() -> Unit) {
            if (isCurrentScope) AppLifecycleManager.get(this@PackageParam).onTerminateCallback = initiate
        }

        fun onLowMemory(initiate: Application.() -> Unit) {
            if (isCurrentScope) AppLifecycleManager.get(this@PackageParam).onLowMemoryCallback = initiate
        }

        fun onTrimMemory(result: (self: Application, level: Int) -> Unit) {
            if (isCurrentScope) AppLifecycleManager.get(this@PackageParam).onTrimMemoryCallback = result
        }

        fun onConfigurationChanged(result: (self: Application, config: Configuration) -> Unit) {
            if (isCurrentScope) AppLifecycleManager.get(this@PackageParam).onConfigurationChangedCallback = result
        }

        fun registerReceiver(vararg action: String, result: (context: Context, intent: Intent) -> Unit) {
            if (isCurrentScope && action.isNotEmpty()) {
                AppLifecycleManager.get(this@PackageParam).onReceiverActionsCallbacks[action.joinToString()] = action to result
            }
        }

        fun registerReceiver(filter: IntentFilter, result: (context: Context, intent: Intent) -> Unit) {
            if (isCurrentScope) {
                AppLifecycleManager.get(this@PackageParam).onReceiverFiltersCallbacks[filter.toString()] = filter to result
            }
        }

        internal fun build() {
            if (AppLifecycleManager.isOnFailureThrowToApp == null) {
                AppLifecycleManager.isOnFailureThrowToApp = isOnFailureThrowToApp
            }
        }
    }

    companion object {
        const val SYSTEM_PACKAGE_NAME = "system"
    }
}
