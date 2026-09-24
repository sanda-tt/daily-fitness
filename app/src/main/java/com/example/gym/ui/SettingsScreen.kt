package com.example.gym.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.example.gym.data.DateUtils
import com.example.gym.data.Exercise
import com.example.gym.data.GymRepository
import com.example.gym.data.formatWeight
import com.example.gym.ui.theme.AppCardWhite
import com.example.gym.ui.theme.AppDivider
import com.example.gym.ui.theme.AppPink
import com.example.gym.ui.theme.AppPinkDeep
import com.example.gym.ui.theme.AppTextDark
import com.example.gym.ui.theme.AppTextGray
import kotlin.math.roundToInt

private data class DialogState(
    val weekday: Int,
    val exercise: Exercise? // null 表示新增
)

/** 一次进行中的拖拽 */
private class DragSession(
    val sourceWeekday: Int,
    val exercise: Exercise,
    var x: Float,
    var y: Float
)

@Composable
fun SettingsScreen(
    onOpenRecords: () -> Unit,
    onOpenRecord: (String) -> Unit
) {
    val todayWeekday = DateUtils.weekdayIndex(DateUtils.today())
    var expandedWeekday by remember { mutableStateOf(todayWeekday) }
    var dialogState by remember { mutableStateOf<DialogState?>(null) }
    var clearDialogWeekday by remember { mutableStateOf<Int?>(null) }

    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    // 拖拽相关状态
    var containerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val dayRects = remember { mutableStateMapOf<Int, Rect>() }
    val rowRects = remember { mutableStateMapOf<Pair<Int, Long>, Rect>() }
    var drag by remember { mutableStateOf<DragSession?>(null) }
    var dropWeekday by remember { mutableStateOf<Int?>(null) }
    var insertIndex by remember { mutableStateOf<Int?>(null) }

    fun endDragVisual() {
        drag = null
        dropWeekday = null
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
            dropWeekday = null
            insertIndex = null
        } else if (target == session.sourceWeekday) {
            dropWeekday = target
            val items = GymRepository.templates[target].orEmpty()
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
            dropWeekday = target
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

                // 重量记录入口
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

            items(7) { position ->
                val weekday = position + 1 // 周一=1 ... 周日=7
                val list = GymRepository.templates[weekday].orEmpty()
                val expanded = expandedWeekday == weekday
                val session = drag
                val isCopyTarget = session != null &&
                    dropWeekday == weekday &&
                    session.sourceWeekday != weekday
                val isReorderSource = session != null &&
                    session.sourceWeekday == weekday &&
                    dropWeekday == weekday

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .onGloballyPositioned { coords ->
                            containerCoords?.let { dayRects[weekday] = it.localBoundingBoxOf(coords) }
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
                                    expandedWeekday = if (expanded) -1 else weekday
                                }
                                .padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "周${DateUtils.weekdayChar(weekday)}",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTextDark
                            )
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
                                        .clickable { clearDialogWeekday = weekday },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_delete),
                                        contentDescription = "清空周${DateUtils.weekdayChar(weekday)}计划",
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
                                list.forEachIndexed { index, exercise ->
                                    if (isReorderSource && insertIndex == index) {
                                        InsertionLine()
                                    }
                                    ExerciseRow(
                                        weekday = weekday,
                                        exercise = exercise,
                                        isDragging = session?.sourceWeekday == weekday &&
                                            session.exercise.id == exercise.id,
                                        onPositioned = { coords ->
                                            containerCoords?.let {
                                                rowRects[weekday to exercise.id] =
                                                    it.localBoundingBoxOf(coords)
                                            }
                                        },
                                        onRowDisposed = { rowRects.remove(weekday to exercise.id) },
                                        toContainer = ::toContainer,
                                        onClick = { dialogState = DialogState(weekday, exercise) },
                                        onDragStart = { point ->
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            drag = DragSession(weekday, exercise, point.x, point.y)
                                            dropWeekday = weekday
                                            insertIndex = index
                                        },
                                        onDrag = ::updateDrag,
                                        onDragEnd = {
                                            val current = drag
                                            if (current != null) {
                                                val target = dropWeekday
                                                when {
                                                    target != null && target != current.sourceWeekday -> {
                                                        GymRepository.copyExerciseTo(target, current.exercise)
                                                        expandedWeekday = target
                                                        haptic.performHapticFeedback(
                                                            HapticFeedbackType.LongPress
                                                        )
                                                    }
                                                    target == current.sourceWeekday -> {
                                                        val from = list.indexOfFirst {
                                                            it.id == current.exercise.id
                                                        }
                                                        val rawTo = insertIndex
                                                        if (from >= 0 && rawTo != null) {
                                                            val to = if (rawTo > from) rawTo - 1 else rawTo
                                                            if (to != from) {
                                                                GymRepository.moveExercise(weekday, from, to)
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
                                            dialogState = DialogState(weekday, null)
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
            val copying = dropWeekday != null && dropWeekday != previewSession.sourceWeekday
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
                    text = if (copying)
                        "复制到 周${DateUtils.weekdayChar(dropWeekday!!)}"
                    else "调整顺序",
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
    clearDialogWeekday?.let { weekday ->
        val count = GymRepository.templates[weekday].orEmpty().size
        AlertDialog(
            onDismissRequest = { clearDialogWeekday = null },
            title = { Text("清空周${DateUtils.weekdayChar(weekday)}计划？") },
            text = {
                Text("将删除该天全部 $count 个训练项目，历史重量记录不会被删除。")
            },
            confirmButton = {
                TextButton(onClick = {
                    GymRepository.clearDay(weekday)
                    clearDialogWeekday = null
                }) {
                    Text("清空", color = AppPinkDeep, fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = { clearDialogWeekday = null }) {
                    Text("取消", color = AppTextGray)
                }
            }
        )
    }

    dialogState?.let { state ->
        ExerciseEditDialog(
            weekday = state.weekday,
            existing = state.exercise,
            onDismiss = { dialogState = null },
            onOpenRecord = onOpenRecord
        )
    }
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
    weekday: Int,
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

    DisposableEffect(weekday, exercise.id) {
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
            .pointerInput(weekday, exercise.id) {
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
    weekday: Int,
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
                    GymRepository.addExercise(
                        weekday, name.trim(), setsValue, detailValue, weightValue
                    )
                } else {
                    GymRepository.updateExercise(
                        weekday,
                        existing.copy(
                            name = name.trim(),
                            sets = setsValue,
                            detail = detailValue,
                            weightKg = weightValue
                        )
                    )
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
                        GymRepository.deleteExercise(weekday, existing.id)
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
