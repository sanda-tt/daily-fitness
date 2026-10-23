package com.example.gym.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gym.R
import com.example.gym.data.CycleNode
import com.example.gym.data.CyclePreset
import com.example.gym.data.DateUtils
import com.example.gym.data.Exercise
import com.example.gym.data.GymRepository
import com.example.gym.data.ScheduleMode
import com.example.gym.data.cycleSlotTitle
import com.example.gym.data.formatWeight
import com.example.gym.data.slotLabel
import com.example.gym.ui.theme.AppCardWhite
import com.example.gym.ui.theme.AppDivider
import com.example.gym.ui.theme.AppPink
import com.example.gym.ui.theme.AppPinkDeep
import com.example.gym.ui.theme.AppPinkSoft
import com.example.gym.ui.theme.AppTextDark
import com.example.gym.ui.theme.AppTextGray
import kotlin.math.roundToInt

/** 设置页中一个可编辑的"天"：按周模式是周一~周日，循环模式是训练日槽位 A/B… */
private data class DaySlot(
    val key: Int,
    val title: String,
    val items: List<Exercise>,
    val theme: String
)

private data class DialogState(
    val slot: Int,
    val exercise: Exercise? // null 表示新增
)

/** 一次进行中的拖拽 */
private class DragSession(
    val sourceSlot: Int,
    val exercise: Exercise,
    var x: Float,
    var y: Float
)

private fun slotItems(mode: ScheduleMode, slot: Int): List<Exercise> = when (mode) {
    ScheduleMode.WEEKLY -> GymRepository.templates[slot].orEmpty()
    ScheduleMode.CYCLE -> GymRepository.cycleTemplates[slot].orEmpty()
}

@Composable
fun SettingsScreen(
    onOpenRecords: () -> Unit,
    onOpenRecord: (String) -> Unit
) {
    val mode = GymRepository.scheduleMode

    // 模式下的全部"天槽位"
    val daySlots = when (mode) {
        ScheduleMode.WEEKLY -> (1..7).map {
            DaySlot(
                key = it,
                title = "周${DateUtils.weekdayChar(it)}",
                items = GymRepository.templates[it].orEmpty(),
                theme = GymRepository.themeFor(it)
            )
        }
        ScheduleMode.CYCLE -> GymRepository.usedCycleSlots().map {
            DaySlot(
                key = it,
                title = cycleSlotTitle(it),
                items = GymRepository.cycleTemplates[it].orEmpty(),
                theme = GymRepository.cycleThemeFor(it)
            )
        }
    }

    // 展开状态：进入时默认展开今天对应的槽位；切换模式时重新计算
    fun initialExpanded(): Int? {
        if (mode == ScheduleMode.WEEKLY) {
            return DateUtils.weekdayIndex(DateUtils.today())
        }
        val todayNode = GymRepository.cycleNodeAt(DateUtils.today())
        return (todayNode as? CycleNode.Training)?.slot ?: daySlots.firstOrNull()?.key
    }
    var expandedSlot by remember(mode) { mutableStateOf(initialExpanded()) }
    var dialogState by remember { mutableStateOf<DialogState?>(null) }
    var clearDialogSlot by remember { mutableStateOf<Int?>(null) }
    var pickSlotDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    // 拖拽相关状态
    var containerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val dayRects = remember { mutableStateMapOf<Int, Rect>() }
    val rowRects = remember { mutableStateMapOf<Pair<Int, Long>, Rect>() }
    var drag by remember { mutableStateOf<DragSession?>(null) }
    var dropSlot by remember { mutableStateOf<Int?>(null) }
    var insertIndex by remember { mutableStateOf<Int?>(null) }

    fun endDragVisual() {
        drag = null
        dropSlot = null
        insertIndex = null
    }

    fun toContainer(coords: LayoutCoordinates, point: Offset): Offset? =
        containerCoords?.localPositionOf(coords, point)

    fun updateDrag(point: Offset) {
        val session = drag ?: return
        session.x = point.x
        session.y = point.y

        val target = dayRects.entries.firstOrNull { it.value.contains(point) }?.key
        if (target == null) {
            dropSlot = null
            insertIndex = null
        } else if (target == session.sourceSlot) {
            dropSlot = target
            val items = slotItems(mode, target)
            var idx = items.size
            for ((i, item) in items.withIndex()) {
                val r = rowRects[target to item.id]
                if (r != null && point.y < r.top + r.height / 2f) {
                    idx = i
                    break
                }
            }
            insertIndex = idx
        } else {
            // 悬停在其他天 → 复制
            dropSlot = target
            insertIndex = null
        }

        // 靠近列表上下边缘时自动滚动
        val height = containerCoords?.size?.height ?: return
        val edge = with(density) { 72.dp.toPx() }
        val step = with(density) { 16.dp.toPx() }
        when {
            point.y < edge -> listState.dispatchRawDelta(-step)
            point.y > height - edge -> listState.dispatchRawDelta(step)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { containerCoords = it },
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp)) {
                    Text(
                        text = "设置",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTextDark
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "长按项目拖动：在当天内调整顺序，拖到其他天即复制；也可一键清空整天计划",
                        fontSize = 13.sp,
                        color = AppTextGray
                    )
                }
            }

            // 计划模式选择
            item {
                ScheduleModeCard(mode)
            }

            // 循环模式：节奏预设 + 序列编排 + 起点
            if (mode == ScheduleMode.CYCLE) {
                item {
                    CyclePresetCard()
                }
                item {
                    CycleSequenceCard(
                        onPickSlot = { pickSlotDialog = true }
                    )
                }
                item {
                    CycleStartCard()
                }
            }

            // 重量记录入口
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)
                        .clickable { onOpenRecords() },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = AppCardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_chart),
                            contentDescription = null,
                            tint = AppPink,
                            modifier = Modifier.size(23.dp)
                        )
                        Spacer(Modifier.width(13.dp))
                        Column {
                            Text(
                                text = "重量记录",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = AppTextDark
                            )
                            Text(
                                text = "记录硬拉、卧推等项目的重量变化",
                                fontSize = 12.sp,
                                color = AppTextGray
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_down),
                            contentDescription = null,
                            tint = AppTextGray,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(180f)
                        )
                    }
                }
            }

            items(daySlots, key = { it.key }) { day ->
                val list = day.items
                val expanded = expandedSlot == day.key
                val session = drag
                val isCopyTarget = session != null &&
                    dropSlot == day.key &&
                    session.sourceSlot != day.key
                val isReorderSource = session != null &&
                    session.sourceSlot == day.key &&
                    dropSlot == day.key

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .onGloballyPositioned { coords ->
                            containerCoords?.let { dayRects[day.key] = it.localBoundingBoxOf(coords) }
                        },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCopyTarget) Color(0xFFFDF0F4) else AppCardWhite
                    ),
                    border = if (isCopyTarget) BorderStroke(1.5.dp, AppPink) else null,
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedSlot = if (expanded) null else day.key
                                }
                                .padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = day.title,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTextDark
                            )
                            // 主题徽章（折叠状态也能看到）
                            if (day.theme.isNotBlank()) {
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(AppPinkSoft)
                                        .padding(horizontal = 9.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = day.theme,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = AppPinkDeep,
                                        maxLines = 1
                                    )
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = if (list.isEmpty()) "休息" else "${list.size} 项",
                                fontSize = 14.sp,
                                color = AppTextGray
                            )
                            Spacer(Modifier.width(6.dp))
                            // 一键清空整天计划
                            if (list.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(50))
                                        .clickable { clearDialogSlot = day.key },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_delete),
                                        contentDescription = "清空${day.title}计划",
                                        tint = AppTextGray,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                            Icon(
                                painter = painterResource(R.drawable.ic_chevron_down),
                                contentDescription = null,
                                tint = AppTextGray,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        AnimatedVisibility(visible = expanded) {
                            Column {
                                HorizontalDivider(color = AppDivider)
                                OutlinedTextField(
                                    value = day.theme,
                                    onValueChange = { input ->
                                        if (mode == ScheduleMode.WEEKLY) {
                                            GymRepository.updateDayTheme(day.key, input.take(20))
                                        } else {
                                            GymRepository.updateCycleTheme(day.key, input.take(20))
                                        }
                                    },
                                    label = { Text("当天主题，如 背+二头") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 18.dp, vertical = 8.dp)
                                )
                                HorizontalDivider(color = AppDivider)
                                list.forEachIndexed { index, exercise ->
                                    if (isReorderSource && insertIndex == index) {
                                        InsertionLine()
                                    }
                                    ExerciseRow(
                                        slot = day.key,
                                        exercise = exercise,
                                        isDragging = session?.sourceSlot == day.key &&
                                            session.exercise.id == exercise.id,
                                        onPositioned = { coords ->
                                            containerCoords?.let {
                                                rowRects[day.key to exercise.id] =
                                                    it.localBoundingBoxOf(coords)
                                            }
                                        },
                                        onRowDisposed = { rowRects.remove(day.key to exercise.id) },
                                        toContainer = ::toContainer,
                                        onClick = { dialogState = DialogState(day.key, exercise) },
                                        onDragStart = { point ->
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            drag = DragSession(day.key, exercise, point.x, point.y)
                                            dropSlot = day.key
                                            insertIndex = index
                                        },
                                        onDrag = ::updateDrag,
                                        onDragEnd = {
                                            val current = drag
                                            if (current != null) {
                                                val target = dropSlot
                                                when {
                                                    target != null && target != current.sourceSlot -> {
                                                        if (mode == ScheduleMode.WEEKLY) {
                                                            GymRepository.copyExerciseTo(
                                                                target, current.exercise
                                                            )
                                                        } else {
                                                            GymRepository.copyCycleExerciseTo(
                                                                target, current.exercise
                                                            )
                                                        }
                                                        expandedSlot = target
                                                        haptic.performHapticFeedback(
                                                            HapticFeedbackType.LongPress
                                                        )
                                                    }
                                                    target == current.sourceSlot -> {
                                                        val from = list.indexOfFirst {
                                                            it.id == current.exercise.id
                                                        }
                                                        val rawTo = insertIndex
                                                        if (from >= 0 && rawTo != null) {
                                                            val to = if (rawTo > from) rawTo - 1 else rawTo
                                                            if (to != from) {
                                                                if (mode == ScheduleMode.WEEKLY) {
                                                                    GymRepository.moveExercise(
                                                                        day.key, from, to
                                                                    )
                                                                } else {
                                                                    GymRepository.moveCycleExercise(
                                                                        day.key, from, to
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            endDragVisual()
                                        },
                                        onDragCancel = { endDragVisual() }
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 49.dp),
                                        color = AppDivider,
                                        thickness = 0.8.dp
                                    )
                                }
                                if (isReorderSource && insertIndex == list.size) {
                                    InsertionLine()
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            dialogState = DialogState(day.key, null)
                                        }
                                        .padding(horizontal = 18.dp, vertical = 15.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_add),
                                        contentDescription = null,
                                        tint = AppPink,
                                        modifier = Modifier.size(19.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "添加项目",
                                        fontSize = 15.sp,
                                        color = AppPink
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 自定义模式：新增训练日类型（槽位）
            if (mode == ScheduleMode.CYCLE && GymRepository.cyclePreset == CyclePreset.CUSTOM) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clickable {
                                val newSlot = GymRepository.addCycleSlot()
                                if (newSlot != null) expandedSlot = newSlot
                            },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        border = BorderStroke(1.5.dp, AppPink.copy(alpha = 0.6f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_add),
                                contentDescription = null,
                                tint = AppPink,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "新增训练日类型",
                                fontSize = 15.sp,
                                color = AppPink
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "每日健身 v${com.example.gym.BuildConfig.VERSION_NAME}",
                    fontSize = 12.sp,
                    color = AppTextGray,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // 跟随手指的拖拽预览（不消费触摸事件）
        val previewSession = drag
        if (previewSession != null) {
            val copying = dropSlot != null && dropSlot != previewSession.sourceSlot
            Column(
                modifier = Modifier.offset {
                    val cardWidth = 220.dp.toPx()
                    val x = (previewSession.x - cardWidth / 2f).roundToInt()
                    val y = (previewSession.y - 74.dp.toPx()).roundToInt()
                    IntOffset(x, y)
                },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (copying) "复制到该天" else "调整顺序",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (copying) AppPinkDeep else AppTextGray)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
                Spacer(Modifier.height(6.dp))
                Card(
                    modifier = Modifier.width(220.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AppCardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_dumbbell),
                            contentDescription = null,
                            tint = AppPink,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text = previewSession.exercise.name,
                            fontSize = 14.sp,
                            color = AppTextDark,
                            maxLines = 1
                        )
                        Spacer(Modifier.weight(1f))
                        previewSession.exercise.weightKg?.let {
                            Text(
                                text = "${formatWeight(it)}kg",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = AppPink
                            )
                        }
                    }
                }
            }
        }
    }

    // 清空整天计划确认
    clearDialogSlot?.let { slot ->
        val count = slotItems(mode, slot).size
        val title = if (mode == ScheduleMode.WEEKLY) {
            "周${DateUtils.weekdayChar(slot)}"
        } else {
            cycleSlotTitle(slot)
        }
        AlertDialog(
            onDismissRequest = { clearDialogSlot = null },
            title = { Text("清空$title 计划？") },
            text = {
                Text("将删除该天全部 $count 个训练项目，历史重量记录不会被删除。")
            },
            confirmButton = {
                TextButton(onClick = {
                    if (mode == ScheduleMode.WEEKLY) {
                        GymRepository.clearDay(slot)
                    } else {
                        GymRepository.clearCycleSlot(slot)
                    }
                    clearDialogSlot = null
                }) {
                    Text("清空", color = AppPinkDeep, fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = { clearDialogSlot = null }) {
                    Text("取消", color = AppTextGray)
                }
            }
        )
    }

    dialogState?.let { state ->
        ExerciseEditDialog(
            mode = mode,
            slot = state.slot,
            existing = state.exercise,
            onDismiss = { dialogState = null },
            onOpenRecord = onOpenRecord
        )
    }

    // 自定义模式：追加训练日时选择槽位
    if (pickSlotDialog) {
        PickSlotDialog(
            onDismiss = { pickSlotDialog = false },
            onPick = { slot ->
                GymRepository.appendCycleTraining(slot)
                pickSlotDialog = false
            }
        )
    }
}

// ---------------- 计划模式选择 ----------------

@Composable
private fun ScheduleModeCard(mode: ScheduleMode) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AppCardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "计划模式",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AppTextDark
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                ModeOption(
                    title = "按周分配",
                    subtitle = "固定周一~周日\n每周重复",
                    selected = mode == ScheduleMode.WEEKLY,
                    modifier = Modifier.weight(1f),
                    onClick = { GymRepository.setMode(ScheduleMode.WEEKLY) }
                )
                ModeOption(
                    title = "循环计划",
                    subtitle = "不看周几\n按序列循环，如练3休1",
                    selected = mode == ScheduleMode.CYCLE,
                    modifier = Modifier.weight(1f),
                    onClick = { GymRepository.setMode(ScheduleMode.CYCLE) }
                )
            }
        }
    }
}

@Composable
private fun ModeOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) AppPink else AppPinkSoft.copy(alpha = 0.45f))
            .then(
                if (selected) Modifier
                else Modifier.border(1.dp, AppPink.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Column {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else AppTextDark
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = if (selected) Color.White.copy(alpha = 0.9f) else AppTextGray
            )
        }
    }
}

// ---------------- 循环节奏预设 ----------------

@Composable
private fun CyclePresetCard() {
    val current = GymRepository.cyclePreset
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AppCardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 14.dp)) {
            Text(
                text = "循环节奏",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AppTextDark,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(6.dp))
            CyclePreset.entries.forEach { preset ->
                PresetRow(
                    preset = preset,
                    selected = current == preset,
                    onClick = { GymRepository.applyCyclePreset(preset) }
                )
            }
        }
    }
}

@Composable
private fun PresetRow(
    preset: CyclePreset,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 单选圆点
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(50))
                .then(
                    if (selected) Modifier.background(AppPink)
                    else Modifier.border(1.5.dp, AppDivider, RoundedCornerShape(50))
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = preset.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = AppTextDark
            )
            Text(
                text = preset.subtitle,
                fontSize = 12.sp,
                color = AppTextGray
            )
        }
    }
}

// ---------------- 循环序列编排 ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CycleSequenceCard(onPickSlot: () -> Unit) {
    val nodes = GymRepository.cycleNodes
    val isCustom = GymRepository.cyclePreset == CyclePreset.CUSTOM
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AppCardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "循环序列（按日历日循环，不看周几）",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AppTextDark
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "点击节点可把该日对齐到今天",
                fontSize = 12.sp,
                color = AppTextGray
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                nodes.forEachIndexed { index, node ->
                    SequenceNodeChip(
                        index = index,
                        node = node,
                        removable = isCustom,
                        onClick = { GymRepository.alignTodayToNode(index) },
                        onRemove = { GymRepository.removeCycleNodeAt(index) }
                    )
                }
            }
            if (isCustom) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    SmallDashedButton(
                        text = "+ 训练日",
                        modifier = Modifier.weight(1f),
                        onClick = onPickSlot
                    )
                    SmallDashedButton(
                        text = "+ 休息日",
                        modifier = Modifier.weight(1f),
                        onClick = { GymRepository.appendCycleRest() }
                    )
                }
            }
        }
    }
}

@Composable
private fun SequenceNodeChip(
    index: Int,
    node: CycleNode,
    removable: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val isRest = node is CycleNode.Rest
    val theme = if (node is CycleNode.Training) GymRepository.cycleThemeFor(node.slot) else ""
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isRest) AppDivider.copy(alpha = 0.35f) else AppPinkSoft)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = if (removable) 4.dp else 10.dp, top = 7.dp, bottom = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isRest) "休" else slotLabel((node as CycleNode.Training).slot),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isRest) AppTextGray else AppPinkDeep
            )
            if (theme.isNotBlank()) {
                Spacer(Modifier.width(5.dp))
                Text(
                    text = theme,
                    fontSize = 12.sp,
                    color = AppTextGray,
                    maxLines = 1
                )
            }
            if (removable) {
                Spacer(Modifier.width(3.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = "删除该节点",
                    tint = AppTextGray,
                    modifier = Modifier
                        .size(15.dp)
                        .clickable(onClick = onRemove)
                )
            }
        }
    }
}

@Composable
private fun SmallDashedButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.2.dp, AppPink.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = AppPink,
            fontWeight = FontWeight.Medium
        )
    }
}

// ---------------- 循环起点 ----------------

@Composable
private fun CycleStartCard() {
    val start = DateUtils.parseKey(GymRepository.cycleStartKey)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AppCardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "循环起点",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTextDark
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${DateUtils.fullDate(start)} · 序列从该日逐日推进，与周几无关",
                    fontSize = 12.sp,
                    color = AppTextGray
                )
            }
            TextButton(onClick = { GymRepository.resetCycleStartToToday() }) {
                Text("重置为今天", color = AppPink, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ---------------- 选择槽位对话框 ----------------

@Composable
private fun PickSlotDialog(
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit
) {
    val slots = GymRepository.usedCycleSlots().ifEmpty { (1..3).toList() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择训练日类型") },
        text = {
            Column {
                slots.forEach { slot ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(slot) }
                            .padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(AppPinkSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = slotLabel(slot),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppPinkDeep
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = GymRepository.cycleThemeFor(slot).ifBlank { cycleSlotTitle(slot) },
                            fontSize = 15.sp,
                            color = AppTextDark
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = AppTextGray)
            }
        }
    )
}

/** 拖拽排序时的粉色插入指示线 */
@Composable
private fun InsertionLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 2.dp)
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(AppPink)
    )
}

@Composable
private fun ExerciseRow(
    slot: Int,
    exercise: Exercise,
    isDragging: Boolean,
    onPositioned: (LayoutCoordinates) -> Unit,
    onRowDisposed: () -> Unit,
    toContainer: (LayoutCoordinates, Offset) -> Offset?,
    onClick: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    var rowCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    DisposableEffect(slot, exercise.id) {
        onDispose(onRowDisposed)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isDragging) 0.3f else 1f)
            .onGloballyPositioned { coords ->
                rowCoords = coords
                onPositioned(coords)
            }
            .pointerInput(slot, exercise.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val coords = rowCoords ?: return@detectDragGesturesAfterLongPress
                        toContainer(coords, offset)?.let(onDragStart)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val coords = rowCoords ?: return@detectDragGesturesAfterLongPress
                        toContainer(coords, change.position)?.let(onDrag)
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel
                )
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_dumbbell),
            contentDescription = null,
            tint = AppPink,
            modifier = Modifier.size(19.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = exercise.name,
            fontSize = 15.sp,
            color = AppTextDark
        )
        Spacer(Modifier.weight(1f))
        exercise.weightKg?.let {
            Text(
                text = "${formatWeight(it)}kg",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = AppPink
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = exercise.planText,
            fontSize = 14.sp,
            color = AppTextGray
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            painter = painterResource(R.drawable.ic_drag),
            contentDescription = null,
            tint = AppDivider,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ExerciseEditDialog(
    mode: ScheduleMode,
    slot: Int,
    existing: Exercise?,
    onDismiss: () -> Unit,
    onOpenRecord: (String) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var sets by remember { mutableStateOf(existing?.sets?.takeIf { it > 0 }?.toString() ?: "") }
    var detail by remember { mutableStateOf(existing?.detail ?: "") }
    var weightField by remember {
        val initial = existing?.weightKg?.let { formatWeight(it) } ?: ""
        mutableStateOf(
            androidx.compose.ui.text.input.TextFieldValue(
                text = initial,
                selection = androidx.compose.ui.text.TextRange(0, initial.length)
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (existing == null) "添加项目" else "编辑项目")
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("项目名称") },
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Row {
                    OutlinedTextField(
                        value = sets,
                        onValueChange = { sets = it.filter { ch -> ch.isDigit() }.take(4) },
                        label = { Text("组数（可空）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = detail,
                        onValueChange = { detail = it.take(12) },
                        label = { Text("次数 / 时长") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = weightField,
                    onValueChange = { input ->
                        val filtered = input.text.filter { ch -> ch.isDigit() || ch == '.' }.take(6)
                        weightField = if (filtered == input.text) {
                            input
                        } else {
                            input.copy(text = filtered)
                        }
                    },
                    label = { Text("重量 kg（可空，改动后自动记录）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Spacer(Modifier.height(10.dp))
                val previewWeight = weightField.text.toDoubleOrNull()
                val preview = Exercise(
                    0,
                    name.ifBlank { "项目" },
                    sets.toIntOrNull() ?: 0,
                    detail,
                    previewWeight
                )
                val previewText = listOfNotNull(
                    preview.weightText.ifBlank { null },
                    preview.planText.ifBlank { null }
                ).joinToString("  ").ifBlank { "仅项目名称" }
                Text(
                    text = "显示效果：$previewText",
                    fontSize = 12.sp,
                    color = AppTextGray
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) return@TextButton
                val setsValue = sets.toIntOrNull()?.coerceAtLeast(0) ?: 0
                val detailValue = detail.trim()
                val weightValue = weightField.text.toDoubleOrNull()
                if (existing == null) {
                    if (mode == ScheduleMode.WEEKLY) {
                        GymRepository.addExercise(
                            slot, name.trim(), setsValue, detailValue, weightValue
                        )
                    } else {
                        GymRepository.addCycleExercise(
                            slot, name.trim(), setsValue, detailValue, weightValue
                        )
                    }
                } else {
                    val updated = existing.copy(
                        name = name.trim(),
                        sets = setsValue,
                        detail = detailValue,
                        weightKg = weightValue
                    )
                    if (mode == ScheduleMode.WEEKLY) {
                        GymRepository.updateExercise(slot, updated)
                    } else {
                        GymRepository.updateCycleExercise(slot, updated)
                    }
                }
                onDismiss()
            }) {
                Text("保存", color = AppPink, fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (existing != null && GymRepository.historyFor(existing.name).isNotEmpty()) {
                    TextButton(onClick = {
                        onOpenRecord(existing.name)
                        onDismiss()
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_chart),
                            contentDescription = "重量记录",
                            tint = AppPink,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("记录", color = AppPink)
                    }
                }
                if (existing != null) {
                    TextButton(onClick = {
                        if (mode == ScheduleMode.WEEKLY) {
                            GymRepository.deleteExercise(slot, existing.id)
                        } else {
                            GymRepository.deleteCycleExercise(slot, existing.id)
                        }
                        onDismiss()
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete),
                            contentDescription = "删除",
                            tint = AppPinkDeep,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("删除", color = AppPinkDeep)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("取消", color = AppTextGray)
                }
            }
        }
    )
}
