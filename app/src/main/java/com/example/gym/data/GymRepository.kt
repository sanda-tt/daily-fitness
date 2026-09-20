package com.example.gym.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * 应用数据仓库（单例）。
 *
 * templates：按 周一=1 ... 周日=7 配置的训练项目模板，设置一次后每周该星期都生效。
 * completion：按日期(yyyy-MM-dd)记录已完成项目的 id 集合。
 * weightHistory：按项目名记录重量调整历史（重量 + 时间）。
 */
object GymRepository {

    private const val PREFS_NAME = "gym_prefs"
    private const val KEY_DATA = "data"

    private lateinit var prefs: android.content.SharedPreferences

    var templates by mutableStateOf<Map<Int, List<Exercise>>>(emptyMap())
        private set

    var completion by mutableStateOf<Map<String, Set<Long>>>(emptyMap())
        private set

    var weightHistory by mutableStateOf<Map<String, List<WeightRecord>>>(emptyMap())
        private set

    private var nextId = 1L

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        load()
    }

    fun itemsForDate(calendar: Calendar): List<Exercise> =
        templates[DateUtils.weekdayIndex(calendar)].orEmpty()

    fun completedIds(dateKey: String): Set<Long> = completion[dateKey].orEmpty()

    fun isCompleted(dateKey: String, id: Long): Boolean =
        completion[dateKey]?.contains(id) == true

    fun addExercise(
        weekday: Int,
        name: String,
        sets: Int,
        detail: String,
        weightKg: Double? = null
    ) {
        val id = nextId++
        templates = templates + (weekday to (templates[weekday].orEmpty() +
            Exercise(id, name, sets, detail, weightKg)))
        if (weightKg != null) {
            appendWeightRecord(name, weightKg)
        } else {
            save()
        }
    }

    fun updateExercise(weekday: Int, exercise: Exercise) {
        val old = templates[weekday]?.firstOrNull { it.id == exercise.id }

        // 改名：把旧名下的重量历史迁移到新名
        if (old != null && old.name != exercise.name) {
            val oldHist = weightHistory[old.name]
            if (oldHist != null && weightHistory[exercise.name].isNullOrEmpty()) {
                weightHistory = weightHistory - old.name + (exercise.name to oldHist)
            }
        }

        templates = templates + (weekday to templates[weekday].orEmpty().map {
            if (it.id == exercise.id) exercise else it
        })

        // 重量发生变化（含首次填写）→ 追加历史记录，并同步其它星期里的同名项目
        if (old != null && exercise.weightKg != null && exercise.weightKg != old.weightKg) {
            appendWeightRecord(exercise.name, exercise.weightKg)
        } else {
            save()
        }
    }

    fun deleteExercise(weekday: Int, id: Long) {
        templates = templates + (weekday to templates[weekday].orEmpty().filterNot { it.id == id })
        save()
    }

    fun setCompleted(dateKey: String, id: Long, completed: Boolean) {
        val current = completion[dateKey].orEmpty()
        val next = if (completed) current + id else current - id
        completion = if (next.isEmpty()) completion - dateKey else completion + (dateKey to next)
        save()
    }

    // ---------------- 重量记录 ----------------

    /** 某项目的重量历史，按时间从早到晚排序 */
    fun historyFor(name: String): List<WeightRecord> =
        weightHistory[name].orEmpty().sortedBy { it.time }

    /** 所有有重量记录的项目，按最近一次调整时间从新到旧排序 */
    fun recordedExercises(): List<Pair<String, List<WeightRecord>>> =
        weightHistory.entries
            .map { (name, list) -> name to list.sortedBy { it.time } }
            .sortedByDescending { it.second.last().time }

    /** 手动记录一次重量（同时更新模板里所有同名项目的当前重量） */
    fun recordWeight(name: String, weightKg: Double) {
        appendWeightRecord(name, weightKg)
    }

    /** 删除某条重量记录 */
    fun deleteWeightRecord(name: String, time: Long) {
        val list = weightHistory[name].orEmpty().filterNot { it.time == time }
        weightHistory = if (list.isEmpty()) {
            weightHistory - name
        } else {
            weightHistory + (name to list)
        }
        save()
    }

    private fun appendWeightRecord(
        name: String,
        weightKg: Double,
        time: Long = System.currentTimeMillis()
    ) {
        val merged = (weightHistory[name].orEmpty() + WeightRecord(weightKg, time))
            .sortedBy { it.time }
        weightHistory = weightHistory + (name to merged)

        // 模板中所有同名项目同步为最新重量
        templates = templates.mapValues { (_, items) ->
            items.map { if (it.name == name) it.copy(weightKg = weightKg) else it }
        }
        save()
    }

    // ---------------- 持久化 ----------------

    private fun load() {
        val raw = prefs.getString(KEY_DATA, null)
        if (raw == null) {
            seedDefaults()
            save()
            return
        }
        runCatching {
            val root = JSONObject(raw)
            nextId = root.optLong("nextId", 1L)

            val templatesJson = root.optJSONObject("templates")
            templates = if (templatesJson == null) {
                emptyMap()
            } else {
                (1..7).associateWith { weekday ->
                    templatesJson.optJSONArray(weekday.toString())
                        ?.let { array -> (0 until array.length()).map { array.getJSONObject(it).toExercise() } }
                        .orEmpty()
                }
            }

            val completionJson = root.optJSONObject("completion")
            completion = if (completionJson == null) {
                emptyMap()
            } else {
                completionJson.keys().asSequence().associateWith { dateKey ->
                    completionJson.getJSONArray(dateKey)
                        .let { array -> (0 until array.length()).map { array.getLong(it) }.toSet() }
                }
            }

            val weightsJson = root.optJSONObject("weights")
            weightHistory = if (weightsJson == null) {
                emptyMap()
            } else {
                weightsJson.keys().asSequence().associateWith { name ->
                    weightsJson.getJSONArray(name).let { array ->
                        (0 until array.length()).map { item ->
                            val obj = array.getJSONObject(item)
                            WeightRecord(obj.getDouble("w"), obj.getLong("t"))
                        }
                    }
                }
            }
        }.onFailure {
            seedDefaults()
            save()
        }
    }

    private fun seedDefaults() {
        nextId = 1L

        fun ex(name: String, sets: Int = 0, detail: String = ""): Exercise =
            Exercise(nextId++, name, sets, detail)

        val monday = listOf(
            ex("上斜哑铃卧推", 4, "6～10"),
            ex("器械卧推", 3, "8～12"),
            ex("夹胸", 3, "10～15"),
            ex("肩推", 3, "8～12"),
            ex("侧平举", 4, "12～20"),
            ex("三头下压", 3, "10～15"),
            ex("坡走", 0, "30分钟")
        )

        val tuesday = listOf(
            ex("高位下拉", 4, "8～12"),
            ex("坐姿划船", 4, "8～12"),
            ex("单臂下拉/单臂划船", 3, "10～12"),
            ex("反向飞鸟", 3, "12～20"),
            ex("二头弯举", 3, "8～12"),
            ex("锤式弯举", 2, "10～15"),
            ex("坡走", 0, "25～30分钟")
        )

        val wednesday = listOf(
            ex("深蹲/哈克深蹲", 4, "6～10"),
            ex("腿举", 3, "8～12"),
            ex("腿弯举", 3, "10～15"),
            ex("腿屈伸", 3, "10～15"),
            ex("提踵", 4, "10～15"),
            ex("卷腹", 3, "12～20"),
            ex("悬垂举腿", 3, "8～15"),
            ex("有氧", 0, "10～20分钟")
        )

        val thursday = listOf(
            ex("走路", 0, "12000～15000步"),
            ex("坡走", 0, "40～50分钟")
        )

        val friday = listOf(
            ex("上斜卧推", 3, "8～12"),
            ex("高位下拉", 3, "8～12"),
            ex("坐姿划船", 3, "8～12"),
            ex("侧平举", 5, "12～20"),
            ex("反向飞鸟", 3, "12～20"),
            ex("夹胸", 3, "12～15"),
            ex("二头", 2),
            ex("三头", 2),
            ex("坡走", 0, "30分钟")
        )

        val saturday = listOf(
            ex("罗马尼亚硬拉", 3, "6～10"),
            ex("腿举", 3, "10～15"),
            ex("腿弯举", 3, "10～15"),
            ex("侧平举", 4, "12～20"),
            ex("卷腹", 3),
            ex("举腿", 3),
            ex("有氧", 0, "20～30分钟")
        )

        val sunday = listOf(
            ex("散步")
        )

        templates = mapOf(
            1 to monday,
            2 to tuesday,
            3 to wednesday,
            4 to thursday,
            5 to friday,
            6 to saturday,
            7 to sunday
        )
        completion = emptyMap()
        weightHistory = emptyMap()
    }

    private fun save() {
        val root = JSONObject()
        root.put("nextId", nextId)

        val templatesJson = JSONObject()
        templates.forEach { (weekday, items) ->
            val array = JSONArray()
            items.forEach { array.put(it.toJson()) }
            templatesJson.put(weekday.toString(), array)
        }
        root.put("templates", templatesJson)

        val completionJson = JSONObject()
        completion.forEach { (dateKey, ids) ->
            val array = JSONArray()
            ids.forEach { array.put(it) }
            completionJson.put(dateKey, array)
        }
        root.put("completion", completionJson)

        val weightsJson = JSONObject()
        weightHistory.forEach { (name, records) ->
            val array = JSONArray()
            records.forEach { record ->
                array.put(JSONObject().put("w", record.weightKg).put("t", record.time))
            }
            weightsJson.put(name, array)
        }
        root.put("weights", weightsJson)

        prefs.edit().putString(KEY_DATA, root.toString()).apply()
    }

    private fun Exercise.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("sets", sets)
        .put("detail", detail)
        .put("weight", weightKg ?: JSONObject.NULL)

    private fun JSONObject.toExercise(): Exercise {
        // 兼容旧版本 reps(数字) 字段
        val migratedDetail = if (!has("detail") && has("reps")) {
            getInt("reps").takeIf { it > 0 }?.toString().orEmpty()
        } else {
            optString("detail", "")
        }
        val weight = if (has("weight") && !isNull("weight")) {
            optDouble("weight")
        } else {
            null
        }
        return Exercise(
            id = getLong("id"),
            name = getString("name"),
            sets = optInt("sets", 0),
            detail = migratedDetail,
            weightKg = weight
        )
    }
}
