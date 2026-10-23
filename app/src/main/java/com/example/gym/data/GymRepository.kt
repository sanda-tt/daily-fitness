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
 * scheduleMode：计划模式。WEEKLY=按周分配（周一~周日），CYCLE=循环计划（不看周几，按序列循环）。
 * templates：按 周一=1 ... 周日=7 配置的训练项目模板，设置一次后每周该星期都生效。
 * cycleTemplates：循环模式下各训练日槽位（1=A, 2=B ...）的项目模板。
 * cycleNodes：循环序列，逐个日历日推进，跑完自动从头开始；训练日绑定槽位，休息日无项目。
 * cycleStartKey：循环起点（序列第 0 个节点对应的日期 yyyy-MM-dd）。
 * completion：按日期(yyyy-MM-dd)记录每个项目「已完成的组数」：dateKey -> (项目id -> 组数)。
 *             多组项目需要逐组累计到设定组数才算完成；无组项目累计 1 即完成。
 * weightHistory：按项目名记录重量调整历史（重量 + 时间）。
 */
object GymRepository {

    private const val PREFS_NAME = "gym_prefs"
    private const val KEY_DATA = "data"

    /** 数据迁移版本；升级时保留用户模板与项目 ID。 */
    private const val PLAN_VERSION = 3

    // （v1.5：PLAN_VERSION=3，新增每日训练主题）
    // （v1.7：新增循环计划模式，按周模板保留不动，老用户默认仍为按周模式）

    private const val MAX_CYCLE_SLOTS = 8

    private var planVersion = 1

    private lateinit var prefs: android.content.SharedPreferences

    /** 当前计划模式 */
    var scheduleMode by mutableStateOf(ScheduleMode.WEEKLY)
        private set

    var templates by mutableStateOf<Map<Int, List<Exercise>>>(emptyMap())
        private set

    var completion by mutableStateOf<Map<String, Map<Long, Int>>>(emptyMap())
        private set

    var weightHistory by mutableStateOf<Map<String, List<WeightRecord>>>(emptyMap())
        private set

    /** 每天的训练主题，如「背+二头」：周一=1 ... 周日=7 */
    var dayThemes by mutableStateOf<Map<Int, String>>(emptyMap())
        private set

    /** 循环模式：当前节奏预设 */
    var cyclePreset by mutableStateOf(CyclePreset.SPLIT3_R1)
        private set

    /** 循环模式：序列（按日历日推进，跑完循环） */
    var cycleNodes by mutableStateOf<List<CycleNode>>(emptyList())
        private set

    /** 循环模式：各训练日槽位的项目模板 */
    var cycleTemplates by mutableStateOf<Map<Int, List<Exercise>>>(emptyMap())
        private set

    /** 循环模式：各训练日槽位的主题 */
    var cycleThemes by mutableStateOf<Map<Int, String>>(emptyMap())
        private set

    /** 循环模式：起点日期（yyyy-MM-dd） */
    var cycleStartKey by mutableStateOf("")
        private set

    private var nextId = 1L

    private data class CycleContent(
        val templates: Map<Int, List<Exercise>>,
        val themes: Map<Int, String>
    )

    // 自定义编排沿用当前分化的内容；两种分化独立保存，避免切换时覆盖编辑。
    private var cycleFamily = "split3"
    private var cycleContents = emptyMap<String, CycleContent>()

    private fun CyclePreset.family(): String = when (this) {
        CyclePreset.SPLIT2_R1, CyclePreset.SPLIT2_R2 -> "split2"
        CyclePreset.SPLIT3_R1, CyclePreset.SPLIT3_R2 -> "split3"
        CyclePreset.CUSTOM -> cycleFamily
    }

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        load()
    }

    fun itemsForDate(calendar: Calendar): List<Exercise> = when (scheduleMode) {
        ScheduleMode.WEEKLY ->
            templates[DateUtils.weekdayIndex(calendar)].orEmpty()
        ScheduleMode.CYCLE ->
            (cycleNodeAt(calendar) as? CycleNode.Training)?.let { cycleTemplates[it.slot].orEmpty() }
                .orEmpty()
    }

    /**
     * 循环模式下某天落在序列的哪个节点；非循环模式或未配置时返回 null。
     * 序列按日历日推进：起点日为第 0 个节点，之后逐日 +1，跑完从头循环。
     */
    fun cycleNodeAt(calendar: Calendar): CycleNode? {
        if (scheduleMode != ScheduleMode.CYCLE || cycleNodes.isEmpty()) return null
        if (cycleStartKey.isBlank()) return null
        val start = DateUtils.parseKey(cycleStartKey)
        val size = cycleNodes.size
        val diff = DateUtils.daysBetween(start, calendar)
        val index = Math.floorMod(diff, size)
        return cycleNodes[index]
    }

    /** 循环模式下某天是否为序列中的休息日 */
    fun isRestDay(calendar: Calendar): Boolean = cycleNodeAt(calendar) is CycleNode.Rest

    /** 某天的训练主题（按模式取） */
    fun themeForDate(calendar: Calendar): String = when (scheduleMode) {
        ScheduleMode.WEEKLY ->
            dayThemes[DateUtils.weekdayIndex(calendar)].orEmpty()
        ScheduleMode.CYCLE ->
            (cycleNodeAt(calendar) as? CycleNode.Training)?.let { cycleThemes[it.slot].orEmpty() }
                .orEmpty()
    }

    /**
     * 循环模式下某天在日期条上的标签：训练日显示槽位字母（A/B…），休息日显示「休」；
     * 按周模式返回 null（调用方回退显示星期几）。
     */
    fun cycleLabelForDate(calendar: Calendar): String? {
        if (scheduleMode != ScheduleMode.CYCLE) return null
        return when (val node = cycleNodeAt(calendar)) {
            is CycleNode.Training -> slotLabel(node.slot)
            is CycleNode.Rest -> "休"
            null -> null
        }
    }

    /** 循环模式下序列中实际用到的槽位序号（升序） */
    fun usedCycleSlots(): List<Int> =
        cycleNodes.mapNotNull { (it as? CycleNode.Training)?.slot }.distinct().sorted()

    /** 包含尚未加入序列的新类型；用于编辑与选择。 */
    fun availableCycleSlots(): List<Int> =
        (cycleTemplates.keys + usedCycleSlots()).distinct().sorted()

    /** 某天全部项目的已完成组数：项目id -> 组数 */
    fun completedCounts(dateKey: String): Map<Long, Int> = completion[dateKey].orEmpty()

    /** 某项目当天已完成的组数 */
    fun completedSetCount(dateKey: String, id: Long): Int =
        completion[dateKey]?.get(id) ?: 0

    /**
     * 完成一组：已完成组数 +1，最多累计到 cap（项目设定组数，无组项目为 1）。
     * @return 累计后的组数
     */
    fun advanceSet(dateKey: String, id: Long, cap: Int): Int {
        val safeCap = cap.coerceAtLeast(1)
        val current = (completion[dateKey]?.get(id) ?: 0).coerceIn(0, safeCap)
        val next = (current + 1).coerceAtMost(safeCap)
        storeSetCount(dateKey, id, next)
        return next
    }

    /**
     * 撤回一组：已完成组数 -1，最少为 0。
     * @return 撤回后的组数
     */
    fun retreatSet(dateKey: String, id: Long): Int {
        val current = completion[dateKey]?.get(id) ?: 0
        val next = (current - 1).coerceAtLeast(0)
        storeSetCount(dateKey, id, next)
        return next
    }

    private fun storeSetCount(dateKey: String, id: Long, count: Int) {
        val dayMap = completion[dateKey].orEmpty()
        val nextMap = if (count <= 0) dayMap - id else dayMap + (id to count)
        completion = if (nextMap.isEmpty()) {
            completion - dateKey
        } else {
            completion + (dateKey to nextMap)
        }
        save()
    }

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

    /**
     * 调整某天内的项目顺序。
     * @param fromIndex 当前位置
     * @param toIndex   移除该项目后、插入到剩余列表中的位置
     */
    fun moveExercise(weekday: Int, fromIndex: Int, toIndex: Int) {
        val items = templates[weekday].orEmpty().toMutableList()
        if (fromIndex !in items.indices) return
        val item = items.removeAt(fromIndex)
        items.add(toIndex.coerceIn(0, items.size), item)
        templates = templates + (weekday to items)
        save()
    }

    /** 复制一个项目到目标天（生成新 id；复制不产生重量调整记录） */
    fun copyExerciseTo(targetWeekday: Int, exercise: Exercise) {
        val copy = exercise.copy(id = nextId++)
        templates = templates +
            (targetWeekday to (templates[targetWeekday].orEmpty() + copy))
        save()
    }

    /** 清空某一天的全部项目（重量历史保留） */
    fun clearDay(weekday: Int) {
        templates = templates + (weekday to emptyList())
        save()
    }

    /** 设置 / 修改某天的训练主题，传空串则清除 */
    fun updateDayTheme(weekday: Int, theme: String) {
        val value = theme.trim()
        dayThemes = if (value.isEmpty()) dayThemes - weekday else dayThemes + (weekday to value)
        save()
    }

    /** 某天的训练主题 */
    fun themeFor(weekday: Int): String = dayThemes[weekday].orEmpty()

    // ---------------- 循环模式 ----------------

    /** 切换计划模式；首次进入循环模式时初始化起点与默认三分化计划 */
    fun setMode(mode: ScheduleMode) {
        if (mode == scheduleMode) return
        scheduleMode = mode
        if (mode == ScheduleMode.CYCLE && cycleStartKey.isBlank() && cycleNodes.isEmpty()) {
            cycleStartKey = DateUtils.dateKey(DateUtils.today())
            applyCyclePreset(CyclePreset.SPLIT3_R1)
        } else {
            save()
        }
    }

    /**
     * 应用循环节奏预设：重建序列；已存在的槽位内容原样保留，
     * 新增槽位从按周模板智能填充（含 A/B 两套内容时），反复切换不丢已编辑内容。
     */
    fun applyCyclePreset(preset: CyclePreset) {
        val family = preset.family()
        if (family != cycleFamily) {
            cycleContents = cycleContents + (cycleFamily to CycleContent(cycleTemplates, cycleThemes))
            val content = cycleContents[family]
            cycleTemplates = content?.templates.orEmpty()
            cycleThemes = content?.themes.orEmpty()
            cycleFamily = family
        }
        cyclePreset = preset
        if (preset == CyclePreset.CUSTOM) {
            save()
            return
        }

        val spec: List<Int> = when (preset) {
            CyclePreset.SPLIT3_R1 -> listOf(1, 2, 3, -1)
            CyclePreset.SPLIT3_R2 -> listOf(1, 2, 3, 4, 5, 6, -1)
            CyclePreset.SPLIT2_R1 -> listOf(1, 2, -1)
            CyclePreset.SPLIT2_R2 -> listOf(1, 2, 3, 4, -1)
            CyclePreset.CUSTOM -> emptyList()
        }
        val slotCount = spec.maxOrNull() ?: 0

        for (slot in 1..slotCount) {
            if (slot !in cycleTemplates) {
                fillCycleSlot(slot, preset)
            }
        }
        cycleNodes = spec.map { code ->
            if (code == -1) CycleNode.Rest else CycleNode.Training(code)
        }
        save()
    }

    /** 新增槽位时的内容填充：三分化取按周模板的推/腿/拉（含 B 套），二分化按上肢/下肢重组 */
    private fun fillCycleSlot(slot: Int, preset: CyclePreset) {
        val isSplit3 = preset == CyclePreset.SPLIT3_R1 || preset == CyclePreset.SPLIT3_R2
        if (isSplit3) {
            val sourceWeekday = when (slot) {
                1 -> 1
                2 -> 2
                3 -> 4
                4 -> 5
                5 -> 6
                6 -> 7
                else -> null
            }
            if (sourceWeekday != null) {
                setCycleSlotItems(slot, cloneItems(templates[sourceWeekday].orEmpty()))
                setCycleThemeInternal(slot, dayThemes[sourceWeekday].orEmpty())
            }
        } else {
            when (slot) {
                1 -> {
                    setCycleSlotItems(slot, buildUpperBodyItems())
                    setCycleThemeInternal(slot, "上肢")
                }
                2 -> {
                    setCycleSlotItems(slot, buildLowerBodyItems())
                    setCycleThemeInternal(slot, "下肢")
                }
                // 二分化练4休1：第二轮内容与第一轮相同（复制，独立 id）
                3 -> {
                    setCycleSlotItems(slot, cloneItems(cycleTemplates[1].orEmpty()))
                    setCycleThemeInternal(slot, cycleThemes[1].orEmpty().ifBlank { "上肢" })
                }
                4 -> {
                    setCycleSlotItems(slot, cloneItems(cycleTemplates[2].orEmpty()))
                    setCycleThemeInternal(slot, cycleThemes[2].orEmpty().ifBlank { "下肢" })
                }
            }
        }
    }

    /** 二分化上肢日：从按周模板的推/拉日中挑选主要项目 */
    private fun buildUpperBodyItems(): List<Exercise> {
        val preferred = listOf(
            "史密斯卧推", "辅助引体向上", "哑铃侧平举", "反向飞鸟",
            "绳索三头下压", "哑铃二头弯举", "有氧：坡走/椭圆机"
        )
        val pool = templates.values.flatten()
        val picked = preferred.mapNotNull { name -> pool.firstOrNull { it.name == name } }
        val fallback = (templates[1].orEmpty().take(3) + templates[4].orEmpty().take(3))
            .filterNot { item -> picked.any { it.name == item.name } }
        return cloneItems((picked + fallback).distinctBy { it.name })
    }

    /** 二分化下肢日：取按周腿日全部项目 */
    private fun buildLowerBodyItems(): List<Exercise> {
        val source = templates[2].orEmpty()
        return if (source.isNotEmpty()) cloneItems(source) else emptyList()
    }

    /** 复制项目列表并分配新 id（复制不产生重量调整记录） */
    private fun cloneItems(items: List<Exercise>): List<Exercise> =
        items.map { it.copy(id = nextId++) }

    private fun setCycleSlotItems(slot: Int, items: List<Exercise>) {
        cycleTemplates = cycleTemplates + (slot to items)
    }

    private fun setCycleThemeInternal(slot: Int, theme: String) {
        cycleThemes = if (theme.isBlank()) cycleThemes - slot else cycleThemes + (slot to theme)
    }

    /** 自定义模式：新增一个空槽位（训练日类型），返回槽位序号；达到上限返回 null */
    fun addCycleSlot(): Int? {
        val slot = (1..MAX_CYCLE_SLOTS).firstOrNull { it !in availableCycleSlots() }
            ?: return null
        setCycleSlotItems(slot, emptyList())
        markCustom()
        return slot
    }

    /** 自定义模式：序列末尾追加一个绑定指定槽位的训练日 */
    fun appendCycleTraining(slot: Int) {
        cycleNodes = cycleNodes + CycleNode.Training(slot)
        markCustom()
    }

    /** 自定义模式：序列末尾追加一个休息日 */
    fun appendCycleRest() {
        cycleNodes = cycleNodes + CycleNode.Rest
        markCustom()
    }

    /** 自定义模式：删除序列中指定位置的节点；至少保留一个节点 */
    fun removeCycleNodeAt(index: Int) {
        if (index !in cycleNodes.indices || cycleNodes.size <= 1) return
        cycleNodes = cycleNodes.filterIndexed { i, _ -> i != index }
        markCustom()
    }

    private fun markCustom() {
        cyclePreset = CyclePreset.CUSTOM
        save()
    }

    /** 循环起点重置为今天（序列第 0 个节点落在今天） */
    fun resetCycleStartToToday() {
        cycleStartKey = DateUtils.dateKey(DateUtils.today())
        save()
    }

    /**
     * 对齐：让序列中指定位置的节点落在今天（相当于整体平移循环起点）。
     * 例如今天正好是第二个训练日，点序列中该节点即可对齐。
     */
    fun alignTodayToNode(sequenceIndex: Int) {
        if (sequenceIndex !in cycleNodes.indices) return
        val start = DateUtils.today().apply { add(Calendar.DATE, -sequenceIndex) }
        cycleStartKey = DateUtils.dateKey(start)
        save()
    }

    // ---- 循环槽位项目增删改（与按周模板平行，重量历史全局共享） ----

    fun addCycleExercise(
        slot: Int,
        name: String,
        sets: Int,
        detail: String,
        weightKg: Double? = null
    ) {
        val id = nextId++
        cycleTemplates = cycleTemplates +
            (slot to (cycleTemplates[slot].orEmpty() + Exercise(id, name, sets, detail, weightKg)))
        if (weightKg != null) appendWeightRecord(name, weightKg) else save()
    }

    fun updateCycleExercise(slot: Int, exercise: Exercise) {
        val old = cycleTemplates[slot]?.firstOrNull { it.id == exercise.id }

        if (old != null && old.name != exercise.name) {
            val oldHist = weightHistory[old.name]
            if (oldHist != null && weightHistory[exercise.name].isNullOrEmpty()) {
                weightHistory = weightHistory - old.name + (exercise.name to oldHist)
            }
        }

        cycleTemplates = cycleTemplates +
            (slot to cycleTemplates[slot].orEmpty().map {
                if (it.id == exercise.id) exercise else it
            })

        if (old != null && exercise.weightKg != null && exercise.weightKg != old.weightKg) {
            appendWeightRecord(exercise.name, exercise.weightKg)
        } else {
            save()
        }
    }

    fun deleteCycleExercise(slot: Int, id: Long) {
        cycleTemplates = cycleTemplates +
            (slot to cycleTemplates[slot].orEmpty().filterNot { it.id == id })
        save()
    }

    fun moveCycleExercise(slot: Int, fromIndex: Int, toIndex: Int) {
        val items = cycleTemplates[slot].orEmpty().toMutableList()
        if (fromIndex !in items.indices) return
        val item = items.removeAt(fromIndex)
        items.add(toIndex.coerceIn(0, items.size), item)
        cycleTemplates = cycleTemplates + (slot to items)
        save()
    }

    fun copyCycleExerciseTo(targetSlot: Int, exercise: Exercise) {
        val copy = exercise.copy(id = nextId++)
        cycleTemplates = cycleTemplates +
            (targetSlot to (cycleTemplates[targetSlot].orEmpty() + copy))
        save()
    }

    fun clearCycleSlot(slot: Int) {
        cycleTemplates = cycleTemplates + (slot to emptyList())
        save()
    }

    fun updateCycleTheme(slot: Int, theme: String) {
        setCycleThemeInternal(slot, theme.trim())
        save()
    }

    fun cycleThemeFor(slot: Int): String = cycleThemes[slot].orEmpty()

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

        // 模板中所有同名项目同步为最新重量（按周 + 循环两套模板都同步）
        templates = templates.mapValues { (_, items) ->
            items.map { if (it.name == name) it.copy(weightKg = weightKg) else it }
        }
        cycleTemplates = cycleTemplates.mapValues { (_, items) ->
            items.map { if (it.name == name) it.copy(weightKg = weightKg) else it }
        }
        cycleContents = cycleContents.mapValues { (_, content) ->
            content.copy(templates = content.templates.mapValues { (_, items) ->
                items.map { if (it.name == name) it.copy(weightKg = weightKg) else it }
            })
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
            planVersion = root.optInt("planVersion", 1)
            scheduleMode = if (root.optInt("mode", 0) == 1) ScheduleMode.CYCLE else ScheduleMode.WEEKLY

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
                buildMap {
                    completionJson.keys().forEach { dateKey ->
                        val dayMap: Map<Long, Int> = when (val node = completionJson.get(dateKey)) {
                            // 新格式：{ "项目id": 已完成组数 }
                            is JSONObject -> node.keys().asSequence().associate { key ->
                                key.toLong() to node.getInt(key)
                            }
                            // 旧格式：[项目id, ...] -> 按当前模板的设定组数补齐
                            is JSONArray -> {
                                val allExercises = templates.values.flatten()
                                (0 until node.length()).associate {
                                    val id = node.getLong(it)
                                    val sets = allExercises.firstOrNull { e -> e.id == id }?.sets ?: 1
                                    id to sets.coerceAtLeast(1)
                                }
                            }
                            else -> emptyMap()
                        }
                        if (dayMap.isNotEmpty()) put(dateKey, dayMap)
                    }
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

            val themesJson = root.optJSONObject("themes")
            dayThemes = if (themesJson == null) {
                emptyMap()
            } else {
                (1..7).associateWith { themesJson.optString(it.toString(), "") }
                    .filterValues { it.isNotBlank() }
            }

            // 循环计划（v1.7 新增，老数据无此节点时保持默认空值 → 按周模式）
            val cycleJson = root.optJSONObject("cycle")
            cycleContents = emptyMap()
            cycleFamily = "split3"
            if (cycleJson == null) {
                cycleStartKey = ""
                cycleNodes = emptyList()
                cycleTemplates = emptyMap()
                cycleThemes = emptyMap()
                cyclePreset = CyclePreset.SPLIT3_R1
            } else {
                cycleStartKey = cycleJson.optString("start", "")
                cyclePreset = runCatching {
                    CyclePreset.valueOf(cycleJson.optString("preset", CyclePreset.SPLIT3_R1.name))
                }.getOrDefault(CyclePreset.SPLIT3_R1)
                // v1.7 首次进入循环必先生成三分化，即使后来选择二分化也未替换内容。
                // 将这套旧内容保存在三分化中，再初始化正确的二分化。
                cycleFamily = cycleJson.optString("family", "split3")
                val contents = cycleJson.optJSONObject("contents")
                if (contents != null) {
                    cycleContents = contents.keys().asSequence().associateWith { family ->
                        readCycleContent(contents.getJSONObject(family))
                    }
                }

                cycleNodes = cycleJson.optJSONArray("nodes")?.let { array ->
                    (0 until array.length()).map { i ->
                        val nodeObj = array.getJSONObject(i)
                        if (nodeObj.optString("t") == "r") {
                            CycleNode.Rest
                        } else {
                            CycleNode.Training(nodeObj.optInt("s", 1))
                        }
                    }
                }.orEmpty()

                val cycleTemplatesJson = cycleJson.optJSONObject("templates")
                cycleTemplates = if (cycleTemplatesJson == null) {
                    emptyMap()
                } else {
                    cycleTemplatesJson.keys().asSequence()
                        .map { key ->
                            key.toInt() to cycleTemplatesJson.getJSONArray(key)
                                .let { array ->
                                    (0 until array.length()).map { array.getJSONObject(it).toExercise() }
                                }
                        }
                        .toMap()
                }

                val cycleThemesJson = cycleJson.optJSONObject("themes")
                cycleThemes = if (cycleThemesJson == null) {
                    emptyMap()
                } else {
                    cycleThemesJson.keys().asSequence()
                        .associate { key ->
                            key.toInt() to cycleThemesJson.getString(key)
                        }
                        .filterValues { it.isNotBlank() }
                }
            }

            // 旧版只迁移新增字段，不能以默认模板覆盖用户数据。
            if (planVersion < PLAN_VERSION) {
                planVersion = PLAN_VERSION
            }
            val allIds = templates.values.flatten().map { it.id } +
                cycleTemplates.values.flatten().map { it.id } +
                cycleContents.values.flatMap { it.templates.values.flatten() }.map { it.id } +
                completion.values.flatMap { it.keys }
            nextId = maxOf(nextId, (allIds.maxOrNull() ?: 0L) + 1L)
            if (cyclePreset.family() != cycleFamily) applyCyclePreset(cyclePreset)
            save()
        }.onFailure {
            seedDefaults()
            save()
        }
    }

    private fun seedDefaults() {
        nextId = 1L
        planVersion = PLAN_VERSION
        scheduleMode = ScheduleMode.WEEKLY
        cyclePreset = CyclePreset.SPLIT3_R1
        cycleStartKey = ""
        cycleNodes = emptyList()
        cycleTemplates = emptyMap()
        cycleThemes = emptyMap()
        cycleFamily = "split3"
        cycleContents = emptyMap()

        fun ex(name: String, sets: Int = 0, detail: String = ""): Exercise =
            Exercise(nextId++, name, sets, detail)

        // 周一｜Push A：胸 + 肩 + 三头
        val monday = listOf(
            ex("史密斯卧推", 3, "6～10"),
            ex("上斜哑铃卧推", 3, "8～12"),
            ex("哑铃侧平举", 4, "12～20"),
            ex("双杠臂屈伸", 3, "6～12"),
            ex("绳索三头下压", 2, "10～15"),
            ex("有氧：坡走/椭圆机", 0, "25～30min")
        )

        // 周二｜Legs A：腿 + 腹
        val tuesday = listOf(
            ex("哈克深蹲", 4, "8～12"),
            ex("箭步蹲", 3, "8～10/腿"),
            ex("坐姿腿弯举", 3, "10～15"),
            ex("坐姿腿屈伸", 3, "10～15"),
            ex("提踵", 3, "12～20"),
            ex("悬垂举腿", 3, "8～12"),
            ex("绳索/器械卷腹", 3, "10～15"),
            ex("有氧：椭圆机/单车", 0, "15～25min")
        )

        // 周三｜游泳
        val wednesday = listOf(
            ex("游泳")
        )

        // 周四｜Pull A：背 + 二头
        val thursday = listOf(
            ex("辅助引体向上", 4, "6～10"),
            ex("坐姿划船", 3, "8～12"),
            ex("直臂下拉", 3, "10～15"),
            ex("反向飞鸟", 3, "12～20"),
            ex("哑铃二头弯举", 3, "10～12"),
            ex("有氧：坡走/椭圆机", 0, "25～30min")
        )

        // 周五｜Push B：胸 + 肩 + 三头
        val friday = listOf(
            ex("上斜哑铃卧推", 3, "8～12"),
            ex("史密斯卧推", 3, "8～12"),
            ex("哑铃侧平举", 4, "15～20"),
            ex("双杠臂屈伸", 2, "8～12"),
            ex("绳索三头下压", 2, "12～15"),
            ex("有氧：坡走/椭圆机", 0, "25～30min")
        )

        // 周六｜Legs B：腿 + 腹
        val saturday = listOf(
            ex("哈克深蹲", 3, "10～12"),
            ex("箭步蹲", 2, "10/腿"),
            ex("坐姿腿弯举", 3, "12～15"),
            ex("坐姿腿屈伸", 2, "12～15"),
            ex("提踵", 3, "15～20"),
            ex("悬垂举腿", 3, "8～12"),
            ex("卷腹", 3, "12～15"),
            ex("平板支撑", 2, "45～60s"),
            ex("有氧：椭圆机/单车", 0, "15～25min")
        )

        // 周日｜Pull B：背 + 二头
        val sunday = listOf(
            ex("辅助引体向上", 3, "8～12"),
            ex("鹦鹉螺划船", 3, "8～12"),
            ex("直臂下拉", 3, "12～15"),
            ex("反向飞鸟", 3, "15～20"),
            ex("哑铃二头弯举", 3, "10～15"),
            ex("有氧：坡走/椭圆机", 0, "25～30min")
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
        dayThemes = mapOf(
            1 to "胸+肩+三头",
            2 to "腿+腹",
            3 to "游泳",
            4 to "背+二头",
            5 to "胸+肩+三头",
            6 to "腿+腹",
            7 to "背+二头"
        )
        completion = emptyMap()
        weightHistory = emptyMap()
    }

    private fun save() {
        val root = JSONObject()
        root.put("nextId", nextId)
        root.put("planVersion", planVersion)
        root.put("mode", if (scheduleMode == ScheduleMode.CYCLE) 1 else 0)

        val templatesJson = JSONObject()
        templates.forEach { (weekday, items) ->
            val array = JSONArray()
            items.forEach { array.put(it.toJson()) }
            templatesJson.put(weekday.toString(), array)
        }
        root.put("templates", templatesJson)

        val completionJson = JSONObject()
        completion.forEach { (dateKey, dayMap) ->
            val dayJson = JSONObject()
            dayMap.forEach { (id, count) -> dayJson.put(id.toString(), count) }
            completionJson.put(dateKey, dayJson)
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

        val themesJson = JSONObject()
        dayThemes.forEach { (weekday, theme) -> themesJson.put(weekday.toString(), theme) }
        root.put("themes", themesJson)

        // 循环计划
        val cycleJson = JSONObject()
        cycleJson.put("start", cycleStartKey)
        cycleJson.put("preset", cyclePreset.name)
        cycleJson.put("family", cycleFamily)
        val contentsJson = JSONObject()
        cycleContents.filterKeys { it != cycleFamily }.forEach { (family, content) ->
            contentsJson.put(family, cycleContentJson(content))
        }
        cycleJson.put("contents", contentsJson)
        val nodesJson = JSONArray()
        cycleNodes.forEach { node ->
            when (node) {
                is CycleNode.Rest -> nodesJson.put(JSONObject().put("t", "r"))
                is CycleNode.Training ->
                    nodesJson.put(JSONObject().put("t", "t").put("s", node.slot))
            }
        }
        cycleJson.put("nodes", nodesJson)
        val cycleTemplatesJson = JSONObject()
        cycleTemplates.forEach { (slot, items) ->
            val array = JSONArray()
            items.forEach { array.put(it.toJson()) }
            cycleTemplatesJson.put(slot.toString(), array)
        }
        cycleJson.put("templates", cycleTemplatesJson)
        val cycleThemesJson = JSONObject()
        cycleThemes.forEach { (slot, theme) -> cycleThemesJson.put(slot.toString(), theme) }
        cycleJson.put("themes", cycleThemesJson)
        root.put("cycle", cycleJson)

        prefs.edit().putString(KEY_DATA, root.toString()).apply()
    }

    private fun cycleContentJson(content: CycleContent): JSONObject {
        val templatesJson = JSONObject()
        content.templates.forEach { (slot, items) ->
            val array = JSONArray()
            items.forEach { array.put(it.toJson()) }
            templatesJson.put(slot.toString(), array)
        }
        val themesJson = JSONObject()
        content.themes.forEach { (slot, theme) -> themesJson.put(slot.toString(), theme) }
        return JSONObject().put("templates", templatesJson).put("themes", themesJson)
    }

    private fun readCycleContent(json: JSONObject): CycleContent {
        val templatesJson = json.getJSONObject("templates")
        val themesJson = json.getJSONObject("themes")
        return CycleContent(
            templatesJson.keys().asSequence().associate { key ->
                val array = templatesJson.getJSONArray(key)
                key.toInt() to (0 until array.length()).map { array.getJSONObject(it).toExercise() }
            },
            themesJson.keys().asSequence().associate { it.toInt() to themesJson.getString(it) }
        )
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
