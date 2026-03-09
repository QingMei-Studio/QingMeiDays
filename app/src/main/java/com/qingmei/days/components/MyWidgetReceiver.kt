package com.qingmei.days.components

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.qingmei.days.utils.DataManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class MyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = MyWidget()

    companion object {
        // 自定义专属广播 Action
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

            // 使用 set()，不申请额外权限，保持 App 纯净
            alarmManager.set(AlarmManager.RTC_WAKEUP, tomorrowMidnight, pendingIntent)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        val action = intent.action
        // 监听：自定义午夜闹钟、系统时间被手动修改、系统日期变化、时区变化
        if (action == ACTION_MIDNIGHT_UPDATE ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_DATE_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED) {

            // 收到广播后，强制刷新所有小组件的数据和版本号
            CoroutineScope(Dispatchers.IO).launch {
                DataManager.syncAllWidgets(context)
            }

            // 如果是午夜闹钟把我们唤醒的，顺手把明天的闹钟也定上，形成无限循环
            if (action == ACTION_MIDNIGHT_UPDATE) {
                scheduleNextMidnightUpdate(context)
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        CoroutineScope(Dispatchers.IO).launch {
            DataManager.syncAllWidgets(context)
        }
        // 当用户第一次把小组件拖到桌面时，启动闹钟
        scheduleNextMidnightUpdate(context)
    }
}