package com.log.simianv2.hook.pk

import android.util.Log
import com.log.simianv2.dexkit.DexKitLookup
import com.log.simianv2.hook.HookEnvironment
import com.log.simianv2.hook.base.BaseHook
import com.log.simianv2.settings.HostSettingsStore
import com.log.simianv2.window.HostLogOverlay
import java.lang.reflect.Method

internal class QuickAnswerHook(private val env: HookEnvironment) : BaseHook {
    private lateinit var answerMethod: Method

    override fun initializeDexKit(lookup: DexKitLookup) {
        answerMethod = lookup.method("answerMethod", "Quick answering methods") {
            paramTypes("int", "java.util.List", "java.util.List")
            usingStrings("/time/recognize/math")
        }
    }

    override fun install() {
        env.module.hook(answerMethod).intercept { chain ->
            val enabled: Boolean =
                HostSettingsStore.get(env.context, HostSettingsStore.QUICK_ANSWER, false) as Boolean
            env.module.log(Log.INFO, TAG, "快速答题已命中，开关: $enabled")
            if (!enabled) return@intercept chain.proceed()

            @Suppress("UNCHECKED_CAST")
            val answers = chain.getArg(2) as List<String>
            val original = chain.proceed()
            val replacement = answers.firstOrNull() ?: return@intercept original
            HostLogOverlay.append("用户输入答案: $original，自动更改为: $replacement")
            replacement
        }
    }
}