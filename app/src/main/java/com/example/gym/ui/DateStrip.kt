package com.example.gym.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gym.data.DateUtils
import com.example.gym.ui.theme.AppCardWhite
import com.example.gym.ui.theme.AppPink
import com.example.gym.ui.theme.AppTextDark
import com.example.gym.ui.theme.AppTextGray
import com.example.gym.ui.theme.AppTodayRing
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val TOTAL_ITEMS = 730
private const val CENTER_INDEX = TOTAL_ITEMS / 2

private fun indexForOffset(offset: Int) = CENTER_INDEX + offset
private fun offsetForIndex(index: Int) = index - CENTER_INDEX

/**
 * 顶部横向日期条：选中日期始终居中，可点击两侧日期，也可左右滑动后自动吸附。
 */
@Composable
fun DateStrip(
    selectedOffset: Int,
    onSelectOffset: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val itemWidth = configuration.screenWidthDp / 7f
    val density = LocalDensity.current
    val itemWidthPx = with(density) { itemWidth.dp.toPx() }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = indexForOffset(selectedOffset) - 3
    )
    val scope = rememberCoroutineScope()

    // 选中项变化时滚动到正中间
    LaunchedEffect(selectedOffset) {
        listState.animateScrollToItem(indexForOffset(selectedOffset) - 3)
    }

    // 用户滑动停止后，取正中间的日期为选中项
    LaunchedEffect(Unit) {
        var wasScrolling = false
        snapshotFlow { listState.isScrollInProgress }.collectLatest { scrolling ->
            if (wasScrolling && !scrolling) {
                val firstVisible = listState.firstVisibleItemIndex
                val fraction = listState.firstVisibleItemScrollOffset / itemWidthPx
                val centeredItem = (firstVisible + fraction + 3f).roundToInt()
                    .coerceIn(0, TOTAL_ITEMS - 1)
                onSelectOffset(offsetForIndex(centeredItem))
            }
            wasScrolling = scrolling
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .height(78.dp)
    ) {
        items(TOTAL_ITEMS) { index ->
            val offset = offsetForIndex(index)
            val calendar = DateUtils.dateForOffset(offset)
            DateCell(
                weekday = DateUtils.weekdayChar(calendar).toString(),
                dayOfMonth = DateUtils.dayOfMonth(calendar),
                selected = offset == selectedOffset,
                isToday = offset == 0,
                width = itemWidth.dp,
                onClick = {
                    scope.launch { onSelectOffset(offset) }
                }
            )
        }
    }
}

@Composable
private fun DateCell(
    weekday: String,
    dayOfMonth: Int,
    selected: Boolean,
    isToday: Boolean,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(width)
            .height(78.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(10.dp))
        Text(
            text = weekday,
            fontSize = 14.sp,
            color = if (selected) AppPink else AppTextGray
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .height(40.dp)
                .width(40.dp)
                .then(
                    when {
                        selected -> Modifier
                            .clip(CircleShape)
                            .background(AppPink)
                        isToday -> Modifier
                            .clip(CircleShape)
                            .background(AppCardWhite)
                            .border(1.5.dp, AppTodayRing, CircleShape)
                        else -> Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = dayOfMonth.toString(),
                fontSize = 17.sp,
                fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    selected -> AppCardWhite
                    isToday -> AppPink
                    else -> AppTextDark
                }
            )
        }
    }
}
