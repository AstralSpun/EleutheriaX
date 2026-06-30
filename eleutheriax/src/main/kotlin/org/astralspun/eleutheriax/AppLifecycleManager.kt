package org.astralspun.eleutheriax

import android.annotation.SuppressLint
import android.app.Application
import android.app.Instrumentation
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build

internal object AppLifecycleManager {

    private val appLifecycleActors = mutableMapOf<String, AppLifecycleActor>()
    private val registeredPackageNames = mutableSetOf<String>()

    internal var isOnFailureThrowToApp: Boolean? = null

    internal fun get(param: PackageParam) =
        appLifecycleActors[param.packageName] ?: AppLifecycleActor().also { appLifecycleActors[param.packageName] = it }

    @SuppressLint("DiscouragedPrivateApi")
    internal fun registerToAppLifecycle(module: EleutheriaXModule, packageName: String) {
        if (registeredPackageNames.add(packageName).not() || appLifecycleActors.isEmpty()) return
        runCatching {
            Application::class.java.getDeclaredMethod("attach", Context::class.java).hook(module) {
                before {
                    runLifecycle(module) {
                        appLifecycleActors.forEach { (_, actor) ->
                            (args.getOrNull(0) as? Context)?.also { actor.attachBaseContextCallback?.invoke(it, false) }
                        }
                    }
                }
                after {
                    runLifecycle(module) {
                        appLifecycleActors.forEach { (_, actor) ->
                            (args.getOrNull(0) as? Context)?.also { actor.attachBaseContextCallback?.invoke(it, true) }
                        }
                    }
                }
            }
            Application::class.java.getDeclaredMethod("onTerminate").hook(module) {
                after {
                    runLifecycle(module) {
                        (instanceOrNull as? Application)?.also { app ->
                            appLifecycleActors.forEach { (_, actor) -> actor.onTerminateCallback?.invoke(app) }
                        }
                    }
                }
            }
            Application::class.java.getDeclaredMethod("onLowMemory").hook(module) {
                after {
                    runLifecycle(module) {
                        (instanceOrNull as? Application)?.also { app ->
                            appLifecycleActors.forEach { (_, actor) -> actor.onLowMemoryCallback?.invoke(app) }
                        }
                    }
                }
            }
            Application::class.java.getDeclaredMethod("onTrimMemory", Int::class.javaPrimitiveType).hook(module) {
                after {
                    runLifecycle(module) {
                        val app = instanceOrNull as? Application ?: return@runLifecycle
                        val level = args.getOrNull(0) as? Int ?: return@runLifecycle
                        appLifecycleActors.forEach { (_, actor) -> actor.onTrimMemoryCallback?.invoke(app, level) }
                    }
                }
            }
            Application::class.java.getDeclaredMethod("onConfigurationChanged", Configuration::class.java).hook(module) {
                after {
                    runLifecycle(module) {
                        val app = instanceOrNull as? Application ?: return@runLifecycle
                        val config = args.getOrNull(0) as? Configuration ?: return@runLifecycle
                        appLifecycleActors.forEach { (_, actor) -> actor.onConfigurationChangedCallback?.invoke(app, config) }
                    }
                }
            }
            Instrumentation::class.java.getDeclaredMethod("callApplicationOnCreate", Application::class.java).hook(module) {
                after {
                    runLifecycle(module) {
                        (args.getOrNull(0) as? Application)?.also { app ->
                            EleutheriaX.assignAppContext(app)
                            appLifecycleActors.forEach { (_, actor) ->
                                actor.onCreateCallback?.invoke(app)
                                actor.onReceiverActionsCallbacks.forEach { (_, value) ->
                                    if (value.first.isNotEmpty()) IntentFilter().apply {
                                        value.first.forEach { action -> addAction(action) }
                                    }.registerReceiver(app, value.second)
                                }
                                actor.onReceiverFiltersCallbacks.forEach { (_, value) ->
                                    value.first.registerReceiver(app, value.second)
                                }
                            }
                        }
                    }
                }
            }
        }.onFailure {
            module.logE("An exception occurred while registering AppLifecycle", it)
        }
    }

    private fun runLifecycle(module: EleutheriaXModule, initiate: () -> Unit) {
        runCatching(initiate).onFailure {
            if (isOnFailureThrowToApp != false) throw it
            else module.logE("An exception occurred during AppLifecycle event", it)
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun IntentFilter.registerReceiver(
        context: Context,
        result: (context: Context, intent: Intent) -> Unit
    ) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                result(context, intent)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, this, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, this)
        }
    }

    internal class AppLifecycleActor {

        internal var attachBaseContextCallback: ((Context, Boolean) -> Unit)? = null

        internal var onCreateCallback: (Application.() -> Unit)? = null

        internal var onTerminateCallback: (Application.() -> Unit)? = null

        internal var onLowMemoryCallback: (Application.() -> Unit)? = null

        internal var onTrimMemoryCallback: ((Application, Int) -> Unit)? = null

        internal var onConfigurationChangedCallback: ((Application, Configuration) -> Unit)? = null

        internal val onReceiverActionsCallbacks = mutableMapOf<String, Pair<Array<out String>, (Context, Intent) -> Unit>>()

        internal val onReceiverFiltersCallbacks = mutableMapOf<String, Pair<IntentFilter, (Context, Intent) -> Unit>>()
    }
}
