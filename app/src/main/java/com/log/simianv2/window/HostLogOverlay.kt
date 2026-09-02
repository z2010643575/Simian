package com.log.simianv2.window

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.log.simianv2.settings.HostSettingsStore
import java.lang.ref.WeakReference
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.WeakHashMap
import kotlin.math.abs

/** Draggable log panel attached to the current host Activity. No overlay permission is required. */
object HostLogOverlay {
    private const val TEAL = 0xFF006A66.toInt()
    private const val PANEL = 0xED102021.toInt()
    private const val MAX_LINES = 120
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val lines = ArrayDeque<String>()
    private val overlays = WeakHashMap<Activity, View>()
    private val installedApplications = WeakHashMap<Application, Boolean>()
    private val outputs = mutableListOf<WeakReference<TextView>>()

    @Volatile
    private var enabled = false

    @Volatile
    private var minimized = false

    fun install(application: Application) {
        enabled =
            HostSettingsStore.get(application, HostSettingsStore.LOG_OVERLAY, false) as Boolean
        runOnMain {
            if (installedApplications.put(application, true) != null) return@runOnMain
            application.registerActivityLifecycleCallbacks(
                object : Application.ActivityLifecycleCallbacks {
                    override fun onActivityResumed(activity: Activity) {
                        if (enabled) attachTo(activity)
                    }

                    override fun onActivityDestroyed(activity: Activity) {
                        overlays.remove(activity)
                        cleanOutputReferences()
                    }

                    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit

                    override fun onActivityStarted(activity: Activity) = Unit

                    override fun onActivityPaused(activity: Activity) = Unit

                    override fun onActivityStopped(activity: Activity) = Unit

                    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) =
                        Unit
                }
            )
        }
    }

    fun show(context: Context) {
        val activity = context.findActivity() ?: return
        enabled = true
        HostSettingsStore.put(context, HostSettingsStore.LOG_OVERLAY, true)
        runOnMain { attachTo(activity) }
    }

    private fun attachTo(activity: Activity) {
        runOnMain {
            if (activity.isFinishing || activity.isDestroyed || overlays.containsKey(activity)) {
                return@runOnMain
            }
            val oldActivities = overlays.keys.filter { it !== activity }
            oldActivities.forEach { oldActivity ->
                overlays.remove(oldActivity)?.let { (it.parent as? ViewGroup)?.removeView(it) }
            }
            val root = activity.window.decorView as? ViewGroup ?: return@runOnMain
            val overlay = createOverlay(activity, root)
            overlays[activity] = overlay
            root.addView(overlay)
        }
    }

    fun hide(context: Context) {
        enabled = false
        minimized = false
        HostSettingsStore.put(context, HostSettingsStore.LOG_OVERLAY, false)
        runOnMain {
            overlays.values.toList().forEach { overlay ->
                (overlay.parent as? ViewGroup)?.removeView(overlay)
            }
            overlays.clear()
            cleanOutputReferences()
        }
    }

    fun isEnabled(): Boolean = enabled

    fun append(message: String) {
        val line = "${LocalTime.now().format(timeFormatter)}  $message"
        synchronized(lines) {
            lines.addLast(line)
            while (lines.size > MAX_LINES) lines.removeFirst()
        }
        runOnMain {
            val text = synchronized(lines) { lines.joinToString("\n") }
            val iterator = outputs.iterator()
            while (iterator.hasNext()) {
                val output = iterator.next().get()
                if (output == null) {
                    iterator.remove()
                } else {
                    output.text = text
                    (output.parent as? ScrollView)?.post {
                        (output.parent as? ScrollView)?.fullScroll(View.FOCUS_DOWN)
                    }
                }
            }
        }
    }

    private fun createOverlay(activity: Activity, root: ViewGroup): View {
        val panel =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                elevation = activity.dp(12).toFloat()
            }
        if (minimized) configureCollapsed(activity, panel, root, false)
        else configureExpanded(activity, panel, root, false)
        return panel
    }

    private fun configureExpanded(
        activity: Activity,
        panel: LinearLayout,
        root: ViewGroup,
        preservePosition: Boolean,
    ) {
        val oldX = panel.x
        val oldY = panel.y
        panel.removeAllViews()
        panel.orientation = LinearLayout.VERTICAL
        panel.gravity = Gravity.NO_GRAVITY
        panel.background = roundedDrawable(PANEL, activity.dp(16).toFloat())
        val output =
            TextView(activity).apply {
                setTextColor(0xFFD8F7ED.toInt())
                textSize = 10f
                typeface = Typeface.MONOSPACE
                setPadding(activity.dp(12), activity.dp(8), activity.dp(12), activity.dp(10))
                text = synchronized(lines) { lines.joinToString("\n") }
            }
        outputs.add(WeakReference(output))

        val scroll =
            ScrollView(activity).apply {
                isFillViewport = true
                addView(output)
            }
        val header = createHeader(activity, panel, root)
        panel.addView(header, LinearLayout.LayoutParams(-1, activity.dp(42)))
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val width =
            minOf(activity.dp(310), activity.resources.displayMetrics.widthPixels - activity.dp(24))
        panel.layoutParams =
            FrameLayout.LayoutParams(width, activity.dp(230), Gravity.TOP or Gravity.END).apply {
                if (!preservePosition) {
                    topMargin = activity.dp(88)
                    marginEnd = activity.dp(12)
                }
            }
        if (preservePosition) restorePosition(panel, root, oldX, oldY)
        makeDraggable(header, panel, root)
    }

    private fun configureCollapsed(
        activity: Activity,
        panel: LinearLayout,
        root: ViewGroup,
        preservePosition: Boolean,
    ) {
        val oldX = panel.x
        val oldY = panel.y
        panel.removeAllViews()
        cleanOutputReferences()
        panel.gravity = Gravity.CENTER
        panel.background = roundedDrawable(TEAL, activity.dp(40).toFloat())
        panel.addView(
            TextView(activity).apply {
                text = "日志"
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
            },
            LinearLayout.LayoutParams(-1, -1),
        )
        panel.layoutParams =
            FrameLayout.LayoutParams(activity.dp(58), activity.dp(58), Gravity.TOP or Gravity.END)
                .apply {
                    if (!preservePosition) {
                        topMargin = activity.dp(110)
                        marginEnd = activity.dp(12)
                    }
                }
        if (preservePosition) restorePosition(panel, root, oldX, oldY)
        makeDraggable(panel, panel, root) {
            minimized = false
            configureExpanded(activity, panel, root, true)
            append("日志悬浮窗已展开")
        }
    }

    private fun restorePosition(panel: View, root: ViewGroup, x: Float, y: Float) {
        panel.post {
            panel.x = x.coerceIn(0f, (root.width - panel.width).coerceAtLeast(0).toFloat())
            panel.y = y.coerceIn(0f, (root.height - panel.height).coerceAtLeast(0).toFloat())
        }
    }

    private fun createHeader(context: Activity, panel: LinearLayout, root: ViewGroup): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(12), 0, context.dp(5), 0)
            background = roundedDrawable(TEAL, context.dp(16).toFloat())

            addView(
                TextView(context).apply {
                    text = "实时日志"
                    setTextColor(Color.WHITE)
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                },
                LinearLayout.LayoutParams(0, -2, 1f),
            )
            addView(
                headerButton(context, "清空") {
                    synchronized(lines) { lines.clear() }
                    append("日志已清空")
                }
            )
            addView(
                headerButton(context, "—") {
                    minimized = true
                    configureCollapsed(context, panel, root, true)
                }
            )
            addView(headerButton(context, "×") { hide(panel.context) })
        }
    }

    private fun headerButton(context: Context, label: String, action: () -> Unit): View {
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = if (label == "×") 22f else 11f
            setPadding(context.dp(8), 0, context.dp(8), 0)
            setOnClickListener { action() }
        }
    }

    private fun makeDraggable(
        handle: View,
        panel: View,
        root: ViewGroup,
        onTap: (() -> Unit)? = null,
    ) {
        var downX = 0f
        var downY = 0f
        var startX = 0f
        var startY = 0f
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = panel.x
                    startY = panel.y
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val maxX = (root.width - panel.width).coerceAtLeast(0).toFloat()
                    val maxY = (root.height - panel.height).coerceAtLeast(0).toFloat()
                    panel.x = (startX + event.rawX - downX).coerceIn(0f, maxX)
                    panel.y = (startY + event.rawY - downY).coerceIn(0f, maxY)
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    val moved =
                        abs(event.rawX - downX) + abs(event.rawY - downY)
                    if (
                        event.actionMasked == MotionEvent.ACTION_UP && moved < panel.context.dp(8)
                    ) {
                        onTap?.invoke()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun cleanOutputReferences() {
        outputs.removeAll { it.get()?.isAttachedToWindow != true }
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action()
        else Handler(Looper.getMainLooper()).post(action)
    }

    private fun roundedDrawable(color: Int, radius: Float) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }

    private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private tailrec fun Context.findActivity(): Activity? =
        when (this) {
            is Activity -> this
            is ContextWrapper -> baseContext.findActivity()
            else -> null
        }
}