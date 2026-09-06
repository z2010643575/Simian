package com.log.simianv2.hook.webview

import android.graphics.PointF
import android.webkit.WebView
import com.log.simianv2.window.HostLogOverlay
import org.json.JSONArray
import org.json.JSONObject

internal object WebApi {
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

        const status =
            window.__strokeSubmitStatus = {
                status: 'finding-module',
                pointCount: points.length
            };

        const unref = target => {
            if (
                target &&
                typeof target === 'object' &&
                'value' in target
            ) {
                return target.value;
            }

            return target;
        };

        const findWritingModule = async () => {
            const resourceUrls = performance
                .getEntriesByType('resource')
                .map(item => item.name);

            const scriptUrls = Array.from(
                document.scripts
            )
                .map(item => item.src)
                .filter(Boolean);

            const candidates = Array.from(
                new Set([
                    ...resourceUrls,
                    ...scriptUrls
                ])
            ).filter(url =>
                url.includes(
                    '/leo-web-oral-pk/assets/'
                ) &&
                /index-legacy\.[^/]+\.js/.test(url)
            );

            status.candidates = candidates;

            for (const moduleUrl of candidates) {
                try {
                    const module =
                        await System.import(moduleUrl);

                    if (
                        typeof module?.d !== 'function'
                    ) {
                        continue;
                    }

                    /*
                     * 先检查函数源码，防止误调用公共模块中
                     * 名字同样为d的closeWebView等函数。
                     */
                    const exportSource =
                        Function.prototype.toString.call(
                            module.d
                        );

                    if (
                        !exportSource.includes(
                            'recognizeConfig'
                        ) ||
                        !exportSource.includes('pad')
                    ) {
                        continue;
                    }

                    const store = module.d();

                    const pad =
                        unref(store?.pad);

                    const recognizeConfig =
                        unref(
                            store?.recognizeConfig
                        );

                    if (
                        !pad ||
                        typeof pad.dispatchEvent !==
                            'function' ||
                        typeof pad.toData !==
                            'function'
                    ) {
                        continue;
                    }

                    /*
                     * 只选择当前题目已经初始化的Store，
                     * 避免选中旧模块实例。
                     */
                    if (!recognizeConfig) {
                        continue;
                    }

                    return {
                        moduleUrl,
                        store,
                        pad,
                        recognizeConfig
                    };
                } catch (_) {
                    // 当前候选不是画板模块，继续检查
                }
            }

            throw new Error(
                '没有找到已初始化的画板模块'
            );
        };

        if (
            typeof System === 'undefined' ||
            typeof System.import !== 'function'
        ) {
            status.status = 'failed';
            status.error =
                '当前页面不支持System.import';

            return JSON.stringify(status);
        }

        findWritingModule()
            .then(result => {
                const pad = result.pad;
                const config =
                    result.recognizeConfig;

                status.moduleUrl =
                    result.moduleUrl;

                status.keypointId =
                    config.keypointId;

                status.expectedResult =
                    config.answers;

                pad._data = [{
                    points: points,
                    penColor: '#000',
                    minWidth: 3,
                    maxWidth: 3,
                    velocityFilterWeight: 0.7,
                    compositeOperation:
                        'source-over'
                }];

                if ('_isEmpty' in pad) {
                    pad._isEmpty = false;
                }

                status.status =
                    'dispatching-end-stroke';

                pad.dispatchEvent(
                    new CustomEvent(
                        'endStroke',
                        {
                            detail: {
                                synthetic: true
                            }
                        }
                    )
                );

                status.status =
                    'waiting-recognition';
            })
            .catch(error => {
                status.status = 'failed';

                status.error = String(
                    error?.stack ||
                    error?.message ||
                    error
                );
            });

        return JSON.stringify(status);
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