package com.log.simianv2.hook.pk

import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import com.log.simianv2.hook.webview.WebApi
import com.log.simianv2.window.HostLogOverlay

internal object PkAutoController {

    private val handler = Handler(Looper.getMainLooper())

    private const val START_DELAY = 8_500L
    private const val HAPPY_DELAY = 3_000L

    private enum class TaskType {
        SUBMIT_STROKE,
        HAPPY_ACCEPT,
        CONTINUE,
        CONTINUE_PK
    }

    private val tasks = mutableMapOf<TaskType, Runnable>()

    private val points = listOf(
        PointF(146.8571f, 498.5714f),
        PointF(146.8571f, 516.2858f),
        PointF(146.8571f, 544.4261f),
        PointF(148f, 561.7143f),
        PointF(148f, 584f),
        PointF(148f, 610.8572f),
        PointF(148f, 627.7143f),
        PointF(149.7143f, 652.2858f),
        PointF(151.4286f, 668f),
        PointF(153.1429f, 675.7143f),
        PointF(156.8571f, 684.5715f)
    )

    /**
     * 提交笔画
     */
    fun schedule(
        webView: WebView,
        delayMillis: Long = START_DELAY
    ) {
        scheduleTask(
            type = TaskType.SUBMIT_STROKE,
            webView = webView,
            delayMillis = delayMillis,
            taskName = "提交笔画"
        ) {
            WebApi.submitStrokeWithoutDrawing(
                webView = it,
                points = points
            )
        }

        HostLogOverlay.append(
            "${delayMillis / 1000.0}秒后提交笔画"
        )
    }

    /**
     * 点击“开心收下”
     */
    fun clickHappyButton(
        webView: WebView,
        delayMillis: Long = HAPPY_DELAY
    ) {
        scheduleTask(
            type = TaskType.HAPPY_ACCEPT,
            webView = webView,
            delayMillis = delayMillis,
            taskName = "点击开心收下"
        ) {
            WebApi.clickHappyAccept(it)
        }
    }

    /**
     * 点击“继续”
     */
    fun clickContinueButton(
        webView: WebView,
        delayMillis: Long = 0L
    ) {
        scheduleTask(
            type = TaskType.CONTINUE,
            webView = webView,
            delayMillis = delayMillis,
            taskName = "点击继续"
        ) {
            WebApi.clickContinue(
                webView = it
            )
        }
    }

    /**
     * 点击“继续PK”
     */
    fun clickContinuePk(
        webView: WebView,
        delayMillis: Long = 0L
    ) {
        scheduleTask(
            type = TaskType.CONTINUE_PK,
            webView = webView,
            delayMillis = delayMillis,
            taskName = "点击继续PK"
        ) {
            WebApi.clickContinuePk(webView = it)
        }
    }

    /**
     * 统一创建延迟任务
     */
    private fun scheduleTask(
        type: TaskType,
        webView: WebView,
        delayMillis: Long,
        taskName: String,
        action: (WebView) -> Unit
    ) {
        cancel(type)

        lateinit var task: Runnable

        task = Runnable {
            // 防止已经被新任务替换的旧任务继续执行
            if (tasks[type] !== task) {
                return@Runnable
            }

            tasks.remove(type)

            if (!webView.isAttachedToWindow) {
                HostLogOverlay.append(
                    "$taskName 失败：WebView已经离开窗口"
                )
                return@Runnable
            }

            runCatching {
                action(webView)
            }.onFailure {
                HostLogOverlay.append(
                    "$taskName 失败：${it.message ?: it.javaClass.simpleName}"
                )
            }
        }

        tasks[type] = task
        handler.postDelayed(task, delayMillis.coerceAtLeast(0L))
    }

    private fun cancel(type: TaskType) {
        tasks.remove(type)?.let(handler::removeCallbacks)
    }

}