package org.astralspun.eleutheriax.xposed

enum class HookStage {
    MODULE_LOADED,
    PACKAGE_LOADED,
    PACKAGE_READY,
    SYSTEM_SERVER
}
