package com.qingmei.days.utils

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.qingmei.days.components.MyWidget
import com.qingmei.days.model.LifeEvent
import java.time.LocalDate

// ❌ 删掉下面这行，Widget 不需要读这个全局 DataStore
// val Context.dataStore by preferencesDataStore("qingmei_days_widget")

object DataManager {

    private const val PREFS_NAME = "qingmei_days_prefs"
    private const val KEY_EVENTS = "saved_events"

    // ⭐ 必须确保 key 的名字和 MyWidget 里写的一模一样
    val WIDGET_VERSION_KEY = intPreferencesKey("widget_version")
    val WIDGET_EVENT_JSON = stringPreferencesKey("widget_event_json")

    private val gson = Gson()

    /**
     * ⭐ 修正后的逻辑：
     * 1. App 数据存 SP (不变)
     * 2. Widget 数据直接注入到 Glance 的 State 里 (这才是 currentState 能读到的地方)
     */
    suspend fun saveAndSyncWidget(context: Context, events: List<LifeEvent>) {
        // 1. 存入 SharedPreferences (App 内部数据)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = gson.toJson(events)
        prefs.edit().putString(KEY_EVENTS, jsonString).apply()

        // 2. 计算 Widget 要显示的数据
        val displayEvent = events.find { it.isTop } ?: events.firstOrNull()
        val widgetJson = if (displayEvent != null) gson.toJson(displayEvent) else ""

        // 3. ⭐ 核心修改：遍历所有 Widget，把数据塞进它们自己的 State 里
        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(MyWidget::class.java)

        glanceIds.forEach { glanceId ->
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[WIDGET_EVENT_JSON] = widgetJson
                val oldVersion = prefs[WIDGET_VERSION_KEY] ?: 0
                prefs[WIDGET_VERSION_KEY] = oldVersion + 1
            }
        }

        MyWidget().updateAll(context)
    }

    /**
     * 加载日子列表并进行逻辑过滤：
     * 1. 纪念日 (Commemoration)：永远保留。
     * 2. 提醒日 (Reminder)：过期即消失。
     */
    fun loadEvents(context: Context): List<LifeEvent> {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = sp.getString(KEY_EVENTS, null) ?: return emptyList()

        val type = object : TypeToken<List<LifeEvent>>() {}.type
        val allEvents: List<LifeEvent> = try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }

        val today = LocalDate.now()

        // ⭐ 核心逻辑：区分类型过滤
        return allEvents.filter { event ->
            try {
                val targetDate = LocalDate.parse(event.date)
                // 如果是纪念日，直接保留；如果是提醒日，只有在今天或以后才保留
                event.isCommemoration || !targetDate.isBefore(today)
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun syncAllWidgets(context: Context) {
        val events = loadEvents(context)
        val displayEvent = events.find { it.isTop } ?: events.firstOrNull()
        val json = displayEvent?.let { Gson().toJson(it) } ?: ""

        val manager = GlanceAppWidgetManager(context)
        val ids = manager.getGlanceIds(MyWidget::class.java)

        ids.forEach { id ->
            updateAppWidgetState(context, id) { prefs ->
                prefs[WIDGET_EVENT_JSON] = json
                val v = prefs[WIDGET_VERSION_KEY] ?: 0
                prefs[WIDGET_VERSION_KEY] = v + 1
            }
            MyWidget().update(context, id)
        }
    }

}