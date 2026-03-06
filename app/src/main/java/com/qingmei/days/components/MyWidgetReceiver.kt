package com.qingmei.days.components

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import com.qingmei.days.utils.DataManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class MyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = MyWidget()

    companion object {
        // 自定义一个广播 Action
        const val ACTION_MIDNIGHT_UPDATE = "com.qingmei.days.ACTION_MIDNIGHT_UPDATE"

        // 设定下一个午夜零点的闹钟
        fun scheduleNextMidnightUpdate(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent(context, MyWidgetReceiver::class.java).apply {
                action = ACTION_MIDNIGHT_UPDATE
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 计算明天零点的时间戳
            val tomorrowMidnight = LocalDate.now().plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            // 使用 set 设置闹钟（不使用 setExact，避免申请新权限，保持 App 纯净）
            // 系统会在零点后，手机退出深度休眠或点亮屏幕时尽快触发
            alarmManager.set(AlarmManager.RTC_WAKEUP, tomorrowMidnight, pendingIntent)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_MIDNIGHT_UPDATE ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED) {

            CoroutineScope(Dispatchers.IO).launch {
                // ⭐ 核心修复：强制改变 version，打破 Glance 的缓存机制，确保护眼重绘
                glanceAppWidget.updateAll(context) // 先更新一下
                // 遍历所有组件实例强制刷新版本号
                // 注意：如果 DataManager 里有专门的 updateVersion 方法最好，这里简写原理
                DataManager.syncAllWidgets(context)
            }

            // 闹钟是一次性的，触发完之后，马上定明天的闹钟！形成闭环！
            if (intent.action == ACTION_MIDNIGHT_UPDATE) {
                scheduleNextMidnightUpdate(context)
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        CoroutineScope(Dispatchers.IO).launch {
            DataManager.syncAllWidgets(context)
        }
        // 当组件被添加到桌面时，启动午夜闹钟循环
        scheduleNextMidnightUpdate(context)
    }
}