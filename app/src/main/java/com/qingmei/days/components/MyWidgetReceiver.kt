package com.qingmei.days.components

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import com.qingmei.days.utils.DataManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = MyWidget()

    /**
     * 处理广播事件
     */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        // 监听日期变化、时间设置变化或时区变化
        when (intent.action) {
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                // 触发小组件刷新，重新计算日期差
                CoroutineScope(Dispatchers.IO).launch {
                    MyWidget().updateAll(context)
                }
            }
        }
    }

    /**
     * 当该类型的小组件第一次被添加到桌面时触发
     */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // 开启协程进行全量同步，确保第一个组件即刻拥有数据
        CoroutineScope(Dispatchers.IO).launch {
            DataManager.syncAllWidgets(context)
        }
    }
}