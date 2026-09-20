package com.example.gym

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.gym.data.GymRepository
import com.example.gym.ui.ExerciseRecordScreen
import com.example.gym.ui.RecordsHubScreen
import com.example.gym.ui.SettingsScreen
import com.example.gym.ui.TodayScreen
import com.example.gym.ui.theme.AppBgPink
import com.example.gym.ui.theme.AppIconGray
import com.example.gym.ui.theme.AppPink
import com.example.gym.ui.theme.GymTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GymRepository.init(applicationContext)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = true
        setContent {
            GymTheme(dynamicColor = false) {
                var currentTab by rememberSaveable { mutableIntStateOf(0) }
                var selectedOffset by rememberSaveable { mutableIntStateOf(0) }
                var recordsHubOpen by rememberSaveable { mutableStateOf(false) }
                var recordExerciseName by rememberSaveable { mutableStateOf<String?>(null) }

                // 全屏记录页覆盖在主界面之上
                recordExerciseName?.let { name ->
                    BackHandler { recordExerciseName = null }
                    ExerciseRecordScreen(
                        name = name,
                        onBack = { recordExerciseName = null }
                    )
                    return@GymTheme
                }

                if (recordsHubOpen) {
                    BackHandler { recordsHubOpen = false }
                    RecordsHubScreen(
                        onOpen = { name ->
                            recordsHubOpen = false
                            recordExerciseName = name
                        },
                        onBack = { recordsHubOpen = false }
                    )
                    return@GymTheme
                }

                Scaffold(
                    containerColor = AppBgPink,
                    bottomBar = {
                        GymBottomBar(
                            currentTab = currentTab,
                            onSelect = { currentTab = it }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentTab) {
                            0 -> TodayScreen(
                                selectedOffset = selectedOffset,
                                onSelectOffset = { selectedOffset = it },
                                onOpenRecord = { name -> recordExerciseName = name }
                            )
                            else -> SettingsScreen(
                                onOpenRecords = { recordsHubOpen = true },
                                onOpenRecord = { name -> recordExerciseName = name }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GymBottomBar(
    currentTab: Int,
    onSelect: (Int) -> Unit
) {
    Surface(color = AppBgPink) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(66.dp)
            ) {
                BottomBarItem(
                    label = "今日",
                    iconRes = R.drawable.ic_today,
                    selected = currentTab == 0,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(0.5f)
                        .height(66.dp)
                        .clickable { onSelect(0) }
                )
                BottomBarItem(
                    label = "设置",
                    iconRes = R.drawable.ic_settings,
                    selected = currentTab == 1,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxWidth(0.5f)
                        .height(66.dp)
                        .clickable { onSelect(1) }
                )
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    label: String,
    iconRes: Int,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val tint = if (selected) AppPink else AppIconGray
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(25.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = tint
        )
    }
}
