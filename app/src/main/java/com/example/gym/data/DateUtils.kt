package com.example.gym.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 日期工具。minSdk 24，java.time 需要 API 26，因此统一使用 Calendar。
 */
object DateUtils {

    private val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

    private val timeFormat = SimpleDateFormat("M月d日 HH:mm", Locale.CHINA)

    private val mdFormat = SimpleDateFormat("M/d", Locale.CHINA)

    private val dayOfWeekChars = charArrayOf('日', '一', '二', '三', '四', '五', '六')

    private fun Calendar.clearTime() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    fun today(): Calendar = Calendar.getInstance().apply { clearTime() }

    /** offset=0 今天，1 明天，-1 昨天 */
    fun dateForOffset(offset: Int): Calendar = today().apply {
        add(Calendar.DATE, offset)
    }

    fun dateKey(calendar: Calendar): String = synchronized(keyFormat) {
        keyFormat.format(calendar.time)
    }

    /** 周一=1 ... 周日=7 */
    fun weekdayIndex(calendar: Calendar): Int =
        (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1

    /** 日历上的星期字符：日/一/二/三/四/五/六 */
    fun weekdayChar(calendar: Calendar): Char =
        dayOfWeekChars[calendar.get(Calendar.DAY_OF_WEEK) - 1]

    /** 按周一=1 ... 周日=7 取星期字符 */
    fun weekdayChar(mondayBasedIndex: Int): Char = when (mondayBasedIndex) {
        7 -> '日'
        else -> dayOfWeekChars[mondayBasedIndex]
    }

    fun dayOfMonth(calendar: Calendar): Int = calendar.get(Calendar.DAY_OF_MONTH)

    fun monthDay(calendar: Calendar): String =
        "${calendar.get(Calendar.MONTH) + 1}月${calendar.get(Calendar.DAY_OF_MONTH)}日"

    fun fullDate(calendar: Calendar): String =
        "${calendar.get(Calendar.YEAR)}年${calendar.get(Calendar.MONTH) + 1}月${calendar.get(Calendar.DAY_OF_MONTH)}日"

    /** 时间戳 → "M月d日 HH:mm" */
    fun formatTime(millis: Long): String = synchronized(timeFormat) {
        timeFormat.format(java.util.Date(millis))
    }

    /** 时间戳 → "M/d"，用于图表横轴 */
    fun monthDayShort(millis: Long): String = synchronized(mdFormat) {
        mdFormat.format(java.util.Date(millis))
    }
}
