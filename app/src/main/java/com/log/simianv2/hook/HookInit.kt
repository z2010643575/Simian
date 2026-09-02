package com.log.simianv2.hook

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import com.log.simianv2.dexkit.DexKitLookup
import com.log.simianv2.dexkit.DexKitLookupSession
import com.log.simianv2.dialog.HostInitializationDialog
import com.log.simianv2.hook.base.BaseHook
import com.log.simianv2.hook.nickName.NickHook
import com.log.simianv2.hook.pk.PkPayloadHook
import com.log.simianv2.hook.pk.QuickAnswerHook
import com.log.simianv2.hook.score.ScoreApiHook
import com.log.simianv2.hook.setting.SettingsEntryHook
import com.log.simianv2.hook.webview.WebviewHook
import com.log.simianv2.window.HostLogOverlay
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class HookInit : XposedModule() {
    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (param.packageName != TARGET_PACKAGE) return

        val applicationClass = Class.forName(Application::class.java.name)
        hook(applicationClass.getDeclaredMethod("attach", Context::class.java)).intercept { chain ->
            val result = chain.proceed()
            val context = chain.getArg(0) as Context
            HostLogOverlay.install(chain.thisObject as Application)

            // Bootstrap target: it must be hooked before DexKit can be shown in HomeActivity.
            val homeClass = context.classLoader.loadClass(HOME_ACTIVITY)
            hook(
                homeClass.getDeclaredMethod(
                    "onCreate",
                    Bundle::class.java
                )
            ).intercept { homeChain ->
                val onCreateResult = homeChain.proceed()
                initializeFromHome(homeChain.thisObject as Activity, context)
                context.classLoader.loadClass("com.tencent.smtt.sdk.WebView").getDeclaredMethod(
                    "setWebContentsDebuggingEnabled",
                    Boolean::class.java
                ).invoke(null, true)
                WebView.setWebContentsDebuggingEnabled(true)
                onCreateResult
            }
            result
        }
    }

    private fun initializeFromHome(activity: Activity, context: Context) {
        if (!initializing.compareAndSet(false, true)) return
        val progress = HostInitializationDialog(activity)
        progress.show()

        executor.execute {
            val result = runCatching {
                val features = createFeatures(HookEnvironment(this, context))
                DexKitLookupSession(context).use { session ->
                    features.forEachIndexed { index, feature ->
                        activity.runOnUiThread {
                            progress.update(
                                "正在初始化 ${feature.TAG}（${index + 1}/${features.size}）"
                            )
                        }
                        initializeAndInstall(feature, session.scoped(feature.name))
                    }
                    runCatching(session::save).onFailure { error ->
                        log(Log.ERROR, TAG, "DexKit 缓存保存失败", error)
                        HostLogOverlay.append("DexKit 缓存保存失败: ${error.message}")
                    }
                    val source = if (session.usedDexKit) "DexKit 扫描" else "本地缓存"
                    log(Log.INFO, TAG, "Hook 目标已通过${source}完成初始化")
                }
            }
            activity.runOnUiThread {
                progress.dismiss()
                result.onFailure { error ->
                    initializing.set(false)
                    log(Log.ERROR, TAG, "DexKit 初始化失败", error)
                    HostLogOverlay.append("DexKit 初始化失败: ${error.message}")
                }
            }
        }
    }

    private fun initializeAndInstall(feature: BaseHook, lookup: DexKitLookup) {
        runCatching {
            feature.initializeDexKit(lookup)
            feature.install()
        }
            .onSuccess { log(Log.INFO, TAG, "${feature.TAG} Hook成功") }
            .onFailure { error ->
                log(Log.ERROR, TAG, "${feature.TAG} 初始化或Hook失败", error)
                HostLogOverlay.append("${feature.TAG} 初始化或Hook失败: ${error.message}")
            }
    }

    private fun createFeatures(env: HookEnvironment): List<BaseHook> =
        listOf(
            SettingsEntryHook(env),
            PkPayloadHook(env),
            QuickAnswerHook(env),
            NickHook(env),
            ScoreApiHook(env),
            WebviewHook(env),
        )

    private companion object {
        const val TAG = "HookInit"
        const val TARGET_PACKAGE = "com.fenbi.android.leo"
        const val HOME_ACTIVITY = "com.fenbi.android.leo.activity.HomeActivity"
        val initializing = AtomicBoolean(false)
        val executor = Executors.newSingleThreadExecutor()
    }
}
