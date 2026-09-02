package com.log.simianv2.hook.webview

import android.graphics.PointF
import android.util.Base64
import android.util.Log
import android.webkit.WebView
import com.log.simianv2.dexkit.DexKitLookup
import com.log.simianv2.hook.HookEnvironment
import com.log.simianv2.hook.base.BaseHook
import com.log.simianv2.hook.pk.PkAutoController
import com.log.simianv2.settings.HostSettingsStore
import com.log.simianv2.window.HostLogOverlay
import org.json.JSONObject
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal class WebviewHook(private val env: HookEnvironment) : BaseHook {
    private lateinit var dataEncrypt: Method

    private lateinit var leoWebViewClass: Class<*>

    override fun initializeDexKit(lookup: DexKitLookup) {
        dataEncrypt = lookup.method("dataEncrypt", "WebView 加密方法") {
            name = "dataEncrypt"
            paramTypes(String::class.java)
            declaredClass {
                methods {
                    add {
                        modifiers = Modifier.PUBLIC
                        name = "getName"
                        returnType = String::class.java.name
                        usingStrings("LeoSecureWebView")
                    }
                }
            }
        }
        leoWebViewClass = lookup.findClass("LeoWebViewClass", "LeoWebView Class") {
            methods {
                add {
                    modifiers = Modifier.PUBLIC
                    name = "getName"
                    returnType = String::class.java.name
                    usingStrings("LeoWebView")
                }
            }
        }
    }

    override fun install() {
        env.module.hook(dataEncrypt).intercept { chain ->
            val enable: Boolean = HostSettingsStore.get(
                env.context,
                HostSettingsStore.CUSTOM_END_TIME_ENABLED,
                false
            ) as Boolean
            if (!enable) return@intercept chain.proceed()

            val json = String(Base64.decode(chain.getArg(0) as String, 0)).let { JSONObject(it) }

            val arguments = json.getJSONArray("arguments")
            val root = arguments.getJSONObject(0)
            val data = String(Base64.decode(root.get("base64").toString().toByteArray(), 0)).let {
                JSONObject(it)
            }
            val origCostTime = data.get("costTime")
            val costTime: Long =
                HostSettingsStore.get(env.context, HostSettingsStore.CUSTOM_END_TIME, 0L) as Long
            data.put("costTime", costTime)
            data.getJSONObject("examVO").put("costTime", costTime)

            val encodeBase64 = Base64.encodeToString(data.toString().toByteArray(), 0)
            root.put("base64", encodeBase64)

            val encodeJson = Base64.encodeToString(json.toString().toByteArray(), 0)
            HostLogOverlay.append("时间${origCostTime} -> ${costTime}")
            chain.proceed(arrayOf(encodeJson))
        }

        val tWebClass = env.classLoader.loadClass("com.tencent.smtt.sdk.WebView")
        env.module.hook(
            tWebClass.getDeclaredMethod(
                "setWebContentsDebuggingEnabled",
                Boolean::class.java
            )
        )
            .intercept { chain ->
                chain.proceed(arrayOf(true))
            }

        env.module.hook(
            WebView::class.java.getDeclaredMethod(
                "setWebContentsDebuggingEnabled",
                Boolean::class.java
            )
        ).intercept { chain ->
            chain.proceed(arrayOf(true))
        }

        val loadUrlsMethod = arrayOf(
            WebView::class.java.getDeclaredMethod("loadUrl", String::class.java),
            WebView::class.java.getDeclaredMethod("loadUrl", String::class.java, Map::class.java)
        )
        loadUrlsMethod.forEach {
            env.module.hook(it).intercept { chain ->

                val webview = chain.thisObject as WebView
                val url = chain.getArg(0) as String
                val startUrl = url.substringBefore("?")
                when (startUrl) {
                    "https://xyks.yuanfudao.com/bh5/leo-web-oral-pk/exercise.html" -> {
                        val enabled = HostSettingsStore.get(
                            env.context,
                            HostSettingsStore.AUTO_ANSWER_ENABLED,
                            false
                        ) as Boolean
                        if (enabled) {
                            val time = HostSettingsStore.get(
                                env.context,
                                HostSettingsStore.AUTO_ANSWER_SPEED,
                                9000L
                            ) as Long
                            PkAutoController.schedule(webview, time)
                        }
                    }

                    "https://xyks.yuanfudao.com/bh5/leo-web-study-group/motivation-honor-roll.html" -> {
                        if (HostSettingsStore.get(
                                env.context,
                                HostSettingsStore.AUTO_CLICK_HAPPY_ACCEPT,
                                false
                            ) as Boolean
                        ) PkAutoController.clickHappyButton(webview)

                        if (HostSettingsStore.get(
                                env.context,
                                HostSettingsStore.AUTO_CLICK_CONTINUE,
                                false
                            ) as Boolean
                        ) PkAutoController.clickContinueButton(webview, 500L)
                    }

                    "https://xyks.yuanfudao.com/bh5/leo-web-oral-pk/result.html" -> {
                        if (HostSettingsStore.get(
                                env.context,
                                HostSettingsStore.AUTO_CLICK_CONTINUE_PK,
                                false
                            ) as Boolean
                        ) PkAutoController.clickContinuePk(webview, 2000L)
                    }
                }
                chain.proceed()
            }
        }
    }
}
