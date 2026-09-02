package com.log.simianv2

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.log.simianv2.ui.theme.SimianV2Theme
import io.github.libxposed.service.XposedService

private val Ink = Color(0xFF071719)
private val Teal = Color(0xFF006A66)
private val Mint = Color(0xFFCEFAEA)
private val Page = Color(0xFFFAFBFA)
private val Muted = Color(0xFF566466)

private data class ModuleStatus(
    val active: Boolean = false,
    val frameworkName: String = "未连接",
    val frameworkVersion: String = "--",
    val apiVersion: String = "--",
) {
    companion object {
        fun from(service: XposedService?): ModuleStatus {
            if (service == null) return ModuleStatus()
            return try {
                ModuleStatus(
                    active = true,
                    frameworkName = service.frameworkName.ifBlank { "Xposed" },
                    frameworkVersion = service.frameworkVersion.ifBlank { "--" },
                    apiVersion = service.apiVersion.toString(),
                )
            } catch (_: Throwable) {
                ModuleStatus()
            }
        }
    }
}

lateinit var action: (String) -> Unit


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SimianV2Theme { App() } }
        action = { url ->
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
            }
            startActivity(intent)
        }
    }
}

@Composable
fun App() {
    var showAbout by remember { mutableStateOf(false) }
    var moduleStatus by remember { mutableStateOf(ModuleStatus.from(MyApp.mService)) }
    DisposableEffect(Unit) {
        val mainHandler = Handler(Looper.getMainLooper())
        val listener =
            object : MyApp.ServiceStateListener {
                override fun onServiceStateChanged(service: XposedService?) {
                    val newStatus = ModuleStatus.from(service)
                    mainHandler.post { moduleStatus = newStatus }
                }
            }
        MyApp.addServiceStateListener(listener, notifyImmediately = true)
        onDispose { MyApp.removeServiceStateListener(listener) }
    }
    val context = LocalContext.current

    Scaffold(containerColor = Page) { inset ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(
                    top = inset.calculateTopPadding() + 22.dp,
                    bottom = inset.calculateBottomPadding() + 22.dp,
                )
        ) {
            Header { showAbout = true }
            Spacer(Modifier.height(30.dp))
            StatusCard(moduleStatus)
            Heading("快捷操作")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickCard(
                    Modifier.weight(1.45f),
                    "启动小猿",
                    "重新载入模块环境",
                    true,
                    Icon.Launch
                ) {
                    val intent: Intent? =
                        context.packageManager.getLaunchIntentForPackage("com.fenbi.android.leo")
                    if (intent != null) context.startActivity(intent)
                }

                QuickCard(Modifier.weight(1f), "模块设置", "功能与偏好", false, Icon.Tune) {
                    Toast.makeText(context, "前往小猿口算进入", Toast.LENGTH_LONG).show()
                }
            }
            Heading("运行概况")
            Overview(moduleStatus)
            Spacer(Modifier.height(15.dp))
            Heading("项目与支持")
            Support(action)
        }
    }
    if (showAbout) AboutDialog { showAbout = false }
}

@Composable
private fun Header(onAbout: () -> Unit) =
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Teal),
            contentAlignment = Alignment.Center,
        ) {
            Mark(Modifier.size(34.dp))
        }
        Spacer(Modifier.width(17.dp))
        Column(Modifier.weight(1f)) {
            Text("Simian", fontSize = 25.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text("小猿口算增强模块", fontSize = 14.sp, color = Muted)
        }
        var expanded by remember { mutableStateOf(false) }
        Box {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF0F1F0))
                    .clickable {
                        expanded = true
                    },
                contentAlignment = Alignment.Center,
            ) {
                More(Modifier.size(22.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("关于", color = Ink) },
                    leadingIcon = { DrawIcon(Icon.Info, Modifier.size(20.dp), Teal) },
                    onClick = {
                        expanded = false
                        onAbout()
                    },
                )
            }
        }
    }

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White,
        icon = {
            Box(
                Modifier
                    .size(54.dp)
                    .background(Teal, RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Mark(Modifier.size(37.dp))
            }
        },
        title = { Text("关于 Simian", color = Ink, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("小猿口算增强模块", color = Muted)
                Spacer(Modifier.height(8.dp))
                Text("版本 " + MyApp.appVersion, color = Ink, fontSize = 14.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("确定", color = Teal) } },
    )
}

@Composable
private fun StatusCard(status: ModuleStatus) {
    val cardColor = if (status.active) Teal else Color(0xFF4F5D5D)
    val badgeColor = if (status.active) Mint else Color(0xFFE5E9E8)
    Box(
        Modifier
            .fillMaxWidth()
            .height(133.dp)
            .shadow(9.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(cardColor)
    ) {
        Canvas(Modifier.matchParentSize()) {
            val center = Offset(size.width * .91f, size.height * .48f)
            listOf(72f, 52f, 34f).forEach {
                drawCircle(
                    Color.White.copy(.08f),
                    it.dp.toPx(),
                    center,
                    style = Stroke(1.dp.toPx()),
                )
            }
        }
        Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(30.dp))
                    .background(badgeColor)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(if (status.active) Teal else Color(0xFF7A8585), CircleShape)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    if (status.active) "运行正常" else "服务未连接",
                    color = if (status.active) Teal else Color(0xFF566161),
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                if (status.active) "模块已激活" else "模块未激活",
                color = Color.White,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${status.frameworkName} · API ${status.apiVersion}",
                color = Color.White.copy(.88f),
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "v" + MyApp.appVersion,
                color = Color.White,
                fontSize = 12.sp,
                modifier =
                    Modifier
                        .border(1.dp, Color.White, RoundedCornerShape(20.dp))
                        .padding(horizontal = 11.dp, vertical = 2.dp),
            )
        }
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 17.dp)
                .size(46.dp)
                .shadow(8.dp, CircleShape)
                .background(badgeColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (status.active) Check(Modifier.size(26.dp)) else Cross(Modifier.size(23.dp))
        }
    }
}

@Composable
private fun Heading(text: String) =
    Text(
        text,
        Modifier.padding(top = 28.dp, bottom = 14.dp, start = 2.dp),
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        color = Ink,
    )

@Composable
private fun QuickCard(
    modifier: Modifier,
    title: String,
    subtitle: String,
    primary: Boolean,
    icon: Icon,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(114.dp)
            .shadow(7.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(if (primary) Color(0xFFC4F7E7) else Color.White)
            .clickable(onClick = onClick)
            .padding(if (primary) 13.dp else 14.dp)
    ) {
        if (primary)
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(49.dp)
                        .background(Teal, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    DrawIcon(icon, Modifier.size(27.dp), Color.White)
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = Ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                    Text(subtitle, color = Muted, fontSize = 10.sp, maxLines = 1)
                }
                Chevron(Modifier.size(16.dp))
            }
        else {
            Box(
                Modifier
                    .size(39.dp)
                    .background(Mint, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                DrawIcon(icon, Modifier.size(22.dp), Teal)
            }
            Column(Modifier.align(Alignment.BottomStart)) {
                Text(
                    title,
                    color = Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Text(subtitle, color = Muted, fontSize = 11.sp, maxLines = 1)
            }
            Box(Modifier.align(Alignment.CenterEnd)) { Chevron(Modifier.size(16.dp)) }
        }
    }
}

@Composable
private fun Overview(status: ModuleStatus) =
    Row(
        Modifier
            .fillMaxWidth()
            .height(104.dp)
            .shadow(7.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Metric(Modifier.weight(1f), Icon.Cube, "加载器", status.frameworkName)
        Divider()
        Metric(Modifier.weight(1f), Icon.Stack, "框架版本", status.frameworkVersion)
        Divider()
        Metric(Modifier.weight(1f), Icon.Code, "接口版本", "API ${status.apiVersion}")
    }

@Composable
private fun Divider() = Box(
    Modifier
        .width(1.dp)
        .height(70.dp)
        .background(Color(0xFFE2E6E5))
)

@Composable
private fun Metric(modifier: Modifier, icon: Icon, label: String, value: String) =
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        DrawIcon(icon, Modifier.size(21.dp), Teal)
        Spacer(Modifier.height(5.dp))
        Text(label, fontSize = 12.sp, color = Ink, maxLines = 1)
        Text(value, fontSize = 13.sp, color = Color(0xFF263537), maxLines = 1)
    }

@Composable
private fun Support(action: (String) -> Unit) =
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(7.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(horizontal = 15.dp)
    ) {
        SupportRow(
            Icon.Github,
            "GitHub 仓库",
            "查看源码与更新"
        ) { action("https://github.com/z2010643575/Simian") }
        HorizontalDivider(color = Color(0xFFE3E7E6))
        SupportRow(
            Icon.Chat,
            "社区交流",
            "问题反馈与讨论"
        ) { action("https://github.com/z2010643575/Simian/issues") }
    }

@Composable
private fun SupportRow(icon: Icon, title: String, subtitle: String, onClick: () -> Unit) =
    Row(
        Modifier
            .fillMaxWidth()
            .height(62.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DrawIcon(icon, Modifier.size(29.dp), Teal)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
        Chevron(Modifier.size(18.dp))
    }

private enum class Icon {
    Launch,
    Tune,
    Cube,
    Stack,
    Code,
    Github,
    Chat,
    Info,
}

@Composable
private fun DrawIcon(kind: Icon, modifier: Modifier, color: Color) =
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = size.minDimension * .085f
        val s = Stroke(sw, cap = StrokeCap.Round)
        when (kind) {
            Icon.Launch -> {
                drawRoundRect(
                    color,
                    Offset(w * .08f, h * .2f),
                    Size(w * .62f, h * .7f),
                    androidx.compose.ui.geometry.CornerRadius(w * .08f),
                    s,
                )
                drawLine(
                    color,
                    Offset(w * .48f, h * .12f),
                    Offset(w * .9f, h * .12f),
                    sw,
                    StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(w * .9f, h * .12f),
                    Offset(w * .9f, h * .52f),
                    sw,
                    StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(w * .9f, h * .12f),
                    Offset(w * .43f, h * .58f),
                    sw,
                    StrokeCap.Round,
                )
            }

            Icon.Tune -> {
                repeat(3) { i ->
                    val y = h * (.25f + i * .25f)
                    drawLine(color, Offset(w * .12f, y), Offset(w * .88f, y), sw, StrokeCap.Round)
                    val x = if (i == 1) w * .68f else w * (.35f + i * .12f)
                    drawCircle(color, sw * .9f, Offset(x, y), style = Stroke(sw * .65f))
                }
            }

            Icon.Stack -> {
                repeat(3) { i ->
                    val y = h * (.27f + i * .23f)
                    val p =
                        Path().apply {
                            moveTo(w * .15f, y)
                            lineTo(w * .5f, y + h * .18f)
                            lineTo(w * .85f, y)
                            lineTo(w * .5f, y - h * .18f)
                            close()
                        }
                    drawPath(p, color, style = s)
                }
            }

            Icon.Code -> {
                drawLine(color, Offset(w * .38f, h * .24f), Offset(w * .15f, h * .5f), sw)
                drawLine(color, Offset(w * .15f, h * .5f), Offset(w * .38f, h * .76f), sw)
                drawLine(color, Offset(w * .62f, h * .24f), Offset(w * .85f, h * .5f), sw)
                drawLine(color, Offset(w * .85f, h * .5f), Offset(w * .62f, h * .76f), sw)
            }

            Icon.Cube -> {
                val p =
                    Path().apply {
                        moveTo(w * .5f, h * .06f)
                        lineTo(w * .88f, h * .27f)
                        lineTo(w * .88f, h * .72f)
                        lineTo(w * .5f, h * .94f)
                        lineTo(w * .12f, h * .72f)
                        lineTo(w * .12f, h * .27f)
                        close()
                    }
                drawPath(p, color, style = s)
                drawCircle(color, w * .19f, Offset(w * .5f, h * .5f), style = s)
            }

            Icon.Github -> {
                drawCircle(color, w * .34f, Offset(w * .5f, h * .45f), style = s)
                drawLine(color, Offset(w * .32f, h * .72f), Offset(w * .28f, h * .92f), sw)
                drawLine(color, Offset(w * .68f, h * .72f), Offset(w * .72f, h * .92f), sw)
            }

            Icon.Chat -> {
                drawOval(color, Offset(w * .05f, h * .1f), Size(w * .67f, h * .65f), style = s)
                drawOval(color, Offset(w * .3f, h * .25f), Size(w * .65f, h * .62f), style = s)
                drawLine(color, Offset(w * .72f, h * .82f), Offset(w * .82f, h * .95f), sw)
            }

            Icon.Info -> {
                drawCircle(color, w * .4f, Offset(w * .5f, h * .5f), style = s)
                drawCircle(color, sw * .55f, Offset(w * .5f, h * .32f))
                drawLine(
                    color,
                    Offset(w * .5f, h * .48f),
                    Offset(w * .5f, h * .72f),
                    sw,
                    StrokeCap.Round,
                )
            }
        }
    }

@Composable
private fun Mark(modifier: Modifier) =
    Canvas(modifier) {
        val sw = size.width * .18f
        drawLine(
            Color.White,
            Offset(size.width * .16f, size.height * .28f),
            Offset(size.width * .4f, size.height * .76f),
            sw,
            StrokeCap.Round,
        )
        drawLine(
            Color.White,
            Offset(size.width * .4f, size.height * .76f),
            Offset(size.width * .72f, size.height * .22f),
            sw,
            StrokeCap.Round,
        )
        drawLine(
            Color.White,
            Offset(size.width * .62f, size.height * .67f),
            Offset(size.width * .82f, size.height * .55f),
            sw,
            StrokeCap.Round,
        )
    }

@Composable
private fun Check(modifier: Modifier) =
    Canvas(modifier) {
        drawLine(
            Teal,
            Offset(size.width * .18f, size.height * .52f),
            Offset(size.width * .42f, size.height * .74f),
            4.dp.toPx(),
            StrokeCap.Round,
        )
        drawLine(
            Teal,
            Offset(size.width * .42f, size.height * .74f),
            Offset(size.width * .84f, size.height * .27f),
            4.dp.toPx(),
            StrokeCap.Round,
        )
    }

@Composable
private fun Cross(modifier: Modifier) =
    Canvas(modifier) {
        val color = Color(0xFF687474)
        drawLine(
            color,
            Offset(size.width * .22f, size.height * .22f),
            Offset(size.width * .78f, size.height * .78f),
            3.dp.toPx(),
            StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(size.width * .78f, size.height * .22f),
            Offset(size.width * .22f, size.height * .78f),
            3.dp.toPx(),
            StrokeCap.Round,
        )
    }

@Composable
private fun Chevron(modifier: Modifier) =
    Canvas(modifier) {
        drawLine(
            Ink,
            Offset(size.width * .35f, size.height * .2f),
            Offset(size.width * .67f, size.height * .5f),
            2.dp.toPx(),
            StrokeCap.Round,
        )
        drawLine(
            Ink,
            Offset(size.width * .67f, size.height * .5f),
            Offset(size.width * .35f, size.height * .8f),
            2.dp.toPx(),
            StrokeCap.Round,
        )
    }

@Composable
private fun More(modifier: Modifier) =
    Canvas(modifier) {
        repeat(3) {
            drawCircle(Ink, 2.dp.toPx(), Offset(size.width * .5f, size.height * (.28f + it * .22f)))
        }
    }

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun Preview() {
    SimianV2Theme { App() }
}
