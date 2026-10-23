package com.example.gym

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.gym.data.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryRegressionTest {
    private val repo = GymRepository
    private val prefs = InstrumentationRegistry.getInstrumentation().targetContext
        .getSharedPreferences("repository_regression", Context.MODE_PRIVATE)

    @Before
    fun reset() {
        GymRepository::class.java.getDeclaredField("prefs").apply {
            isAccessible = true
            set(null, prefs)
        }
        prefs.edit().clear().commit()
        reload()
    }

    private fun reload() {
        GymRepository::class.java.getDeclaredMethod("load").apply {
            isAccessible = true
            invoke(repo)
        }
    }

    @Test
    fun legacyUpgradePreservesTemplatesWeightsCompletionAndIds() {
        for (version in 1..3) {
            val exercise = JSONObject().put("id", 90).put("name", "自定义卧推")
                .put("sets", 3).put("detail", "8").put("weight", 42.5)
            val root = JSONObject().put("planVersion", version).put("nextId", 1)
                .put("templates", JSONObject().put("1", JSONArray().put(exercise)))
                .put("completion", JSONObject().put("2026-10-19", JSONObject().put("150", 1)))
                .put("weights", JSONObject().put("自定义卧推", JSONArray().put(
                    JSONObject().put("w", 42.5).put("t", 1000))))
            prefs.edit().putString("data", root.toString()).commit()
            reload()
            assertEquals("自定义卧推", repo.templates[1]!!.single().name)
            assertEquals(90L, repo.templates[1]!!.single().id)
            assertEquals(1, repo.completedSetCount("2026-10-19", 150))
            assertEquals(42.5, repo.historyFor("自定义卧推").single().weightKg, 0.0)
            repo.addExercise(1, "新项目", 2, "10")
            assertTrue(repo.templates[1]!!.last().id > 150)
            reload()
            assertEquals(2, repo.templates[1]!!.size)
        }
    }

    @Test
    fun legacySplit2SelectionMigratesWithoutDiscardingOldEdits() {
        repo.setMode(ScheduleMode.CYCLE)
        repo.addCycleExercise(1, "旧版编辑", 3, "8")
        val root = JSONObject(prefs.getString("data", null)!!)
        root.getJSONObject("cycle").apply {
            remove("family")
            remove("contents")
            put("preset", "SPLIT2_R1")
        }
        prefs.edit().putString("data", root.toString()).commit()
        reload()
        assertEquals("上肢", repo.cycleThemeFor(1))
        assertTrue(repo.cycleTemplates[1]!!.any { it.name == "辅助引体向上" })
        repo.applyCyclePreset(CyclePreset.SPLIT3_R1)
        assertTrue(repo.cycleTemplates[1]!!.any { it.name == "旧版编辑" })
    }

    @Test
    fun splitFamiliesKeepIndependentEditedContentsAcrossReload() {
        repo.setMode(ScheduleMode.CYCLE)
        repo.addCycleExercise(1, "我的推日项目", 3, "8")
        val pushIds = repo.cycleTemplates[1]!!.map { it.id }
        repo.applyCyclePreset(CyclePreset.SPLIT2_R1)
        assertEquals("上肢", repo.cycleThemeFor(1))
        assertTrue(repo.cycleTemplates[1]!!.any { it.name == "辅助引体向上" })
        assertFalse(repo.cycleTemplates[1]!!.any { it.name == "我的推日项目" })
        repo.addCycleExercise(1, "我的上肢项目", 2, "12")
        reload()
        repo.applyCyclePreset(CyclePreset.SPLIT3_R2)
        assertEquals(pushIds, repo.cycleTemplates[1]!!.map { it.id })
        reload()
        repo.applyCyclePreset(CyclePreset.SPLIT2_R2)
        assertTrue(repo.cycleTemplates[1]!!.any { it.name == "我的上肢项目" })
        assertEquals(5, repo.cycleNodes.size)
    }

    @Test
    fun customSlotsRemainAvailableUniqueAndPersisted() {
        repo.setMode(ScheduleMode.CYCLE)
        repo.applyCyclePreset(CyclePreset.CUSTOM)
        val first = repo.addCycleSlot()!!
        val second = repo.addCycleSlot()!!
        assertNotEquals(first, second)
        assertTrue(repo.availableCycleSlots().containsAll(listOf(first, second)))
        repo.addCycleExercise(first, "自定义训练", 2, "10")
        repo.appendCycleTraining(first)
        reload()
        assertTrue(repo.availableCycleSlots().contains(second))
        assertTrue(repo.usedCycleSlots().contains(first))
        assertEquals("自定义训练", repo.cycleTemplates[first]!!.single().name)
        while (repo.addCycleSlot() != null) { /* 上限为 8 个已分配类型 */ }
        assertEquals(8, repo.availableCycleSlots().size)
        assertNull(repo.addCycleSlot())
    }

    @Test
    fun clearedSlotsStayEmptyAcrossPresetsFamiliesAndReload() {
        repo.setMode(ScheduleMode.CYCLE)
        repo.clearCycleSlot(1)
        repo.applyCyclePreset(CyclePreset.SPLIT3_R2)
        assertTrue(repo.cycleTemplates[1]!!.isEmpty())
        repo.applyCyclePreset(CyclePreset.SPLIT2_R1)
        repo.clearCycleSlot(2)
        reload()
        repo.applyCyclePreset(CyclePreset.SPLIT3_R1)
        assertTrue(repo.cycleTemplates[1]!!.isEmpty())
        repo.applyCyclePreset(CyclePreset.SPLIT2_R2)
        assertTrue(repo.cycleTemplates[2]!!.isEmpty())
    }

    @Test
    fun archivedTemplatesReceiveSharedWeightUpdates() {
        repo.setMode(ScheduleMode.CYCLE)
        repo.applyCyclePreset(CyclePreset.SPLIT2_R1)
        repo.recordWeight("史密斯卧推", 67.5)
        reload()
        repo.applyCyclePreset(CyclePreset.SPLIT3_R1)
        assertEquals(67.5, repo.cycleTemplates[1]!!.first { it.name == "史密斯卧推" }.weightKg!!, 0.0)
    }

    @Test
    fun cycleAlignmentCompletionAndWeeklyModeRemainIntact() {
        val weekly = repo.templates
        repo.setMode(ScheduleMode.CYCLE)
        repo.alignTodayToNode(3)
        assertTrue(repo.isRestDay(DateUtils.today()))
        repo.alignTodayToNode(0)
        val item = repo.itemsForDate(DateUtils.today()).first()
        val today = DateUtils.dateKey(DateUtils.today())
        repo.advanceSet(today, item.id, item.sets)
        reload()
        assertEquals(1, repo.completedSetCount(today, item.id))
        repo.retreatSet(today, item.id)
        assertEquals(0, repo.completedSetCount(today, item.id))
        repo.setMode(ScheduleMode.WEEKLY)
        assertEquals(weekly, repo.templates)
    }
}
