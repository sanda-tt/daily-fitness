package com.example.gym

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.gym.data.GymRepository
import org.junit.Rule
import org.junit.Test
import java.io.File

class CycleUiRegressionTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun openCleanSettings() {
        check(compose.activity.packageName.endsWith(".verification")) {
            "UI regression tests require the isolated .verification application ID"
        }
        compose.runOnIdle {
            val prefs = compose.activity.getSharedPreferences("gym_prefs", Context.MODE_PRIVATE)
            prefs.edit().clear().commit()
            GymRepository::class.java.getDeclaredField("prefs").apply {
                isAccessible = true
                set(null, prefs)
            }
            GymRepository::class.java.getDeclaredMethod("load").apply {
                isAccessible = true
                invoke(GymRepository)
            }
        }
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("循环计划").performClick()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val file = File(compose.activity.getExternalFilesDir(null), "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun scrollToText(text: String): SemanticsNodeInteraction {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
        return compose.onNodeWithText(text)
    }

    @Test
    fun switchingSplitAndReturningToTodayShowsUpperBody() {
        openCleanSettings()
        scrollToText("二分化 · 练2休1").performClick()
        compose.onNodeWithText("今天", substring = false).assertDoesNotExist()
        compose.onNodeWithText("今日").performClick()
        compose.onNodeWithText("上肢").assertIsDisplayed()
        compose.onNodeWithText("史密斯卧推").assertIsDisplayed()
        screenshot("split2-today")
    }

    @Test
    fun newCustomTypeCanBeEditedAndAddedToSequence() {
        openCleanSettings()
        scrollToText("自定义编排").performClick()
        scrollToText("新增训练日类型").performClick()
        scrollToText("训练日 D").assertIsDisplayed()
        screenshot("custom-new-type")
        scrollToText("+ 训练日").performClick()
        compose.onNode(hasText("训练日 D") and hasAnyAncestor(isDialog())).performClick()
        compose.runOnIdle { check(GymRepository.usedCycleSlots().contains(4)) }
        screenshot("custom-sequence")
    }

    @Test
    fun clearDayThenChangePresetKeepsItEmpty() {
        openCleanSettings()
        compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("清空训练日 A计划"))
        compose.onNodeWithContentDescription("清空训练日 A计划").performClick()
        compose.onNodeWithText("清空", useUnmergedTree = true).performClick()
        scrollToText("三分化 · 练6休1").performClick()
        compose.onNodeWithText("今日").performClick()
        compose.onNodeWithText("这天还没有安排训练项目\n去「设置」里添加吧").assertIsDisplayed()
        screenshot("cleared-today")
    }
}
