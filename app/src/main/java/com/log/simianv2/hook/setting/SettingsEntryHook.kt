package com.log.simianv2.hook.setting

import android.view.View
import android.view.ViewGroup
import com.log.simianv2.dexkit.DexKitLookup
import com.log.simianv2.dialog.HostSettingsDialog
import com.log.simianv2.hook.HookEnvironment
import com.log.simianv2.hook.base.BaseHook
import com.log.simianv2.window.HostLogOverlay
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Proxy

internal class SettingsEntryHook(private val env: HookEnvironment) : BaseHook {
    private lateinit var cellConstructor: Constructor<*>
    private lateinit var createCellMethod: Method

    override fun initializeDexKit(lookup: DexKitLookup) {
        cellConstructor = lookup.constructor("cellConstructor", "设置入口容器") {
            declaredClass = CELL_LIST_CLASS
            paramTypes("android.content.Context", "android.util.AttributeSet", "int")
        }
        createCellMethod = lookup.method("createCellMethod", "设置入口创建方法") {
            declaredClass = CELL_LIST_CLASS
            name = "A"
            paramTypes(
                CELL_LIST_CLASS,
                "int",
                "java.lang.String",
                "java.lang.String",
                "kotlin.jvm.functions.Function0",
                "int",
                "java.lang.Object",
            )
        }
    }

    override fun install() {
        installCellConstructorHook()
    }

    private fun installCellConstructorHook() {
        env.module.hook(cellConstructor).intercept { chain ->
            val result = chain.proceed()
            val hostContext = (chain.thisObject as View).context
            val functionClass = env.classLoader.loadClass("kotlin.jvm.functions.Function0")
            val unit = env.classLoader
                .loadClass("kotlin.Unit")
                .getDeclaredField("INSTANCE")
                .get(null)
            val action = Proxy.newProxyInstance(
                functionClass.classLoader,
                arrayOf(functionClass),
            ) { proxy, method, args ->
                when (method.name) {
                    "invoke" -> {
                        HostSettingsDialog.show(hostContext)
                        unit
                    }

                    "toString" -> "ModuleSettingsAction"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.getOrNull(0)
                    else -> null
                }
            }
            val settingsView = createCellMethod.invoke(
                null,
                chain.thisObject,
                0x7f100222,
                "模块设置",
                null,
                action,
                4,
                null,
            ) as View
            (chain.thisObject as ViewGroup).addView(
                settingsView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            HostLogOverlay.append("模块设置入口注入完成")
            result
        }
    }

    private companion object {
        const val CELL_LIST_CLASS =
            "com.fenbi.android.leo.business.user.view.UserCenterCellListView"
    }
}