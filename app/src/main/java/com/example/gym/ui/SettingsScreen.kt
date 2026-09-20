package com.example.gym.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.example.gym.R
import com.example.gym.data.DateUtils
import com.example.gym.data.Exercise
import com.example.gym.data.GymRepository
import com.example.gym.data.formatWeight
import androidx.compose.ui.draw.rotate
import com.example.gym.ui.theme.AppCardWhite
import com.example.gym.ui.theme.AppDivider
import com.example.gym.ui.theme.AppPink
import com.example.gym.ui.theme.AppPinkDeep
import com.example.gym.ui.theme.AppTextDark
import com.example.gym.ui.theme.AppTextGray

private data class DialogState(
    val weekday: Int,
    val exercise: Exercise? // null 表示新增
)

@Composable
fun SettingsScreen(
    onOpenRecords: () -> Unit,
    onOpenRecord: (String) -> Unit
) {
    val todayWeekday = DateUtils.weekdayIndex(DateUtils.today())
    var expandedWeekday by remember { mutableStateOf(todayWeekday) }
    var dialogState by remember { mutableStateOf<DialogState?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
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
                    text = "按周一到周日设置训练项目，之后每周该星期都按此计划训练",
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

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AppCardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedWeekday = if (expanded) -1 else weekday
                            }
                            .padding(horizontal = 18.dp, vertical = 16.dp),
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
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_down),
                            contentDescription = null,
                            tint = AppTextGray,
                            modifier = Modifier
                                .size(20.dp)
                        )
                    }

                    AnimatedVisibility(visible = expanded) {
                        Column {
                            HorizontalDivider(color = AppDivider)
                            list.forEach { exercise ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            dialogState = DialogState(weekday, exercise)
                                        }
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
                                }
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 49.dp),
                                    color = AppDivider,
                                    thickness = 0.8.dp
                                )
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

    dialogState?.let { state ->
        ExerciseEditDialog(
            weekday = state.weekday,
            existing = state.exercise,
            onDismiss = { dialogState = null },
            onOpenRecord = onOpenRecord
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
