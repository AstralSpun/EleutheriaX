package org.astralspun.eleutheriax.xposed

import android.content.pm.ApplicationInfo
import android.os.Build
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import org.astralspun.eleutheriax.EleutheriaX
import org.astralspun.eleutheriax.dexkit.DexResolver
import org.astralspun.eleutheriax.log.Elog
import org.astralspun.eleutheriax.log.logInnerD
import org.astralspun.eleutheriax.log.logInnerE
import org.astralspun.eleutheriax.reflect.ReflectionUtils
import org.astralspun.eleutheriax.xposed.hook.MemberHookCreator
import org.astralspun.eleutheriax.xposed.hook.hookMember
import org.astralspun.eleutheriax.xposed.hooker.EleutheriaXBaseHooker
import org.astralspun.eleutheriax.xposed.lifecycle.AppLifecycleManager
import org.astralspun.eleutheriax.xposed.param.PackageParam
import org.astralspun.eleutheriax.xposed.param.wrapper.PackageParamWrapper

abstract class EleutheriaXModule : XposedModule() {

    internal var packageParamCallback: (PackageParam.() -> Unit)? = null
    private var isHookInitialized = false
    private val packageParams = mutableMapOf<String, PackageParam>()
    private val loadedPackageNames = mutableSetOf<String>()
    private val packageParamWrappers = mutableMapOf<String, PackageParamWrapper>()

    val modulePackageName: String
        get() = runCatching { moduleApplicationInfo.packageName }.getOrNull().orEmpty()

    open fun onInit() = Unit

    abstract fun onHook()

    fun configs(initiate: EleutheriaXConfigs.() -> Unit) {
        EleutheriaXConfigs().initiate()
    }

    fun encase(initiate: PackageParam.() -> Unit) {
        packageParamCallback = initiate
    }

    fun encase(vararg hooker: EleutheriaXBaseHooker) {
        packageParamCallback = {
            if (hooker.isEmpty()) {
                logInnerE("Failed to passing \"encase\" method because your hooker param is empty")
            } else {
                hooker.forEach { it.assignInstance(this) }
            }
        }
    }

    fun java.lang.reflect.Executable.hook(
        initiate: MemberHookCreator.() -> Unit
    ): XposedInterface.HookHandle = hookMember(this, initiate)

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        EleutheriaX.refreshAppContext()
        prepareHook()
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        EleutheriaX.refreshAppContext()
        prepareHook()
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        dispatch(
            type = HookEntryType.PACKAGE,
            stage = HookStage.PACKAGE_READY,
            packageName = param.packageName,
            processName = param.applicationInfo.processName ?: param.packageName,
            appClassLoader = param.classLoader,
            defaultClassLoader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                param.defaultClassLoader
            } else {
                param.classLoader
            },
            appInfo = param.applicationInfo,
            isFirstPackage = param.isFirstPackage,
            isSystemServer = false
        )
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        dispatch(
            type = HookEntryType.SYSTEM,
            stage = HookStage.SYSTEM_SERVER,
            packageName = PackageParam.SYSTEM_PACKAGE_NAME,
            processName = PackageParam.SYSTEM_PACKAGE_NAME,
            appClassLoader = param.classLoader,
            isFirstPackage = true,
            isSystemServer = true
        )
    }

    private fun isPackageLoaded(packageName: String?, type: HookEntryType): Boolean {
        if (packageName == null) return false
        if (loadedPackageNames.contains("$packageName:$type")) return true
        loadedPackageNames.add("$packageName:$type")
        return false
    }

    private fun PackageParamWrapper.instantiate() =
        packageParams[wrapperNameId] ?: PackageParam(this@EleutheriaXModule, this).also { packageParams[wrapperNameId] = it }

    private fun assignWrapper(
        type: HookEntryType,
        stage: HookStage,
        packageName: String,
        processName: String,
        appClassLoader: ClassLoader,
        defaultClassLoader: ClassLoader = appClassLoader,
        appInfo: ApplicationInfo? = null,
        isFirstPackage: Boolean = false,
        isSystemServer: Boolean = false
    ): PackageParamWrapper? {
        if (isPackageLoaded(packageName, type)) return null
        return packageParamWrappers[if (type == HookEntryType.SYSTEM) PackageParam.SYSTEM_PACKAGE_NAME else packageName]?.also {
            it.type = type
            it.stage = stage
            it.packageName = packageName
            it.processName = processName
            it.appClassLoader = appClassLoader
            it.defaultClassLoader = defaultClassLoader
            it.appInfo = appInfo
            it.isFirstPackage = isFirstPackage
            it.isSystemServer = isSystemServer
        } ?: PackageParamWrapper(
            type = type,
            stage = stage,
            packageName = packageName,
            processName = processName,
            appClassLoader = appClassLoader,
            defaultClassLoader = defaultClassLoader,
            appInfo = appInfo,
            isFirstPackage = isFirstPackage,
            isSystemServer = isSystemServer
        ).also { packageParamWrappers[it.wrapperNameId] = it }
    }

    private fun dispatch(
        type: HookEntryType,
        stage: HookStage,
        packageName: String,
        processName: String,
        appClassLoader: ClassLoader,
        defaultClassLoader: ClassLoader = appClassLoader,
        appInfo: ApplicationInfo? = null,
        isFirstPackage: Boolean = false,
        isSystemServer: Boolean = false
    ) {
        EleutheriaX.refreshAppContext()
        prepareHook()
        assignWrapper(
            type = type,
            stage = stage,
            packageName = packageName,
            processName = processName,
            appClassLoader = appClassLoader,
            defaultClassLoader = defaultClassLoader,
            appInfo = appInfo,
            isFirstPackage = isFirstPackage,
            isSystemServer = isSystemServer
        )?.also {
            logInnerD("Dispatch hook package=$packageName process=$processName stage=$stage type=$type")
            runCatching {
                ReflectionUtils.withDefaultClassLoader(it.appClassLoader) {
                    val packageParam = it.instantiate().assign(this, it)
                    DexResolver.withPackageParam(packageParam) {
                        packageParamCallback?.invoke(packageParam)
                    }
                    if (it.type == HookEntryType.PACKAGE) AppLifecycleManager.registerToAppLifecycle(this, it.packageName)
                }
            }.onFailure {
                logInnerE("An exception occurred in EleutheriaX hook process", it)
            }
        }
    }

    private fun prepareHook() {
        if (isHookInitialized) return
        isHookInitialized = true
        runCatching {
            Elog.attach(this)
            DexResolver.setModuleApkPath(moduleApplicationInfo.sourceDir)
            onInit()
            logInnerD("Initialize EleutheriaX module ${modulePackageName.ifBlank { javaClass.name }}")
            onHook()
            logInnerD("EleutheriaX module initialized")
        }.onFailure {
            logInnerE("An exception occurred while initializing EleutheriaX", it)
        }
    }
}
