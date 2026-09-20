package com.example.gym.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gym.R
import com.example.gym.data.DateUtils
import com.example.gym.data.GymRepository
import com.example.gym.data.WeightRecord
import com.example.gym.data.formatWeight
import com.example.gym.ui.theme.AppBgPink
import com.example.gym.ui.theme.AppCardWhite
import com.example.gym.ui.theme.AppDivider
import com.example.gym.ui.theme.AppPink
import com.example.gym.ui.theme.AppTextDark
import com.example.gym.ui.theme.AppTextGray
import kotlin.math.ceil

/** 记录中心：所有有重量记录的项目 */
@Composable
fun RecordsHubScreen(
    onOpen: (String) -> Unit,
    onBack: () -> Unit
) {
    val items = GymRepository.recordedExercises()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBgPink)
            .statusBarsPadding()
    ) {
        RecordTopBar(title = "重量记录", onBack = onBack)

        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "还没有重量记录\n在「设置」里给项目填写重量后\n每次调整都会自动记录下来",
                    fontSize = 15.sp,
                    color = AppTextGray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(6.dp))
                items.forEach { (name, records) ->
                    val latest = records.last()
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable { onOpen(name) },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = AppCardWhite),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_chart),
                                contentDescription = null,
                                tint = AppPink,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = AppTextDark
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "上次更新 ${DateUtils.formatTime(latest.time)}",
                                    fontSize = 12.sp,
                                    color = AppTextGray
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "${formatWeight(latest.weightKg)}kg",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppPink
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

/** 项目重量详情：折线图 + 统计 + 历史列表 */
@Composable
fun ExerciseRecordScreen(
    name: String,
    onBack: () -> Unit
) {
    val records = GymRepository.historyFor(name)
    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBgPink)
            .statusBarsPadding()
    ) {
        RecordTopBar(title = name, onBack = onBack)

        if (records.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "还没有重量记录\n点下方按钮记录第一次的重量",
                    fontSize = 15.sp,
                    color = AppTextGray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(6.dp))

                // 折线图
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = AppCardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    WeightChart(
                        records = records,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(230.dp)
                            .padding(vertical = 10.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                // 统计
                val start = records.first().weightKg
                val current = records.last().weightKg
                val change = current - start
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = AppCardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatItem("当前", "${formatWeight(current)}kg", Modifier.weight(1f))
                        Box(
                            Modifier
                                .width(0.8.dp)
                                .height(34.dp)
                                .background(AppDivider)
                        )
                        StatItem("起始", "${formatWeight(start)}kg", Modifier.weight(1f))
                        Box(
                            Modifier
                                .width(0.8.dp)
                                .height(34.dp)
                                .background(AppDivider)
                        )
                        val changeText = when {
                            change > 0 -> "+${formatWeight(change)}kg"
                            change < 0 -> "${formatWeight(change)}kg"
                            else -> "持平"
                        }
                        StatItem("累计变化", changeText, Modifier.weight(1f), highlight = change > 0)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 新增记录按钮
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAddDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AppPink),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = null,
                            tint = AppCardWhite,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "记录新重量",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppCardWhite
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 历史列表（最新在前）
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = AppCardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column {
                        Text(
                            text = "历史记录",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTextDark,
                            modifier = Modifier.padding(start = 18.dp, top = 16.dp, bottom = 8.dp)
                        )
                        records.asReversed().forEachIndexed { index, record ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = DateUtils.formatTime(record.time),
                                        fontSize = 14.sp,
                                        color = AppTextDark
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = "${formatWeight(record.weightKg)}kg",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = AppPink
                                )
                                Spacer(Modifier.width(14.dp))
                                Icon(
                                    painter = painterResource(R.drawable.ic_delete),
                                    contentDescription = "删除这条记录",
                                    tint = AppTextGray,
                                    modifier = Modifier
                                        .size(19.dp)
                                        .clickable {
                                            GymRepository.deleteWeightRecord(name, record.time)
                                        }
                                )
                            }
                            if (index != records.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 18.dp),
                                    color = AppDivider,
                                    thickness = 0.8.dp
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (showAddDialog) {
        AddWeightDialog(
            currentWeight = records.lastOrNull()?.weightKg,
            onDismiss = { showAddDialog = false },
            onConfirm = { weight ->
                GymRepository.recordWeight(name, weight)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddWeightDialog(
    currentWeight: Double?,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var field by remember {
        val initial = currentWeight?.let { formatWeight(it) } ?: ""
        mutableStateOf(TextFieldValue(text = initial, selection = TextRange(0, initial.length)))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记录新重量") },
        text = {
            Column {
                OutlinedTextField(
                    value = field,
                    onValueChange = { input ->
                        val filtered = input.text.filter { ch -> ch.isDigit() || ch == '.' }.take(6)
                        field = if (filtered == input.text) input else input.copy(text = filtered)
                    },
                    label = { Text("重量（公斤 kg）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "记录后，设置里该项目的重量也会同步更新",
                    fontSize = 12.sp,
                    color = AppTextGray
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val value = field.text.toDoubleOrNull()
                if (value != null && value > 0) onConfirm(value)
            }) {
                Text("保存", color = AppPink, fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = AppTextGray)
            }
        }
    )
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = if (highlight) AppPink else AppTextDark
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = AppTextGray
        )
    }
}

@Composable
private fun RecordTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_back),
            contentDescription = "返回",
            tint = AppTextDark,
            modifier = Modifier
                .size(38.dp)
                .clickable { onBack() }
                .padding(7.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = title,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = AppTextDark
        )
    }
}

/** 重量变化折线图（Canvas 手绘，无第三方依赖） */
@Composable
private fun WeightChart(
    records: List<WeightRecord>,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val gridColor = AppDivider

    Canvas(modifier = modifier) {
        val n = records.size
        val left = with(density) { 46.dp.toPx() }
        val right = with(density) { 18.dp.toPx() }
        val top = with(density) { 28.dp.toPx() }
        val bottom = with(density) { 34.dp.toPx() }
        val chartW = size.width - left - right
        val chartH = size.height - top - bottom

        var minW = records.minOf { it.weightKg }
        var maxW = records.maxOf { it.weightKg }
        if (minW == maxW) {
            minW -= 2.5
            maxW += 2.5
        }
        val pad = (maxW - minW) * 0.15
        minW -= pad
        maxW += pad

        fun yPos(w: Double): Float =
            top + chartH * (1f - ((w - minW) / (maxW - minW)).toFloat())

        fun xPos(i: Int): Float =
            if (n == 1) left + chartW / 2f else left + chartW * i / (n - 1)

        val gridLabelPaint = Paint().apply {
            color = AppTextGray.toArgb()
            textSize = with(density) { 11.sp.toPx() }
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        val dateLabelPaint = Paint().apply {
            color = AppTextGray.toArgb()
            textSize = with(density) { 10.sp.toPx() }
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val valueLabelPaint = Paint().apply {
            color = AppPink.toArgb()
            textSize = with(density) { 11.sp.toPx() }
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }

        // 横向网格 + 纵轴刻度
        for (g in 0..4) {
            val gy = top + chartH * g / 4f
            drawLine(
                color = gridColor,
                start = androidx.compose.ui.geometry.Offset(left, gy),
                end = androidx.compose.ui.geometry.Offset(left + chartW, gy),
                strokeWidth = 1f
            )
            val gw = maxW - (maxW - minW) * g / 4f
            drawContext.canvas.nativeCanvas.drawText(
                formatWeight(gw),
                left - with(density) { 8.dp.toPx() },
                gy + with(density) { 4.dp.toPx() },
                gridLabelPaint
            )
        }

        // 折线
        val linePath = Path().apply {
            records.forEachIndexed { i, record ->
                val px = xPos(i)
                val py = yPos(record.weightKg)
                if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
        }
        drawPath(
            path = linePath,
            color = AppPink,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = with(density) { 3.dp.toPx() },
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // 数据点、数值、日期
        val dateLabelEvery = if (n <= 8) 1 else ceil(n / 8f).toInt()
        records.forEachIndexed { i, record ->
            val px = xPos(i)
            val py = yPos(record.weightKg)

            drawCircle(
                color = Color.White,
                radius = with(density) { 6.dp.toPx() },
                center = androidx.compose.ui.geometry.Offset(px, py)
            )
            drawCircle(
                color = AppPink,
                radius = with(density) { 4.2.dp.toPx() },
                center = androidx.compose.ui.geometry.Offset(px, py)
            )

            if (n <= 6) {
                drawContext.canvas.nativeCanvas.drawText(
                    formatWeight(record.weightKg),
                    px,
                    py - with(density) { 11.dp.toPx() },
                    valueLabelPaint
                )
            }

            if (i % dateLabelEvery == 0 || i == n - 1) {
                drawContext.canvas.nativeCanvas.drawText(
                    DateUtils.monthDayShort(record.time),
                    px,
                    top + chartH + with(density) { 20.dp.toPx() },
                    dateLabelPaint
                )
            }
        }
    }
}
