package com.log.simianv2.hook.score

import com.log.simianv2.dexkit.DexKitLookup
import com.log.simianv2.hook.HookEnvironment
import com.log.simianv2.hook.base.BaseHook
import com.log.simianv2.service.HostScoreService
import com.log.simianv2.window.HostLogOverlay
import java.lang.reflect.Method

internal class ScoreApiHook(private val env: HookEnvironment) : BaseHook {
    private lateinit var homeOnResume: Method

    override fun initializeDexKit(lookup: DexKitLookup) {
        homeOnResume = lookup.method("homeOnResume", "主页 onResume") {
            declaredClass = HOME_ACTIVITY
            name = "onResume"
            returnType = "void"
            paramTypes()
        }
    }

    override fun install() {
        env.module.hook(homeOnResume).intercept { chain ->
            val result = chain.proceed()
            if (!HostScoreService.isReady) {
                runCatching { HostScoreService.initialize(env.classLoader) }
                    .onSuccess { HostLogOverlay.append("刷分接口初始化完成") }
                    .onFailure { HostLogOverlay.append("刷分接口初始化失败: ${it.message}") }
            }
            result
        }
    }

    private companion object {
        const val HOME_ACTIVITY = "com.fenbi.android.leo.activity.HomeActivity"
    }
}