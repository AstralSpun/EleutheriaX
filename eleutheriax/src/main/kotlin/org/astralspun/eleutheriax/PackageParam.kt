package org.astralspun.eleutheriax

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import io.github.libxposed.api.XposedInterface

class PackageParam internal constructor(
    internal val module: EleutheriaXModule,
    internal var wrapper: PackageParamWrapper
) {

    val stage get() = wrapper.stage

    val packageName get() = wrapper.packageName

    val processName get() = wrapper.processName

    val appClassLoader get() = wrapper.appClassLoader

    val defaultClassLoader get() = wrapper.defaultClassLoader

    val appInfo: ApplicationInfo? get() = wrapper.appInfo

    val isFirstPackage get() = wrapper.isFirstPackage

    val isSystemServer get() = wrapper.isSystemServer

    val isMainProcess get() = packageName == processName

    val appContext get() = EleutheriaX.appContext

    val requireAppContext get() = EleutheriaX.requireAppContext

    fun onAppLifecycle(isOnFailureThrowToApp: Boolean = true, initiate: AppLifecycle.() -> Unit) =
        AppLifecycle(isOnFailureThrowToApp).apply(initiate).build()

    fun loadApp(name: String, initiate: PackageParam.() -> Unit) {
        if (wrapper.type == HookEntryType.PACKAGE && (packageName == name || name.isBlank())) initiate(this)
    }

    fun loadApp(vararg name: String, initiate: PackageParam.() -> Unit) {
        if (wrapper.type != HookEntryType.PACKAGE) return
        if (name.isEmpty() || name.any { it == packageName }) initiate(this)
    }

    fun allApps(isExcludeSelf: Boolean = false, initiate: PackageParam.() -> Unit) {
        if (wrapper.type == HookEntryType.PACKAGE && (isExcludeSelf.not() || packageName != module.modulePackageName)) initiate(this)
    }

    fun loadSystem(initiate: PackageParam.() -> Unit) {
        if (wrapper.type == HookEntryType.SYSTEM) initiate(this)
    }

    fun withProcess(name: String, initiate: PackageParam.() -> Unit) {
        if (processName == name) initiate(this)
    }

    fun withProcess(vararg name: String, initiate: PackageParam.() -> Unit) {
        if (name.isEmpty()) error("withProcess method need a \"name\" param")
        if (name.any { it == processName }) initiate(this)
    }

    fun mainProcess(initiate: PackageParam.() -> Unit) {
        if (isMainProcess) initiate(this)
    }

    fun detach() = module.detach()

    fun java.lang.reflect.Executable.hook(
        initiate: MemberHookCreator.() -> Unit
    ): XposedInterface.HookHandle = module.hookMember(this, initiate)

    internal fun assign(wrapper: PackageParamWrapper): PackageParam {
        this.wrapper = wrapper
        return this
    }

    inner class AppLifecycle internal constructor(private val isOnFailureThrowToApp: Boolean) {

        private val isCurrentScope get() = wrapper.type == HookEntryType.PACKAGE

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
