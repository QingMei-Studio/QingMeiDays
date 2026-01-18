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
import androidx.core.content.edit

// ❌ 删掉下面这行，Widget 不需要读这个全局 DataStore
// val Context.dataStore by preferencesDataStore("qingmei_days_widget")

object DataManager {

    private const val PREFS_NAME = "qingmei_days_prefs"
    private const val KEY_EVENTS = "saved_events"

    // ⭐ 必须确保 key 的名字和 MyWidget 里写的一模一样
    val WIDGET_VERSION_KEY = intPreferencesKey("widget_version")
    val WIDGET_EVENT_JSON = stringPreferencesKey("widget_event_json")

    val WIDGET_INDEX_KEY = intPreferencesKey("widget_index")

    private val gson = Gson()

    /**
     * ⭐ 修正后的逻辑：
     * 1. App 数据存 SP (不变)
     * 2. Widget 数据直接注入到 Glance 的 State 里 (这才是 currentState 能读到的地方)
     */
    suspend fun saveAndSyncWidget(context: Context, events: List<LifeEvent>) {
        // 1. 存入 SharedPreferences（App 内部数据）
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = gson.toJson(events)
        prefs.edit { putString(KEY_EVENTS, jsonString) }

        // 2. ⭐ 不在这里选 displayEvent 了！
        //    直接交给 syncAllWidgets 统一分配
        syncAllWidgets(context)
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
            return emptyList()
        }

        val today = LocalDate.now()

        // 🌟 纯净版过滤逻辑 (无 type)
        val filteredList = allEvents.filter { event ->
            try {
                // 1. 如果是纪念日，永远保留 (比如生日、恋爱纪念日)
                if (event.isCommemoration) {
                    return@filter true
                }

                // 2. 如果不是纪念日 (即倒数日/提醒日)，检查日期
                val targetDate = LocalDate.parse(event.date)

                // 规则：目标日期必须是 今天 或 未来
                // (!isBefore 等价于 >= )
                val shouldKeep = !targetDate.isBefore(today)

                shouldKeep

            } catch (e: Exception) {
                // 日期格式错乱的，为了安全起见先不显示，防止崩坏
                false
            }
        }

        return filteredList
    }

    suspend fun syncAllWidgets(context: Context) {
        val events = loadEvents(context)
        val manager = GlanceAppWidgetManager(context)
        val ids = manager.getGlanceIds(MyWidget::class.java)

        ids.forEachIndexed  { index, id ->
            val displayEvent = events.getOrNull(index)
            val json = displayEvent?.let { Gson().toJson(it) } ?: ""
            updateAppWidgetState(context, id) { prefs ->
                prefs[WIDGET_INDEX_KEY] = index
                prefs[WIDGET_EVENT_JSON] = json
                val v = prefs[WIDGET_VERSION_KEY] ?: 0
                prefs[WIDGET_VERSION_KEY] = v + 1
            }
            MyWidget().update(context, id)
        }
    }

}