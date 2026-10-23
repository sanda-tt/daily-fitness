package com.example.gym.data

/**
 * 一个训练项目。
 *
 * @param sets      组数，0 表示不按组（如纯有氧、走路）
 * @param detail    每组规格，自由文本：
 *                  次数范围 "6～10"、时长 "30分钟"、步数 "12000～15000步" 等；
 *                  为空时仅按组数显示。
 * @param weightKg  当前训练重量（公斤），null 表示不记录重量。
 */
data class Exercise(
    val id: Long,
    val name: String,
    val sets: Int = 0,
    val detail: String = "",
    val weightKg: Double? = null
) {
    /** 项目规格的展示文本，例如 "4×6～10"、"2组"、"30分钟" */
    val planText: String
        get() = when {
            sets > 0 && detail.isNotBlank() -> "${sets}×${detail}"
            sets > 0 -> "${sets}组"
            detail.isNotBlank() -> detail
            else -> ""
        }

    /** 重量展示文本，例如 "60kg"、"62.5kg" */
    val weightText: String
        get() = weightKg?.let { "${formatWeight(it)}kg" } ?: ""
}

/**
 * 一次重量调整记录。
 * @param weightKg 调整后的重量（公斤）
 * @param time     调整时间（System.currentTimeMillis）
 */
data class WeightRecord(
    val weightKg: Double,
    val time: Long
)

/** 重量数字格式化：整数不带小数，非整数保留原样（如 62.5） */
fun formatWeight(kg: Double): String =
    if (kg % 1.0 == 0.0) kg.toLong().toString() else kg.toString()

/**
 * 计划分配模式。
 * @param WEEKLY 按周分配：按周一~周日固定，每周重复（原有模式）
 * @param CYCLE  循环计划：不看周几，按「训练日/休息日」序列逐日循环，
 *               如练3休1、练6休1，适合连三休一等轮班作息
 */
enum class ScheduleMode {
    WEEKLY,
    CYCLE
}

/** 循环序列中的一天。 */
sealed class CycleNode {
    /** 休息日 */
    object Rest : CycleNode()

    /** 训练日，绑定一个槽位序号（1=A, 2=B ...） */
    data class Training(val slot: Int) : CycleNode()
}

/** 循环节奏预设。 */
enum class CyclePreset(val title: String, val subtitle: String) {
    SPLIT3_R1("三分化 · 练3休1", "A推 B腿 C拉 休息，一轮一休"),
    SPLIT3_R2("三分化 · 练6休1", "A推 B腿 C拉 推 腿 拉 休息，两轮一休"),
    SPLIT2_R1("二分化 · 练2休1", "A上肢 B下肢 休息，一轮一休"),
    SPLIT2_R2("二分化 · 练4休1", "A上肢 B下肢 上肢 下肢 休息，两轮一休"),
    CUSTOM("自定义编排", "自由添加训练日与休息日")
}

/** 槽位序号 → 字母标签：1→A ... 8→H */
fun slotLabel(slot: Int): String = ('A' + slot - 1).toString()

/** 循环训练日的完整标题，如 "训练日 A" */
fun cycleSlotTitle(slot: Int): String = "训练日 ${slotLabel(slot)}"
