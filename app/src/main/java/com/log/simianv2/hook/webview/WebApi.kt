package com.log.simianv2.hook.webview

import android.graphics.PointF
import android.webkit.WebView
import com.log.simianv2.window.HostLogOverlay
import org.json.JSONArray
import org.json.JSONObject

internal object WebApi {

    private const val MODULE_URL =
        "https://leo.fbcontent.cn/bh5/leo-web-oral-pk/assets/" +
                "index-legacy.DMgv2yXx.js"

    /**
     * 将坐标写入画板内部数据并触发 endStroke。
     */
    fun submitStrokeWithoutDrawing(
        webView: WebView,
        points: List<PointF>
    ) {
        if (points.isEmpty()) {
            HostLogOverlay.append("笔画提交失败：坐标为空")
            return
        }

        val startTime = System.currentTimeMillis()

        val pointsJson = JSONArray().apply {
            points.forEachIndexed { index, point ->
                put(
                    JSONObject().apply {
                        put("x", point.x.toDouble())
                        put("y", point.y.toDouble())
                        put("pressure", 0)
                        put("time", startTime + index * 8L)
                    }
                )
            }
        }

        val script = """
            (() => {
                const points = $pointsJson;

                window.__strokeSubmitStatus = {
                    status: 'loading-module',
                    pointCount: points.length
                };

                System.import('$MODULE_URL')
                    .then(module => {
                        const store = module.d?.();
                        const pad = store?.pad?.value ?? store?.pad;

                        if (!pad) {
                            throw new Error('画板尚未初始化');
                        }

                        pad._data = [{
                            points: points,
                            penColor: '#000',
                            minWidth: 3,
                            maxWidth: 3,
                            velocityFilterWeight: 0.7,
                            compositeOperation: 'source-over'
                        }];

                        window.__strokeSubmitStatus.status =
                            'dispatching-end-stroke';

                        pad.dispatchEvent(
                            new CustomEvent('endStroke', {
                                detail: {
                                    synthetic: true
                                }
                            })
                        );

                        window.__strokeSubmitStatus.status = 'submitted';
                    })
                    .catch(error => {
                        window.__strokeSubmitStatus.status = 'failed';
                        window.__strokeSubmitStatus.error =
                            String(error);
                    });

                return JSON.stringify(
                    window.__strokeSubmitStatus
                );
            })();
        """.trimIndent()

        evaluate(
            webView = webView,
            script = script,
            actionName = "笔画提交"
        )
    }

    /**
     * 查找一次并直接点击“开心收下”。
     */
    fun clickHappyAccept(webView: WebView) {
        clickButton(
            webView = webView,
            text = "开心收下",
            selectors = listOf(
                ".checkin-reward-content .button",
                ".xiaoyuan-cultivate .btn-confirm-wrap"
            )
        )
    }

    /**
     * 查找一次并直接点击“继续”。
     */
    fun clickContinue(webView: WebView) {
        clickButton(
            webView = webView,
            text = "继续",
            selectors = listOf(
                ".modal-confirm",
                ".bottom-content-button"
            )
        )
    }

    /**
     * 查找一次并直接点击“继续PK”。
     */
    fun clickContinuePk(webView: WebView) {
        clickButton(
            webView = webView,
            text = "继续PK",
            selectors = listOf(
                ".retry"
            )
        )
    }

    /**
     * 根据选择器和按钮文字查找一次并直接点击。
     *
     * 如果按钮尚未出现，会直接返回 not-found。
     */
    private fun clickButton(
        webView: WebView,
        text: String,
        selectors: List<String>
    ) {
        val targetTextJson = JSONObject.quote(text)

        val selectorsJson = JSONArray().apply {
            selectors.forEach(::put)
        }

        val script = """
            (() => {
                const targetText = $targetTextJson;
                const selectors = $selectorsJson;

                const normalizeText = element =>
                    (element?.textContent || '')
                        .replace(/\s+/g, '');

                const isVisible = element => {
                    if (!element) {
                        return false;
                    }

                    const style =
                        window.getComputedStyle(element);

                    const rect =
                        element.getBoundingClientRect();

                    return style.display !== 'none' &&
                           style.visibility !== 'hidden' &&
                           style.opacity !== '0' &&
                           style.pointerEvents !== 'none' &&
                           rect.width > 0 &&
                           rect.height > 0;
                };

                const textMatches = element =>
                    normalizeText(element) === targetText;

                let button = null;

                // 优先使用已知选择器
                for (const selector of selectors) {
                    const elements =
                        document.querySelectorAll(selector);

                    button = Array.from(elements).find(
                        element =>
                            isVisible(element) &&
                            textMatches(element)
                    );

                    if (button) {
                        break;
                    }
                }

                // 选择器失效时，通过按钮文字兜底
                if (!button) {
                    const candidates =
                        document.querySelectorAll([
                            'button',
                            '[role="button"]',
                            '.button',
                            '.btn',
                            '.retry',
                            '.modal-confirm',
                            '.bottom-content-button',
                            '.btn-confirm-wrap'
                        ].join(','));

                    button = Array.from(candidates).find(
                        element =>
                            isVisible(element) &&
                            textMatches(element)
                    );
                }

                if (!button) {
                    return JSON.stringify({
                        status: 'not-found',
                        text: targetText,
                        url: location.href
                    });
                }

                button.click();

                return JSON.stringify({
                    status: 'clicked',
                    text: targetText,
                    tagName: button.tagName,
                    className: String(button.className),
                    url: location.href
                });
            })();
        """.trimIndent()

        evaluate(
            webView = webView,
            script = script,
            actionName = "点击$text"
        )
    }

    /**
     * 在 WebView 所在线程执行 JS，并输出执行结果。
     */
    private fun evaluate(
        webView: WebView,
        script: String,
        actionName: String
    ) {
        webView.post {
            if (!webView.isAttachedToWindow) {
                HostLogOverlay.append(
                    "$actionName 失败：WebView已经离开窗口"
                )
                return@post
            }

            webView.evaluateJavascript(script) { result ->
                HostLogOverlay.append(
                    "$actionName 结果：$result"
                )
            }
        }
    }
}