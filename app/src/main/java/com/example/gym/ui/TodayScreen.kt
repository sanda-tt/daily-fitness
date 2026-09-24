package com.example.gym.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
import com.example.gym.ui.theme.AppGold
import com.example.gym.ui.theme.AppPink
import com.example.gym.ui.theme.AppPinkDeep
import com.example.gym.ui.theme.AppPinkSoft
import com.example.gym.ui.theme.AppTextDark
import com.example.gym.ui.theme.AppTextGray
import com.example.gym.ui.theme.AppUndoGray
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun TodayScreen(
    selectedOffset: Int,
    onSelectOffset: (Int) -> Unit,
    onOpenRecord: (String) -> Unit
) {
    val calendar = DateUtils.dateForOffset(selectedOffset)
    val dateKey = DateUtils.dateKey(calendar)
    val items = GymRepository.itemsForDate(calendar)
    val counts = GymRepository.completedCounts(dateKey)
    val targetOf: (Exercise) -> Int = { it.sets.coerceAtLeast(1) }
    val doneCount = items.count { (counts[it.id] ?: 0) >= targetOf(it) }

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
            // 今日训练主题徽章
            val theme = GymRepository.themeFor(DateUtils.weekdayIndex(calendar))
            if (theme.isNotBlank()) {
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .align(Alignment.Top)
                        .padding(top = 3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(AppPinkSoft)
                        .padding(horizontal = 11.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = theme,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppPinkDeep,
                        maxLines = 1
                    )
                }
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
            shape = RoundedCornerShape(24.dp),
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
                        val target = targetOf(exercise)
                        val doneSets = (counts[exercise.id] ?: 0).coerceIn(0, target)
                        SwipeExerciseRow(
                            exercise = exercise,
                            doneSets = doneSets,
                            targetSets = target,
                            onAdvance = {
                                GymRepository.advanceSet(dateKey, exercise.id, target)
                            },
                            onRetreat = {
                                GymRepository.retreatSet(dateKey, exercise.id)
                            },
                            onOpenHistory = { onOpenRecord(exercise.name) }
                        )
                        if (index != items.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 83.dp, end = 20.dp),
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
 * 可左右滑动的训练项目行 —— 集章打卡：
 * 向左滑过阈值 -> 完成一组（盖一个章），多组项目需逐组滑到设定组数才算完成；
 * 向右滑过阈值 -> 撤回一组。
 */
@Composable
private fun SwipeExerciseRow(
    exercise: Exercise,
    doneSets: Int,
    targetSets: Int,
    onAdvance: () -> Unit,
    onRetreat: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val completed = doneSets >= targetSets
    val hasSets = exercise.sets > 0

    var offsetX by remember(exercise.id) { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val density = LocalDensity.current
    val thresholdPx = with(density) { 100.dp.toPx() }

    // 盖章 / 撤回特效事件
    var stampEvent by remember(exercise.id) { mutableStateOf<StampEvent?>(null) }
    var undoMsg by remember(exercise.id) { mutableStateOf<UndoMsg?>(null) }
    var prevDone by remember(exercise.id) { mutableIntStateOf(doneSets) }

    // 轻量 tick 触感（KEYBOARD_TICK 在新 SDK 已移除，按版本选用）
    val tickHaptic = remember(view) {
        {
            val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.GESTURE_START
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
            view.performHapticFeedback(constant)
        }
    }

    LaunchedEffect(exercise.id, doneSets) {
        if (doneSets > prevDone) {
            val isFinal = doneSets >= targetSets
            stampEvent = StampEvent(doneSets, isFinal)
            if (isFinal && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                tickHaptic()
            }
        } else if (doneSets < prevDone) {
            undoMsg = UndoMsg(prevDone)
            tickHaptic()
        }
        prevDone = doneSets
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (hasSets) 84.dp else 72.dp)
            .clipToBounds()
    ) {
        val maxDragPx = with(density) { maxWidth.toPx() * 0.45f }

        // 右侧：左滑露出 —— 推进 / 完成
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(maxWidth * 0.5f)
                .fillMaxHeight()
                .background(if (completed) AppUndoGray else AppPink),
            contentAlignment = Alignment.CenterEnd
        ) {
            Row(
                modifier = Modifier.padding(end = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = "完成一组",
                    tint = AppCardWhite,
                    modifier = Modifier.size(21.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = when {
                        completed -> "已完成"
                        hasSets -> "第 ${doneSets + 1} 组"
                        else -> "完成"
                    },
                    color = AppCardWhite,
                    fontSize = 15.sp
                )
            }
        }

        // 左侧：右滑露出 —— 撤回（没有已完成组数时不展示）
        if (doneSets > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(maxWidth * 0.5f)
                    .fillMaxHeight()
                    .background(AppUndoGray),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.padding(start = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_undo),
                        contentDescription = "撤回一组",
                        tint = AppCardWhite,
                        modifier = Modifier.size(21.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (hasSets) "撤一组" else "撤销",
                        color = AppCardWhite,
                        fontSize = 15.sp
                    )
                }
            }
        }

        // 前景内容
        Row(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .background(AppCardWhite)
                .pointerInput(exercise.id, doneSets) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val current = offsetX
                            scope.launch {
                                if (current <= -thresholdPx && doneSets < targetSets) {
                                    onAdvance()
                                } else if (current >= thresholdPx && doneSets > 0) {
                                    onRetreat()
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
            SetBadge(done = doneSets, target = targetSets, completed = completed)
            Spacer(Modifier.width(13.dp))
            Text(
                text = exercise.name,
                fontSize = 16.sp,
                color = if (completed) AppCompletedGray else AppTextDark,
                textDecoration = if (completed) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                val info = buildAnnotatedString {
                    exercise.weightKg?.let {
                        withStyle(SpanStyle(color = if (completed) AppCompletedGray else AppPink)) {
                            append("${formatWeight(it)}kg")
                        }
                    }
                    if (exercise.detail.isNotBlank()) {
                        if (exercise.weightKg != null) append("  ")
                        withStyle(SpanStyle(color = if (completed) AppCompletedGray else AppTextGray)) {
                            append(exercise.detail)
                        }
                    }
                }
                if (info.isNotEmpty()) {
                    Text(text = info, fontSize = 14.sp)
                }
                if (hasSets && targetSets <= 8) {
                    if (info.isNotEmpty()) Spacer(Modifier.height(7.dp))
                    SetPips(total = targetSets, done = doneSets, completed = completed)
                }
            }
        }

        // 特效层（最上方，不拦截手势）
        StampEffectLayer(stampEvent)
        UndoLabelLayer(undoMsg, hasSets)
    }
}

/**
 * 左侧徽章：
 * 未开始 -> 哑铃；进行中 -> 进度环 + "已做/总组数"；全部完成 -> 对勾。
 */
@Composable
private fun SetBadge(done: Int, target: Int, completed: Boolean) {
    Box(
        modifier = Modifier.size(52.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(if (completed) AppDivider else AppPinkSoft)
        )
        if (!completed && done > 0) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 4.dp.toPx()
                val inset = stroke / 2f
                val diameter = size.minDimension - stroke
                drawCircle(
                    color = AppDivider,
                    radius = diameter / 2f,
                    center = center,
                    style = Stroke(stroke)
                )
                drawArc(
                    color = AppPink,
                    startAngle = -90f,
                    sweepAngle = done.toFloat() / target * 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(diameter, diameter),
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
        when {
            completed -> Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = "已完成",
                tint = AppCompletedGray,
                modifier = Modifier.size(25.dp)
            )
            done == 0 -> Icon(
                painter = painterResource(R.drawable.ic_dumbbell),
                contentDescription = null,
                tint = AppPink,
                modifier = Modifier.size(26.dp)
            )
            else -> Text(
                text = "$done/$target",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = AppPink
            )
        }
    }
}

/**
 * 右侧组点：完成的实心填充并弹跳，未完成的空心描边。
 */
@Composable
private fun SetPips(total: Int, done: Int, completed: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(total) { index ->
            val filled = index < done
            val scale by animateFloatAsState(
                targetValue = if (filled) 1f else 0.55f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "pip"
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(CircleShape)
                    .background(
                        if (filled) {
                            if (completed) AppCompletedGray else AppPink
                        } else {
                            Color.Transparent
                        }
                    )
                    .border(
                        width = 1.2.dp,
                        color = if (filled) {
                            Color.Transparent
                        } else if (completed) {
                            AppCompletedGray
                        } else {
                            AppPink.copy(alpha = 0.5f)
                        },
                        shape = CircleShape
                    )
            )
        }
    }
}

// ---------------- 盖章特效 ----------------

private data class StampEvent(val setNumber: Int, val final: Boolean)

private data class Particle(
    val angle: Float,
    val distance: Float,
    val radiusDp: Float,
    val gold: Boolean
)

/**
 * 盖章特效层：印章旋转砸下 + 冲击波 + 标签上飘；
 * 最后一组额外有金色扫光和金粉粒子迸发。
 */
@Composable
private fun StampEffectLayer(event: StampEvent?) {
    val density = LocalDensity.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(event) {
        if (event != null) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(700))
        }
    }
    if (event == null) return
    val p = progress.value
    val mainColor = if (event.final) AppGold else AppPink

    Box(Modifier.fillMaxSize()) {
        // 金色扫光
        if (event.final) {
            Box(
                modifier = Modifier
                    .graphicsLayer { alpha = sin(p * PI).toFloat() * 0.22f }
                    .fillMaxSize()
                    .background(AppGold)
            )
        }

        // 金粉粒子
        if (event.final) {
            FinalParticles(p)
        }

        // 锚点：徽章中心（左 padding 18 + 半径 26 = 44dp）
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 18.dp)
                .size(52.dp)
        ) {
            // 冲击波光环
            val ringScale = 0.3f + p * 1.4f
            val ringAlpha = (1f - p).coerceIn(0f, 1f)
            Canvas(Modifier.fillMaxSize()) {
                val strokeWidth = 2.5.dp.toPx()
                drawCircle(
                    color = mainColor,
                    radius = (size.minDimension / 2f) * ringScale,
                    style = Stroke(strokeWidth),
                    alpha = ringAlpha * 0.9f
                )
            }

            // 印章
            val stampScale = when {
                p < 0.16f -> 1.75f + (0.92f - 1.75f) * (p / 0.16f)
                p < 0.4f -> 0.92f + (1.06f - 0.92f) * ((p - 0.16f) / 0.24f)
                else -> 1.06f + (1f - 1.06f) * ((p - 0.4f) / 0.6f)
            }
            val stampRotation = (-16f + p * 120f).coerceAtMost(0f)
            val stampAlpha = when {
                p < 0.08f -> p / 0.08f
                p > 0.78f -> (1f - (p - 0.78f) / 0.22f).coerceAtLeast(0f)
                else -> 1f
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = stampScale
                        scaleY = stampScale
                        rotationZ = stampRotation
                        alpha = stampAlpha
                    }
                    .clip(CircleShape)
                    .background(mainColor)
                    .border(2.dp, Color.White.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (event.final) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                } else {
                    Text(
                        text = "${event.setNumber}",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // 上飘标签
            val labelAlpha = if (p < 0.2f) 1f else (1f - (p - 0.2f) / 0.8f).coerceAtLeast(0f)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        translationY = with(density) { (-(18f + 34f * p)).dp.toPx() }
                        alpha = labelAlpha
                    }
            ) {
                Text(
                    text = if (event.final) "最后一组 · 完成" else "第 ${event.setNumber} 组",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (event.final) AppGold else AppPinkDeep
                )
            }
        }
    }
}

/** 集齐瞬间的金粉粒子迸发。 */
@Composable
private fun FinalParticles(p: Float) {
    val particles = remember {
        List(16) {
            Particle(
                angle = kotlin.random.Random.nextFloat() * (2f * PI.toFloat()),
                distance = 0.6f + kotlin.random.Random.nextFloat() * 0.45f,
                radiusDp = 2.2f + kotlin.random.Random.nextFloat() * 2.6f,
                gold = kotlin.random.Random.nextFloat() > 0.45f
            )
        }
    }
    val eased = 1f - (1f - p) * (1f - p)
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(44.dp.toPx(), size.height / 2f)
        val maxDist = 54.dp.toPx()
        particles.forEach { particle ->
            val r = maxDist * particle.distance * eased
            val pos = Offset(
                center.x + (cos(particle.angle) * r).toFloat(),
                center.y + (sin(particle.angle) * r).toFloat()
            )
            drawCircle(
                color = if (particle.gold) AppGold else AppPink,
                radius = particle.radiusDp.dp.toPx() * (1f - p * 0.4f),
                center = pos,
                alpha = 1f - p
            )
        }
    }
}

// ---------------- 撤回特效 ----------------

private data class UndoMsg(val setNumber: Int)

/** 撤回一组时，灰色小标签从徽章处向下飘并淡出。 */
@Composable
private fun UndoLabelLayer(msg: UndoMsg?, hasSets: Boolean) {
    val density = LocalDensity.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(msg) {
        if (msg != null) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(560))
        }
    }
    if (msg == null) return
    val p = progress.value
    val alpha = if (p < 0.15f) 1f else (1f - (p - 0.15f) / 0.85f).coerceAtLeast(0f)

    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 18.dp)
                .size(52.dp)
                .graphicsLayer {
                    translationY = with(density) { (24f + 26f * p).dp.toPx() }
                    this.alpha = alpha
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .border(0.8.dp, AppDivider, RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (hasSets) "撤回第 ${msg.setNumber} 组" else "已撤销",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppUndoGray
                )
            }
        }
    }
}
