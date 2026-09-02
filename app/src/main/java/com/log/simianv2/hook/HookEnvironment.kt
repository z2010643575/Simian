package com.log.simianv2.hook

import android.content.Context
import io.github.libxposed.api.XposedModule

/** Shared dependencies for every feature hook. */
internal data class HookEnvironment(
    val module: XposedModule,
    val context: Context,
) {
    val classLoader: ClassLoader
        get() = context.classLoader
}
