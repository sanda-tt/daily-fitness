package com.example.gym.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gym.R
import com.example.gym.data.DateUtils
import com.example.gym.data.Exercise
import com.example.gym.data.GymRepository
import com.example.gym.data.formatWeight
import com.example.gym.ui.theme.AppCardWhite
import com.example.gym.ui.theme.AppCompletedGray
import com.example.gym.ui.theme.AppDivider
import com.example.gym.ui.theme.AppPink
import com.example.gym.ui.theme.AppPinkSoft
import com.example.gym.ui.theme.AppTextDark
import com.example.gym.ui.theme.AppTextGray
import com.example.gym.ui.theme.AppUndoGray
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun TodayScreen(
    selectedOffset: Int,
    onSelectOffset: (Int) -> Unit,
    onOpenRecord: (String) -> Unit
) {
    val calendar = DateUtils.dateForOffset(selectedOffset)
    val dateKey = DateUtils.dateKey(calendar)
    val items = GymRepository.itemsForDate(calendar)
    val completedIds = GymRepository.completedIds(dateKey)
    val doneCount = items.count { completedIds.contains(it.id) }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        DateStrip(
            selectedOffset = selectedOffset,
            onSelectOffset = onSelectOffset
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = when (selectedOffset) {
                        0 -> "今天"
                        1 -> "明天"
                        -1 -> "昨天"
                        else -> DateUtils.monthDay(calendar)
                    },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTextDark
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${DateUtils.fullDate(calendar)} 周${DateUtils.weekdayChar(calendar)}",
                    fontSize = 13.sp,
                    color = AppTextGray
                )
            }
            Spacer(Modifier.weight(1f))
            if (items.isNotEmpty()) {
                Text(
                    text = if (doneCount == items.size) "全部完成" else "$doneCount/${items.size} 完成",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (doneCount == items.size) AppPink else AppTextGray
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .weight(1f),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = AppCardWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "这天还没有安排训练项目\n去「设置」里添加吧",
                        fontSize = 15.sp,
                        color = AppTextGray,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(Modifier.height(4.dp))
                    items.forEachIndexed { index, exercise ->
                        SwipeExerciseRow(
                            exercise = exercise,
                            completed = completedIds.contains(exercise.id),
                            onComplete = {
                                GymRepository.setCompleted(dateKey, exercise.id, true)
                            },
                            onUndo = {
                                GymRepository.setCompleted(dateKey, exercise.id, false)
                            },
                            onOpenHistory = { onOpenRecord(exercise.name) }
                        )
                        if (index != items.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 74.dp, end = 20.dp),
                                color = AppDivider,
                                thickness = 0.8.dp
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

/**
 * 可左右滑动的训练项目行：
 * 向左滑过阈值 -> 标记完成；向右滑过阈值 -> 撤销完成。
 */
@Composable
private fun SwipeExerciseRow(
    exercise: Exercise,
    completed: Boolean,
    onComplete: () -> Unit,
    onUndo: () -> Unit,
    onOpenHistory: () -> Unit
) {
    var offsetX by remember(exercise.id) { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val thresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        100.dp.toPx()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clipToBounds()
    ) {
        val maxDragPx = with(androidx.compose.ui.platform.LocalDensity.current) {
            maxWidth.toPx() * 0.45f
        }

        // 右侧：完成（左滑时露出），只占右半区，避免与左侧撤销层互相覆盖
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(maxWidth * 0.5f)
                .fillMaxHeight()
                .background(AppPink),
            contentAlignment = Alignment.CenterEnd
        ) {
            Row(
                modifier = Modifier.padding(end = 26.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = "完成",
                    tint = AppCardWhite,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("完成", color = AppCardWhite, fontSize = 15.sp)
            }
        }

        // 左侧：撤销（右滑时露出），只占左半区
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(maxWidth * 0.5f)
                .fillMaxHeight()
                .background(AppUndoGray),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.padding(start = 26.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_undo),
                    contentDescription = "撤销",
                    tint = AppCardWhite,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("撤销", color = AppCardWhite, fontSize = 15.sp)
            }
        }

        // 前景内容
        Row(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .background(AppCardWhite)
                .pointerInput(exercise.id, completed) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val current = offsetX
                            scope.launch {
                                if (current <= -thresholdPx && !completed) {
                                    onComplete()
                                } else if (current >= thresholdPx && completed) {
                                    onUndo()
                                }
                                animate(
                                    initialValue = current,
                                    targetValue = 0f,
                                    animationSpec = tween(220)
                                ) { value, _ -> offsetX = value }
                            }
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount).coerceIn(-maxDragPx, maxDragPx)
                    }
                }
                .pointerInput(exercise.id) {
                    detectTapGestures(onTap = { onOpenHistory() })
                }
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (completed) AppDivider else AppPinkSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_dumbbell),
                    contentDescription = null,
                    tint = if (completed) AppCompletedGray else AppPink,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text = exercise.name,
                fontSize = 17.sp,
                color = if (completed) AppCompletedGray else AppTextDark,
                textDecoration = if (completed) TextDecoration.LineThrough else null
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = buildAnnotatedString {
                    exercise.weightKg?.let {
                        withStyle(SpanStyle(color = if (completed) AppCompletedGray else AppPink)) {
                            append("${formatWeight(it)}kg  ")
                        }
                    }
                    append(exercise.planText)
                },
                fontSize = 15.sp,
                color = if (completed) AppCompletedGray else AppTextGray,
                textDecoration = if (completed) TextDecoration.LineThrough else null
            )
        }
    }
}
