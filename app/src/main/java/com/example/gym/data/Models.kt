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
