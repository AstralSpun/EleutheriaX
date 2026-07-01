package org.astralspun.eleutheriax.xposed.hook

import io.github.libxposed.api.XposedInterface
import org.astralspun.eleutheriax.xposed.EleutheriaXModule
import org.astralspun.eleutheriax.xposed.param.PackageParam
import java.lang.reflect.Executable

fun XposedInterface.hookMember(
    member: Executable,
    initiate: MemberHookCreator.() -> Unit
): XposedInterface.HookHandle =
    MemberHookCreator(this, member).apply(initiate).build()

fun XposedInterface.hookClassInitializer(
    clazz: Class<*>,
    initiate: MemberHookCreator.() -> Unit
): XposedInterface.HookHandle {
    val creator = MemberHookCreator(this).apply(initiate)
    return hookClassInitializer(clazz)
        .setPriority(creator.priority)
        .setExceptionMode(creator.exceptionMode)
        .setId(creator.id)
        .intercept(creator.createHooker())
}

fun Executable.hook(
    module: EleutheriaXModule,
    initiate: MemberHookCreator.() -> Unit
): XposedInterface.HookHandle = module.hookMember(this, initiate)

fun Executable.hook(
    param: PackageParam,
    initiate: MemberHookCreator.() -> Unit
): XposedInterface.HookHandle = param.module.hookMember(this, initiate)

fun PackageParam.hook(
    member: Executable,
    initiate: MemberHookCreator.() -> Unit
): XposedInterface.HookHandle = module.hookMember(member, initiate)

fun PackageParam.hookClassInitializer(
    clazz: Class<*>,
    initiate: MemberHookCreator.() -> Unit
): XposedInterface.HookHandle = module.hookClassInitializer(clazz, initiate)
