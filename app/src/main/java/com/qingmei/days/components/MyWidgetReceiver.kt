package com.qingmei.days.components

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
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
        const val ACTION_MIDNIGHT_UPDATE = "com.qingmei.days.ACTION_MIDNIGHT_UPDATE"

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

            val tomorrowMidnight = LocalDate.now().plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            try {
                // 这个方法自带系统级休眠唤醒白名单
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, tomorrowMidnight, pendingIntent)
            } catch (e: SecurityException) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, tomorrowMidnight, pendingIntent)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        // 1. 先让 Glance 处理它自己的原生生命周期（比如新建组件），千万别抢它的控制权
        super.onReceive(context, intent)

        val action = intent.action
        if (action == ACTION_MIDNIGHT_UPDATE ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_DATE_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED) {

            // 🌟 核心突破：只在这里申请“免死金牌”！
            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 在免死金牌的保护下，从容地查数据、刷新小组件
                    DataManager.syncAllWidgets(context)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    // 活儿干完了，主动交还金牌，让系统可以休眠，绝不死锁转圈！
                    pendingResult.finish()
                }
            }

            // 无论怎么改时间，都重新定下明天的闹钟，支持无限次测试
            scheduleNextMidnightUpdate(context)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // 新建小组件时，立刻拉取数据，不加免死金牌，不和 Glance 冲突
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DataManager.syncAllWidgets(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleNextMidnightUpdate(context)
    }
}