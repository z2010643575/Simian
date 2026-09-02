package com.log.simianv2.hook.nickName

import com.log.simianv2.dexkit.DexKitLookup
import com.log.simianv2.hook.HookEnvironment
import com.log.simianv2.hook.base.BaseHook
import com.log.simianv2.settings.HostSettingsStore
import org.luckypray.dexkit.query.enums.MatchType
import java.lang.reflect.Method

internal class NickHook(private val env: HookEnvironment) : BaseHook {
    private lateinit var nicknameValidator: Method

    override fun initializeDexKit(lookup: DexKitLookup) {
        nicknameValidator = lookup.method("nicknameValidator", "Nickname verification method") {
            returnType = "boolean"
            paramTypes(String::class.java)
            usingNumbers(16)
            invokeMethods {
                add {
                    returnType = "int"
                    paramTypes(String::class.java)
                    usingStrings("GBK")
                }
                matchType = MatchType.Contains
            }
        }
    }

    override fun install() {
        env.module.hook(nicknameValidator).intercept { chain ->
            val enabled: Boolean =
                HostSettingsStore.get(
                    env.context,
                    HostSettingsStore.UNLIMITED_NAME,
                    false
                ) as Boolean
            if (enabled) {
                return@intercept true
            }
            chain.proceed()
        }
    }
}