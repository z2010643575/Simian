package com.log.simianv2.dialog

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

internal class HostInitializationDialog(private val activity: Activity) {
    private val status = TextView(activity).apply {
        text = "正在初始化 DexKit…"
        textSize = 15f
        setTextColor(Color.rgb(25, 48, 48))
        gravity = Gravity.CENTER
    }
    private val dialog = Dialog(activity).apply {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setCancelable(false)
        setCanceledOnTouchOutside(false)
        setContentView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(24), dp(28), dp(22))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(22).toFloat()
            }
            addView(ProgressBar(activity), LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(status, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(14) })
        })
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    fun show() = dialog.show()
    fun update(message: String) { status.text = message }
    fun dismiss() { if (dialog.isShowing) dialog.dismiss() }

    private fun dp(value: Int) =
        (value * activity.resources.displayMetrics.density + 0.5f).toInt()
}
