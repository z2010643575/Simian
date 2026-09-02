package com.log.simianv2.dialog

import android.R
import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.log.simianv2.service.HostScoreService
import com.log.simianv2.settings.HostSettingsStore
import com.log.simianv2.window.HostLogOverlay

/** Settings panel displayed inside the hooked host application. */
object HostSettingsDialog {
    private const val TEAL = 0xFF006A66.toInt()
    private const val MINT = 0xFFCEFAEA.toInt()
    private const val INK = 0xFF071719.toInt()
    private const val MUTED = 0xFF667375.toInt()
    private const val DIVIDER = 0xFFE5E9E8.toInt()

    fun show(context: Context) {
        val activity = context.findActivity() ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showOnMainThread(activity)
        } else {
            Handler(Looper.getMainLooper()).post { showOnMainThread(activity) }
        }
    }

    private fun showOnMainThread(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setContentView(createContent(activity, dialog))
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            setDimAmount(0.48f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply {
                width = (activity.resources.displayMetrics.widthPixels * 0.91f).toInt()
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.CENTER
            }
        }
        dialog.setOnShowListener {
            dialog.findViewById<View>(CONTENT_ID)?.apply {
                alpha = 0f
                scaleX = 0.94f
                scaleY = 0.94f
                animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180L).start()
            }
        }
        dialog.show()
    }

    private fun createContent(context: Context, dialog: Dialog): View {
        val content =
            LinearLayout(context).apply {
                id = CONTENT_ID
                orientation = LinearLayout.VERTICAL
                setPadding(context.dp(20), context.dp(18), context.dp(20), context.dp(18))
                background = roundedDrawable(Color.WHITE, context.dp(26).toFloat())
            }

        content.addView(createHeader(context, dialog))
        content.addView(space(context, 18))

        val scroll =
            ScrollView(context).apply {
                isFillViewport = true
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            }
        val settings = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        settings.addView(sectionTitle(context, "答题"))
        settings.addView(
            settingRow(
                context,
                "快速答题",
                "自动提交正确答案(速度更快)",
                HostSettingsStore.QUICK_ANSWER
            )
        )
        settings.addView(divider(context))
        settings.addView(
            actionRow(context, "自动化", "自动答题与页面自动点击") {
                showAutomationSettings(context)
            }
        )
        settings.addView(divider(context))
        settings.addView(editableSettingRow(context, CUSTOM_ANSWER_SPEC))
        settings.addView(divider(context))
        settings.addView(editableSettingRow(context, QUESTION_COUNT_SPEC))
        settings.addView(divider(context))
        settings.addView(editableSettingRow(context, END_TIME_SPEC))
        settings.addView(divider(context))
        settings.addView(
            settingRow(
                context,
                "名字无限制",
                "解除名字长度与字符限制",
                HostSettingsStore.UNLIMITED_NAME
            )
        )
        settings.addView(divider(context))
        settings.addView(
            actionRow(
                context,
                "自定义分数",
                "查询当前分数并提交增加值"
            ) { showScoreEditor(context) })

        settings.addView(space(context, 18))
        settings.addView(sectionTitle(context, "调试工具"))
        settings.addView(
            settingRow(
                context,
                "日志悬浮窗",
                "在宿主界面显示可拖动的实时日志",
                HostSettingsStore.LOG_OVERLAY
            ) { enabled
                ->
                if (enabled) HostLogOverlay.show(context) else HostLogOverlay.hide(context)
            }
        )
        settings.addView(space(context, 18))
        settings.addView(sectionTitle(context, "其他"))
        settings.addView(actionRow(context, "更新记录", "查看版本功能与改动") {
            showUpdateLog(
                context
            )
        })
        settings.addView(divider(context))
        settings.addView(actionRow(context, "联系方式", "QQ群：994173459") {
            showContactDialog(context)
        })
        settings.addView(space(context, 12))
        settings.addView(
            TextView(context).apply {
                text = "Simian 模块设置 · 配置已自动保存"
                setTextColor(MUTED)
                textSize = 11f
                gravity = Gravity.CENTER
                setPadding(0, context.dp(8), 0, context.dp(2))
            }
        )

        scroll.addView(settings)
        content.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(390)),
        )
        return content
    }

    private fun createHeader(context: Context, dialog: Dialog): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            addView(
                TextView(context).apply {
                    text = "S"
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    textSize = 22f
                    typeface = Typeface.DEFAULT_BOLD
                    background = roundedDrawable(TEAL, context.dp(14).toFloat())
                },
                LinearLayout.LayoutParams(context.dp(48), context.dp(48)),
            )

            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(context.dp(14), 0, 0, 0)
                    addView(title(context, "模块设置", 20f))
                    addView(body(context, "小猿口算增强功能", 12f))
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )

            addView(
                TextView(context).apply {
                    text = "×"
                    gravity = Gravity.CENTER
                    setTextColor(MUTED)
                    textSize = 27f
                    background = roundedDrawable(0xFFF1F3F2.toInt(), context.dp(20).toFloat())
                    setOnClickListener { dialog.dismiss() }
                },
                LinearLayout.LayoutParams(context.dp(40), context.dp(40)),
            )
        }
    }

    private fun settingRow(
        context: Context,
        name: String,
        summary: String,
        key: String,
        onChanged: (Boolean) -> Unit = {},
    ): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(4), context.dp(13), 0, context.dp(13))
            isClickable = true

            val textArea =
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(title(context, name, 15f))
                    addView(body(context, summary, 12f))
                }
            val toggle =
                createSwitch(
                    context,
                    HostSettingsStore.get(context, key, false) as Boolean
                ) { checked ->
                    HostSettingsStore.put(context, key, checked)
                    onChanged(checked)
                    HostLogOverlay.append("$name: ${if (checked) "开启" else "关闭"}")
                }
            setOnClickListener { toggle.isChecked = !toggle.isChecked }
            addView(textArea, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(toggle, LinearLayout.LayoutParams(context.dp(52), context.dp(40)))
        }
    }

    private fun editableSettingRow(context: Context, spec: EditableSettingSpec): View {

        val summaryView = body(
            context,
            spec.summary(readSettingValue(context, spec)),
            12f
        )
        lateinit var row: LinearLayout
        val textArea =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(title(context, spec.title, 15f))
                addView(summaryView)
            }
        val updateState: (Boolean) -> Unit = { enabled ->
            textArea.alpha = if (enabled) 1f else 0.45f
            row.isClickable = enabled
            row.setOnClickListener(
                if (enabled) {
                    View.OnClickListener {
                        showValueEditor(context, spec) { value ->
                            summaryView.text = spec.summary(value)
                        }
                    }
                } else null
            )
        }
        val toggle =
            createSwitch(
                context,
                HostSettingsStore.get(context, spec.enabledKey, false) as Boolean
            ) { checked
                ->
                HostSettingsStore.put(context, spec.enabledKey, checked)
                updateState(checked)
                HostLogOverlay.append("${spec.title}: ${if (checked) "开启" else "关闭"}")
            }
        row =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(context.dp(4), context.dp(13), 0, context.dp(13))
                addView(
                    textArea,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
                addView(toggle, LinearLayout.LayoutParams(context.dp(52), context.dp(40)))
            }
        updateState(toggle.isChecked)
        return row
    }

    private fun showValueEditor(
        context: Context,
        spec: EditableSettingSpec,
        onSaved: (String) -> Unit,
    ) {
        val activity = context.findActivity() ?: return
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val editor =
            EditText(activity).apply {
                setText(readSettingValue(context, spec))
                hint = spec.hint
                inputType = spec.inputType
                setTextColor(INK)
                setHintTextColor(0xFF98A3A2.toInt())
                textSize = 15f
                minLines = spec.minLines
                maxLines = spec.maxLines
                gravity =
                    if (spec.maxLines > 1) Gravity.TOP or Gravity.START else Gravity.CENTER_VERTICAL
                setPadding(activity.dp(14), activity.dp(12), activity.dp(14), activity.dp(12))
                background = roundedDrawable(0xFFF2F6F5.toInt(), activity.dp(14).toFloat())
                setSelection(text.length)
            }
        val content =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(activity.dp(20), activity.dp(18), activity.dp(20), activity.dp(16))
                background = roundedDrawable(Color.WHITE, activity.dp(24).toFloat())
                addView(title(activity, spec.title, 20f))
                addView(space(activity, 5))
                addView(body(activity, spec.description, 12f))
                addView(space(activity, 14))
                addView(editor, LinearLayout.LayoutParams(-1, -2))
                addView(space(activity, 14))
                addView(
                    LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.END
                        addView(editorButton(activity, "取消", false) { dialog.dismiss() })
                        addView(spaceWidth(activity, 10))
                        addView(
                            editorButton(activity, "保存", true) {
                                val rawValue = editor.text.toString().trim()
                                spec.validate(rawValue)?.let { error ->
                                    editor.error = error
                                    return@editorButton
                                }
                                val value = spec.normalize(rawValue)
                                saveSettingValue(activity, spec, value)
                                HostLogOverlay.append("${spec.title}已保存: $value")
                                onSaved(value)
                                dialog.dismiss()
                            }
                        )
                    }
                )
            }
        dialog.setContentView(content)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.86f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun actionRow(
        context: Context,
        name: String,
        summary: String,
        onClick: () -> Unit,
    ): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(4), context.dp(13), context.dp(4), context.dp(13))
            isClickable = true
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(title(context, name, 15f))
                    addView(body(context, summary, 12f))
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                TextView(context).apply {
                    text = "›"
                    textSize = 25f
                    gravity = Gravity.CENTER
                    setTextColor(MUTED)
                },
                LinearLayout.LayoutParams(context.dp(32), context.dp(40)),
            )
            setOnClickListener { onClick() }
        }

    private fun createSwitch(
        context: Context,
        checked: Boolean,
        onChanged: (Boolean) -> Unit,
    ): Switch =
        Switch(context).apply {
            isChecked = checked
            showText = false
            buttonTintList = null
            thumbTintList =
                ColorStateList(
                    arrayOf(intArrayOf(R.attr.state_checked), intArrayOf()),
                    intArrayOf(Color.WHITE, 0xFFF7F8F8.toInt()),
                )
            trackTintList =
                ColorStateList(
                    arrayOf(intArrayOf(R.attr.state_checked), intArrayOf()),
                    intArrayOf(TEAL, 0xFFB9C1C0.toInt()),
                )
            setOnCheckedChangeListener { _, value -> onChanged(value) }
        }

    private fun showAutomationSettings(context: Context) {
        val activity = context.findActivity() ?: return
        val automationDialog = Dialog(activity)
        automationDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val rows =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(editableSettingRow(activity, AUTO_ANSWER_SPEC))
                addView(divider(activity))
                addView(
                    settingRow(
                        activity,
                        "自动点击开心收下",
                        "结算页面出现后自动点击开心收下",
                        HostSettingsStore.AUTO_CLICK_HAPPY_ACCEPT,
                    )
                )
                addView(divider(activity))
                addView(
                    settingRow(
                        activity,
                        "自动点击继续",
                        "页面出现继续按钮后自动点击",
                        HostSettingsStore.AUTO_CLICK_CONTINUE,
                    )
                )
                addView(divider(activity))
                addView(
                    settingRow(
                        activity,
                        "自动点击继续PK",
                        "结算页面出现继续PK后自动点击",
                        HostSettingsStore.AUTO_CLICK_CONTINUE_PK,
                    )
                )
            }

        val content =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(activity.dp(20), activity.dp(18), activity.dp(20), activity.dp(16))
                background = roundedDrawable(Color.WHITE, activity.dp(24).toFloat())
                addView(title(activity, "自动化", 20f))
                addView(space(activity, 4))
                addView(body(activity, "配置自动答题速度和页面自动点击", 12f))
                addView(space(activity, 10))
                addView(rows)
                addView(space(activity, 12))
                addView(
                    LinearLayout(activity).apply {
                        gravity = Gravity.END
                        addView(
                            editorButton(activity, "关闭", true) {
                                automationDialog.dismiss()
                            }
                        )
                    }
                )
            }

        automationDialog.setContentView(content)
        automationDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        automationDialog.show()
        automationDialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.88f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun showContactDialog(context: Context) {
        val activity = context.findActivity() ?: return
        val contactDialog = Dialog(activity)
        contactDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val content =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(activity.dp(20), activity.dp(18), activity.dp(20), activity.dp(16))
                background = roundedDrawable(Color.WHITE, activity.dp(24).toFloat())
                addView(title(activity, "联系方式", 20f))
                addView(space(activity, 6))
                addView(body(activity, "欢迎加入 QQ 群交流、反馈问题和获取更新", 12f))
                addView(space(activity, 16))
                addView(
                    LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(
                            activity.dp(16),
                            activity.dp(14),
                            activity.dp(16),
                            activity.dp(14),
                        )
                        background = roundedDrawable(MINT, activity.dp(16).toFloat())
                        addView(body(activity, "QQ 群", 12f))
                        addView(space(activity, 3))
                        addView(title(activity, QQ_GROUP_NUMBER, 21f))
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
                addView(space(activity, 16))
                addView(
                    LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.END
                        addView(
                            editorButton(activity, "关闭", false) {
                                contactDialog.dismiss()
                            }
                        )
                        addView(spaceWidth(activity, 10))
                        addView(
                            editorButton(activity, "复制群号", true) {
                                activity.getSystemService(ClipboardManager::class.java)
                                    .setPrimaryClip(
                                        ClipData.newPlainText("Simian QQ群", QQ_GROUP_NUMBER)
                                    )
                                Toast.makeText(activity, "群号已复制", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                )
            }

        contactDialog.setContentView(content)
        contactDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        contactDialog.show()
        contactDialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.86f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun showUpdateLog(context: Context) {
        val activity = context.findActivity() ?: return
        val updateDialog = Dialog(activity)
        updateDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val v11Changes =
            listOf(
                "新增自动化功能",
                "新增自动答题，可自定义答题速度",
                "新增自动点击开心收下",
                "新增自动点击继续",
                "新增自动点击继续PK",
                "自动答题需要同时开启“快速答题” 和 ”自定义题数(1)“功能",
            )
        val v10Changes =
            listOf(
                "全新模块设置弹窗",
                "支持快速答题与自定义答案",
                "支持日志悬浮窗与圆形收起模式",
                "配置保存至宿主私有 JSON 文件",
                "新增自定义分数查询与提交",
            )

        fun versionHeader(version: String, date: String): View =
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(title(activity, version, 16f))
                addView(
                    body(activity, date, 11f).apply { gravity = Gravity.END },
                    LinearLayout.LayoutParams(0, -2, 1f),
                )
            }

        fun changeList(changes: List<String>): View =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                changes.forEach { change ->
                    addView(
                        body(activity, "• $change", 13f).apply {
                            setPadding(0, activity.dp(5), 0, activity.dp(5))
                        }
                    )
                }
            }

        val records =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(versionHeader("v1.1", "2026.09.02"))
                addView(space(activity, 8))
                addView(changeList(v11Changes))
                addView(space(activity, 14))
                addView(divider(activity))
                addView(space(activity, 14))
                addView(versionHeader("v1.0", "2026.08.28"))
                addView(space(activity, 8))
                addView(changeList(v10Changes))
            }
        val content =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(activity.dp(20), activity.dp(18), activity.dp(20), activity.dp(16))
                background = roundedDrawable(Color.WHITE, activity.dp(24).toFloat())
                addView(title(activity, "更新记录", 20f))
                addView(space(activity, 14))
                addView(
                    ScrollView(activity).apply { addView(records) },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        activity.dp(360),
                    ),
                )
                addView(space(activity, 14))
                addView(
                    LinearLayout(activity).apply {
                        gravity = Gravity.END
                        addView(editorButton(activity, "关闭", true) { updateDialog.dismiss() })
                    }
                )
            }
        updateDialog.setContentView(content)
        updateDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        updateDialog.show()
        updateDialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.86f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun showScoreEditor(context: Context) {
        val activity = context.findActivity() ?: return
        val scoreDialog = Dialog(activity)
        scoreDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        var currentScore: Int? = null

        val currentView = body(activity, "当前分数：加载中", 14f)
        val expectedView = body(activity, "预计目标分数：加载中", 14f)
        val statusView = body(activity, "", 12f)
        val editor =
            EditText(activity).apply {
                hint = "请输入增加的分数"
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                setTextColor(INK)
                setHintTextColor(0xFF98A3A2.toInt())
                textSize = 15f
                setPadding(activity.dp(14), activity.dp(12), activity.dp(14), activity.dp(12))
                background = roundedDrawable(0xFFF2F6F5.toInt(), activity.dp(14).toFloat())
            }
        lateinit var submitButton: TextView

        fun updateExpectedScore() {
            val increase = editor.text.toString().toLongOrNull()
            val current = currentScore
            val valid =
                increase != null &&
                        increase in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() &&
                        current != null
            submitButton.isEnabled = valid
            submitButton.alpha = if (valid) 1f else 0.45f
            expectedView.text =
                if (valid) "预计目标分数：${current!! + increase!!.toInt()}"
                else if (current != null) "预计目标分数：$current" else "预计目标分数：加载中"
        }

        submitButton =
            editorButton(activity, "提交", true) {
                val score = editor.text.toString().toIntOrNull() ?: return@editorButton
                submitButton.isEnabled = false
                statusView.text = "正在提交…"
                HostScoreService.addScore(score) { result ->
                    activity.runOnUiThread {
                        result
                            .onSuccess {
                                statusView.text = "提交成功，正在刷新分数…"
                                HostScoreService.getCurrentScore { scoreResult ->
                                    activity.runOnUiThread {
                                        scoreResult
                                            .onSuccess {
                                                currentScore = it
                                                currentView.text = "当前分数：$it"
                                                statusView.text = "提交成功"
                                                editor.text = null
                                            }
                                            .onFailure {
                                                statusView.text = "刷新失败：${it.message}"
                                            }
                                        updateExpectedScore()
                                    }
                                }
                            }
                            .onFailure {
                                statusView.text = "提交失败：${it.message}"
                                updateExpectedScore()
                            }
                    }
                }
            }
        submitButton.isEnabled = false
        submitButton.alpha = 0.45f
        editor.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) =
                    Unit

                override fun afterTextChanged(s: Editable?) = updateExpectedScore()
            }
        )

        val content =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(activity.dp(20), activity.dp(18), activity.dp(20), activity.dp(16))
                background = roundedDrawable(Color.WHITE, activity.dp(24).toFloat())
                addView(title(activity, "自定义分数", 20f))
                addView(space(activity, 10))
                addView(currentView)
                addView(space(activity, 4))
                addView(expectedView)
                addView(space(activity, 12))
                addView(editor, LinearLayout.LayoutParams(-1, -2))
                addView(space(activity, 8))
                addView(statusView)
                addView(space(activity, 12))
                addView(
                    LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.END
                        addView(editorButton(activity, "关闭", false) { scoreDialog.dismiss() })
                        addView(spaceWidth(activity, 10))
                        addView(submitButton)
                    }
                )
            }
        scoreDialog.setContentView(content)
        scoreDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        scoreDialog.show()
        scoreDialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.86f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
        )

        HostScoreService.getCurrentScore { result ->
            activity.runOnUiThread {
                result
                    .onSuccess {
                        currentScore = it
                        currentView.text = "当前分数：$it"
                        statusView.text = ""
                    }
                    .onFailure {
                        currentView.text = "当前分数：获取失败"
                        statusView.text = it.message ?: "刷分接口不可用"
                    }
                updateExpectedScore()
            }
        }
    }

    private fun editorButton(
        context: Context,
        label: String,
        primary: Boolean,
        action: () -> Unit,
    ) =
        TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(if (primary) Color.WHITE else TEAL)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            background = roundedDrawable(if (primary) TEAL else MINT, context.dp(18).toFloat())
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(context.dp(76), context.dp(40))
        }

    private fun answerSummary(answer: String): String =
        if (answer.isBlank()) "未设置，点击输入" else answer.replace('\n', ' ').take(24)

    private fun questionCountSummary(count: String): String =
        if (count.isBlank()) "未设置，点击输入" else "当前设置：$count 题"

    private fun endTimeSummary(value: String): String =
        if (value.isBlank()) "未设置，点击输入" else "当前设置：$value"

    private fun autoAnswerSpeedSummary(value: String): String =
        "答题速度：${value.ifBlank { DEFAULT_AUTO_ANSWER_SPEED.toString() }} 毫秒"

    private fun readSettingValue(context: Context, spec: EditableSettingSpec): String =
        HostSettingsStore.get(context, spec.valueKey, spec.defaultValue).toString()

    private fun saveSettingValue(context: Context, spec: EditableSettingSpec, value: String) {
        val storedValue: Any =
            when (spec.storageType) {
                SettingStorageType.STRING -> value
                SettingStorageType.LONG -> value.toLong()
                SettingStorageType.INT -> value.toInt()
            }
        HostSettingsStore.put(context, spec.valueKey, storedValue)
    }

    private fun spaceWidth(context: Context, width: Int) =
        Space(context).apply {
            layoutParams = LinearLayout.LayoutParams(context.dp(width), context.dp(1))
        }

    private fun sectionTitle(context: Context, value: String) =
        TextView(context).apply {
            text = value
            setTextColor(TEAL)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(context.dp(4), context.dp(4), 0, context.dp(5))
        }

    private fun title(context: Context, value: String, size: Float) =
        TextView(context).apply {
            text = value
            setTextColor(INK)
            textSize = size
            typeface = Typeface.DEFAULT_BOLD
        }

    private fun body(context: Context, value: String, size: Float) =
        TextView(context).apply {
            text = value
            setTextColor(MUTED)
            textSize = size
        }

    private fun divider(context: Context) =
        View(context)
            .apply { setBackgroundColor(DIVIDER) }
            .also {
                it.layoutParams =
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(1))
            }

    private fun space(context: Context, height: Int) =
        Space(context).apply {
            layoutParams =
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(height))
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

    private data class EditableSettingSpec(
        val title: String,
        val enabledKey: String,
        val valueKey: String,
        val description: String,
        val hint: String,
        val inputType: Int = InputType.TYPE_CLASS_TEXT,
        val minLines: Int = 1,
        val maxLines: Int = 1,
        val storageType: SettingStorageType = SettingStorageType.STRING,
        val defaultValue: Any = "",
        val summary: (String) -> String,
        val validate: (String) -> String? = { null },
        val normalize: (String) -> String = { it },
    )

    private enum class SettingStorageType {
        STRING,
        LONG,
        INT
    }

    private val AUTO_ANSWER_SPEC =
        EditableSettingSpec(
            title = "自动答题",
            enabledKey = HostSettingsStore.AUTO_ANSWER_ENABLED,
            valueKey = HostSettingsStore.AUTO_ANSWER_SPEED,
            description = "设置每道题自动作答前等待的时间，单位为毫秒",
            hint = "请输入答题速度，例如 9000(如果速度过快，可能导致失效)",
            inputType = InputType.TYPE_CLASS_NUMBER,
            storageType = SettingStorageType.LONG,
            defaultValue = DEFAULT_AUTO_ANSWER_SPEED,
            summary = ::autoAnswerSpeedSummary,
            validate = { value ->
                if (value.toLongOrNull()?.let { it > 0L } == true) {
                    null
                } else {
                    "请输入大于 0 的毫秒数"
                }
            },
            normalize = { it.toLong().toString() },
        )

    private val CUSTOM_ANSWER_SPEC =
        EditableSettingSpec(
            title = "自定义答案",
            enabledKey = HostSettingsStore.CUSTOM_ANSWER_ENABLED,
            valueKey = HostSettingsStore.CUSTOM_ANSWER,
            description = "保存后将在宿主中持续生效",
            hint = "请输入自定义答案",
            minLines = 3,
            maxLines = 6,
            summary = ::answerSummary,
        )

    private val QUESTION_COUNT_SPEC =
        EditableSettingSpec(
            title = "自定义题数",
            enabledKey = HostSettingsStore.CUSTOM_QUESTION_COUNT_ENABLED,
            valueKey = HostSettingsStore.CUSTOM_QUESTION_COUNT,
            description = "请输入大于 0 的整数",
            hint = "请输入题目数量",
            inputType = InputType.TYPE_CLASS_NUMBER,
            storageType = SettingStorageType.INT,
            summary = ::questionCountSummary,
            validate = { value ->
                if (value.toIntOrNull()?.let { it > 0 } == true) null else "请输入大于 0 的整数"
            },
            normalize = { it.toInt().toString() },
        )

    private val END_TIME_SPEC =
        EditableSettingSpec(
            title = "自定义结束时间",
            enabledKey = HostSettingsStore.CUSTOM_END_TIME_ENABLED,
            valueKey = HostSettingsStore.CUSTOM_END_TIME,
            description = "请输入大于 0 的整数",
            hint = "请输入结束时间",
            inputType = InputType.TYPE_CLASS_NUMBER,
            storageType = SettingStorageType.LONG,
            summary = ::endTimeSummary,
            validate = { value ->
                if (value.toLongOrNull()
                        ?.let { it > 0L } == true
                ) null else "请输入有效的 Long 正整数"
            },
            normalize = { it.toLong().toString() },
        )

    private const val CONTENT_ID = 0x53494D49
    private const val DEFAULT_AUTO_ANSWER_SPEED = 9000L
    private const val QQ_GROUP_NUMBER = "994173459"
}
