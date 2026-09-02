package com.log.simianv2.hook.pk

import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import com.log.simianv2.dexkit.DexKitLookup
import com.log.simianv2.hook.HookEnvironment
import com.log.simianv2.hook.base.BaseHook
import com.log.simianv2.settings.HostSettingsStore
import com.log.simianv2.window.HostLogOverlay
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.lang.reflect.Constructor

internal class PkPayloadHook(private val env: HookEnvironment) : BaseHook {
    private lateinit var encryptResultConstructor: Constructor<*>

    override fun initializeDexKit(lookup: DexKitLookup) {
        encryptResultConstructor =
            lookup.constructor("encryptResult", "Answer Load Constructor") {
                declaredClass = ENCRYPT_RESULT_CLASS
                paramTypes("java.lang.String")
            }
    }

    override fun install() {
        env.module.hook(encryptResultConstructor).intercept { chain ->

            val enabled: Boolean =
                HostSettingsStore.get(
                    env.context,
                    HostSettingsStore.CUSTOM_ANSWER_ENABLED,
                    false
                ) as Boolean

            env.module.log(Log.INFO, TAG, "EncryptResult 已命中，自定义答案开关: $enabled")

            val decodedBytes = try {
                Base64.decode(chain.getArg(0) as String, Base64.DEFAULT)
            } catch (e: IllegalArgumentException) {
                return@intercept chain.proceed()
            }

            val payload = try {
                decodedBytes.decodeToString().let {
                    JSONObject(it)
                }
            } catch (e: JSONException) {
                return@intercept chain.proceed()
            }

            logPayload(payload)

            val customEnabled: Boolean = HostSettingsStore.get(
                env.context,
                HostSettingsStore.CUSTOM_QUESTION_COUNT_ENABLED,
                false
            ) as Boolean
            if (customEnabled) {
                val count: Int =
                    HostSettingsStore.get(
                        env.context,
                        HostSettingsStore.CUSTOM_QUESTION_COUNT,
                        1
                    ) as Int
                setCustomQuestionCount(payload, count)
            }
            applyCustomAnswer(payload, enabled)

            val encoded =
                Base64.encodeToString(payload.toString().toByteArray(), Base64.NO_WRAP)
            chain.proceed(arrayOf(encoded))
        }
    }

    private fun logPayload(payload: JSONObject) {
        HostLogOverlay.append("当前场次 PK id: ${payload.get("pkIdStr")}")
        val user = payload.getJSONObject("otherUser")
        HostLogOverlay.append("对方账号 id: ${user.get("userId")} 名字: ${user.get("userName")}")
        HostLogOverlay.append("当前模式: ${payload.getJSONObject("examVO").get("pointName")}")
    }

    private fun setCustomQuestionCount(payload: JSONObject, count: Int) {
        val examVO = payload.getJSONObject("examVO")
        val questions = examVO.getJSONArray("questions")

        val array = JSONArray()
        for (index in 0 until count) {
            val question = questions.getJSONObject(index)
            array.put(question)
        }
        examVO.put("questions", array)
    }

    private fun applyCustomAnswer(payload: JSONObject, enabled: Boolean) {
        if (!enabled) return
        val answer: String =
            HostSettingsStore.get(env.context, HostSettingsStore.CUSTOM_ANSWER, "") as String
        val questions = payload.getJSONObject("examVO").getJSONArray("questions")
        for (index in 0 until questions.length()) {
            val question = questions.getJSONObject(index)
            question.put("answer", answer)
            question.getJSONArray("answers").put(answer)
            HostLogOverlay.append("已把 ${question.getString("content")} 答案替换为 $answer")
        }
    }

    private companion object {
        const val ENCRYPT_RESULT_CLASS =
            "com.fenbi.android.leo.webapp.secure.commands.EncryptResult"
    }
}