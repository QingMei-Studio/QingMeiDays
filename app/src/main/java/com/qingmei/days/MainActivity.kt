package com.qingmei.days

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.qingmei.days.utils.WidgetRefresher
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.util.Consumer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.qingmei.days.components.AddEventDialog
import com.qingmei.days.components.EventCard
import com.qingmei.days.components.EventDetailScreen
import com.qingmei.days.model.LifeEvent
import com.qingmei.days.ui.theme.QingMeiDaysTheme
import com.qingmei.days.utils.DataManager
import com.qingmei.days.components.MyWidgetReceiver

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 【双保险】：每次打开 App 都重新定一次明天的闹钟
        // 这样就不怕重启手机或者更新 App 导致闹钟丢失了
        MyWidgetReceiver.scheduleNextMidnightUpdate(this)

        setContent {
            QingMeiDaysTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val scope = rememberCoroutineScope()

    val events = remember { mutableStateListOf<LifeEvent>() }
    var selectedEvent by remember { mutableStateOf<LifeEvent?>(null) }
    var pendingEventId by remember { mutableStateOf<String?>(null) }

    // 🌟 统一处理跳转逻辑
    fun tryOpenEvent(id: String) {
        if (events.isNotEmpty()) {
            val found = events.find { it.id == id }
            if (found != null) {
                // 找到人了，直接打开！
                selectedEvent = found
                pendingEventId = null
            } else {
            }
        } else {
            // 数据还没好，先存着
            pendingEventId = id
        }
    }

    // 🌟 解析 Intent 并弹窗提示
    fun parseIntent(intent: Intent?, source: String) {
        val targetId = intent?.getStringExtra("target_id")

        if (targetId != null) {
            // 尝试去打开
            tryOpenEvent(targetId)
            // 清除，防止重复
            intent.removeExtra("target_id")
        }
    }

    // 1. 初始化 & 冷启动检查
    LaunchedEffect(Unit) {
        // 检查是不是刚启动就带了 ID
        parseIntent(activity?.intent, "冷启动")

        withContext(Dispatchers.IO) {
            val savedList = DataManager.loadEvents(context)
            withContext(Dispatchers.Main) {
                if (savedList.isNotEmpty()) {
                    events.addAll(savedList)
                    val sorted = events.sortedWith(compareByDescending<LifeEvent> { it.isTop }.thenBy { it.date })
                    events.clear()
                    events.addAll(sorted)
                }
            }
        }
    }

    // 2. 🌟 核心：监听热启动 (App 在后台时被点开)
    DisposableEffect(Unit) {
        val listener = Consumer<Intent> { newIntent ->
            // 这一步非常关键：更新 Activity 的 Intent
            activity?.intent = newIntent
            parseIntent(newIntent, "热启动")
        }
        activity?.addOnNewIntentListener(listener)
        onDispose { activity?.removeOnNewIntentListener(listener) }
    }

    // 3. 补救措施：如果刚才数据没加载完，现在加载完了，赶紧补救
    LaunchedEffect(events.size) {
        if (pendingEventId != null && events.isNotEmpty()) {
            tryOpenEvent(pendingEventId!!)
        }
    }


    fun saveAndRefresh() {
        // 1. 先排序 (排序逻辑决定了“取消置顶”后谁排第一)
        val sorted = events.sortedWith(
            compareByDescending<LifeEvent> { it.isTop }.thenBy { it.date }
        )
        events.clear()
        events.addAll(sorted)

        scope.launch {
            // 2. 核心：在 IO 线程完成所有“存”的操作，并等待其结束
            withContext(Dispatchers.IO) {
                // 这行代码执行完，意味着数据 100% 已经写进 DataStore 了
                DataManager.saveAndSyncWidget(context, events)
            }

            // 3. 数据存好了，再通知 Widget 刷新
            WidgetRefresher.refresh(context)
        }
    }


    var showDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<LifeEvent?>(null) }
    var eventToDelete by remember { mutableStateOf<LifeEvent?>(null) }

    if (selectedEvent != null) {
        BackHandler { selectedEvent = null }

        EventDetailScreen(
            event = selectedEvent!!,
            onBack = { selectedEvent = null },
            onDelete = {
                events.remove(selectedEvent)
                selectedEvent = null
                saveAndRefresh()
            },
            onEdit = {
                editTarget = selectedEvent
                showDialog = true
            },
            onUpdateImage = { newUri ->
                val index = events.indexOf(selectedEvent)
                if (index != -1) {
                    val updated = selectedEvent!!.copy(imageUri = newUri)
                    events[index] = updated
                    selectedEvent = updated
                    saveAndRefresh()
                }
            },
            onToggleTop = {
                val index = events.indexOf(selectedEvent)
                if (index != -1) {
                    val updated = selectedEvent!!.copy(isTop = !selectedEvent!!.isTop)
                    events[index] = updated
                    selectedEvent = updated
                    saveAndRefresh()
                }
            }
        )
    } else {
        Scaffold(
            containerColor = Color(0xFFF7F8FA),
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { editTarget = null; showDialog = true },
                    containerColor = Color(0xFFFFF9C4),
                    contentColor = Color(0xFF6D4C41)
                ) { Icon(Icons.Default.Add, "添加") }
            }
        ) { innerPadding ->
            LazyColumn(contentPadding = innerPadding) {
                item { HomeHeader(eventCount = events.size) }

                items(events) { event ->
                    EventCard(
                        title = event.title,
                        dateString = event.date,
                        colorHex = event.color,
                        isCommemoration = event.isCommemoration,
                        isTop = event.isTop,
                        onClick = { selectedEvent = event },
                        onLongClick = { eventToDelete = event }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    if (showDialog) {
        val isEditing = editTarget != null
        AddEventDialog(
            initialTitle = editTarget?.title ?: "",
            initialDate = editTarget?.date ?: "",
            initialIsCommemoration = editTarget?.isCommemoration ?: false, // 传入初始状态
            initialColor = editTarget?.color ?: 0xFFF48FB1,
            initialDesc = editTarget?.description ?: "",
            onDismiss = {
                showDialog = false
                editTarget = null
            },
            onConfirm = { title, date, color, desc, isCommemoration ->
                if (isEditing) {
                    val index = events.indexOf(editTarget)
                    if (index != -1) {
                        // 修改：使用 copy 并保留 isTop，同时更新 isCommemoration
                        val updated = editTarget!!.copy(
                            title = title,
                            date = date,
                            color = color,
                            description = desc,
                            isTop = editTarget!!.isTop,
                            isCommemoration = isCommemoration // 更新字段
                        )
                        events[index] = updated
                        if (selectedEvent == editTarget) selectedEvent = updated
                    }
                } else {
                    // 新增：包含新字段
                    events.add(0, LifeEvent(
                        title = title,
                        date = date,
                        color = color,
                        description = desc,
                        isTop = false,
                        isCommemoration = isCommemoration // 初始保存
                    ))
                }

                // 执行持久化并刷新小组件
                saveAndRefresh()

                showDialog = false
                editTarget = null
            }
        )
    }

    if (eventToDelete != null) {
        AlertDialog(
            onDismissRequest = { eventToDelete = null },
            title = { Text("删除确认", color = Color.Red) },
            text = { Text("你确定要忘记 \"${eventToDelete?.title}\" 吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        events.remove(eventToDelete)
                        eventToDelete = null
                        saveAndRefresh()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { eventToDelete = null }) { Text("取消") } },
            containerColor = Color.White
        )
    }
}

@Composable
fun HomeHeader(eventCount: Int) {
    val artisticFont = FontFamily(Font(R.font.art_font, weight = FontWeight.Normal))
    val PacificoFont = FontFamily(Font(resId = R.font.pacifico_regular, weight = FontWeight.Normal))
    val today = java.time.LocalDate.now()
    val dateString = "${today.year}年${today.monthValue}月${today.dayOfMonth}日"
    val weekString = when(today.dayOfWeek.value) {
        1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"; 5 -> "周五"; 6 -> "周六"; else -> "周日"
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(4.dp, 16.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)).background(Color(0xFF00BCD4)))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "$dateString · $weekString", style = MaterialTheme.typography.labelLarge, color = Color(0xFF616161), letterSpacing = 1.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(text = "轻", fontSize = 42.sp, fontFamily = artisticFont, color = Color(0xFF00BCD4))
            Text(text = "梅", fontSize = 42.sp, fontFamily = artisticFont, color = Color(0xFFEC407A))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "Days", fontSize = 32.sp, fontFamily = PacificoFont, color = Color(0xFF9E9E9E))
            Spacer(modifier = Modifier.weight(1f))
            Surface(color = Color(0xFF00BCD4).copy(alpha = 0.05f), shape = androidx.compose.foundation.shape.CircleShape, border = androidx.compose.foundation.BorderStroke(1.dp, Color.White)) {
                Text(text = "$eventCount 个纪念", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFFEC407A).copy(alpha = 0.8f), modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "生活中的每一次期待，都值得被记录。", fontSize = 13.sp, color = Color(0xFF757575))
    }
}