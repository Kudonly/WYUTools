package com.wyutools.app.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SportsBasketball
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector
import com.wyutools.app.SharedViewTransition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** 今日：从课表拆出来的单日页，以后会挂更多「今天」相关的东西。 */
const val ROUTE_TODAY = "today"

/** 考试 is its own page; it is not part of the shared-element tool set. */
const val ROUTE_EXAM = "exam"

/** 成绩 / 教评 / 体测 同样是独立页面，只从「更多」打开。 */
const val ROUTE_GRADES = "grades"
const val ROUTE_GPA = "grades/gpa"
const val ROUTE_EVAL = "eval"
const val ROUTE_PHYSICAL = "physical"

/** 体测二级页面（某一学年的各项成绩），不是工具，不参与卡片形变。 */
const val ROUTE_PHYSICAL_DETAIL = "physical/detail"

/** 常用卡片 picker. Not a tool, but it opens with the same morph as a tool. */
const val ROUTE_COMMON = "settings/common"

/**
 * Every tool the app has. A tool lives either on the 常用 page as a card or on
 * the 更多 page as an icon — never both, never neither.
 */
enum class WyuTool(
    val id: String,
    val title: String,
    val summary: String,
    /**
     * Core entries the 常用 page refuses to drop. Currently every card can be
     * removed: the 更多 page always keeps an icon, so nothing is ever lost.
     */
    val lockedInCommon: Boolean,
) {
    Today("today", "今日", "今日待办 · 今天的课", false),
    Schedule("schedule", "课表", "本周课程 · 今日安排", false),
    Lxt("lxt", "洗澡", "热水余额 · 开阀/关阀", false),
    Elec("elec", "电费", "用电查询 · 扫码充值", false),
    Gym("gym", "体育馆预约", "场地查询 · 在线预约", false),
    Exam("exam", "考试", "考试安排查询", false),
    Grades("grades", "成绩", "学期成绩 · 单科分析单", false),
    Eval("eval", "教评", "结课评价 · 评价明细", false),
    Physical("physical", "体测", "体测总分 · 各项成绩", false),
    ;

    val icon: ImageVector
        get() = when (this) {
            Today -> Icons.Outlined.Today
            Schedule -> Icons.Outlined.CalendarMonth
            Lxt -> Icons.Outlined.WaterDrop
            Elec -> Icons.Outlined.Bolt
            Gym -> Icons.Outlined.SportsBasketball
            Exam -> Icons.AutoMirrored.Outlined.Assignment
            Grades -> Icons.Outlined.School
            Eval -> Icons.Outlined.RateReview
            Physical -> Icons.Outlined.FitnessCenter
        }

    val route: String
        get() = when (this) {
            Today -> ROUTE_TODAY
            Schedule -> SharedViewTransition.ROUTE_SCHEDULE
            Lxt -> SharedViewTransition.ROUTE_LXT
            Elec -> SharedViewTransition.ROUTE_ELEC
            Gym -> SharedViewTransition.ROUTE_GYM
            Exam -> ROUTE_EXAM
            Grades -> ROUTE_GRADES
            Eval -> ROUTE_EVAL
            Physical -> ROUTE_PHYSICAL
        }

    companion object {
        fun fromId(id: String?): WyuTool? = entries.firstOrNull { it.id == id }
    }
}

data class WyuToolLayout(
    /** Tool ids shown as cards on 常用, in display order. */
    val common: List<String>,
    /** Tool ids shown as icons on 更多, in the order the user dragged them. */
    val more: List<String>,
) {
    val commonTools: List<WyuTool> get() = common.mapNotNull(WyuTool::fromId)
    val moreTools: List<WyuTool> get() = more.mapNotNull(WyuTool::fromId)
    fun isCommon(tool: WyuTool): Boolean = tool.id in common
}

/**
 * Who goes where, persisted as one small JSON blob. The invariant — every tool
 * appears exactly once — is restored on load, so a stale or hand-edited file
 * can never make a tool vanish.
 */
object WyuToolLayoutStore {
    private const val PREFS = "tool_layout"
    private const val KEY = "layout"
    /**
     * Bumped when a new tool must be placed instead of merely appended, or when
     * an existing order has to change. 3: 课表 moves to the top of 常用.
     */
    private const val VERSION = 3

    private var prefs: SharedPreferences? = null

    private val _state = MutableStateFlow(WyuToolLayout(defaultCommon(), defaultMore()))
    val state: StateFlow<WyuToolLayout> = _state.asStateFlow()

    /** 课表 is the page this app is opened for, so it leads the 常用 stack. */
    private fun defaultCommon(): List<String> = listOf(
        WyuTool.Schedule.id,
        WyuTool.Today.id,
        WyuTool.Lxt.id,
        WyuTool.Elec.id,
        WyuTool.Gym.id,
    )

    private fun defaultMore(): List<String> = listOf(
        WyuTool.Exam.id,
        WyuTool.Grades.id,
        WyuTool.Eval.id,
        WyuTool.Physical.id,
    )

    fun ensureLoaded(context: Context) {
        if (prefs != null) return
        val store = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        _state.value = decode(store.getString(KEY, null))
    }

    fun setInCommon(tool: WyuTool, inCommon: Boolean) {
        val current = _state.value
        if (current.isCommon(tool) == inCommon) return
        if (!inCommon && tool.lockedInCommon) return
        val next = if (inCommon) {
            // Appended, not sorted by enum order: the list is user-ordered now,
            // and re-sorting would throw that order away on every toggle.
            WyuToolLayout(
                common = current.common + tool.id,
                more = current.more - tool.id,
            )
        } else {
            WyuToolLayout(
                common = current.common - tool.id,
                more = current.more + tool.id,
            )
        }
        apply(next)
    }

    /**
     * Puts [tool] at [index] of 常用 ([toCommon]) or 更多, taking it out of the
     * other list first. The index is the slot the finger was over, so a move
     * inside one list means "remove, then insert at that slot" and a move across
     * the two lists means "append or insert there".
     */
    fun moveTool(tool: WyuTool, toCommon: Boolean, index: Int) {
        val current = _state.value
        val wasCommon = tool.id in current.common
        if (toCommon == wasCommon) {
            val list = if (toCommon) current.common else current.more
            val from = list.indexOf(tool.id)
            if (from < 0) return
            val to = index.coerceIn(0, list.lastIndex)
            if (from == to) return
            val next = list.toMutableList().apply { add(to, removeAt(from)) }
            apply(if (toCommon) current.copy(common = next) else current.copy(more = next))
            return
        }
        val common = if (toCommon) {
            current.common.toMutableList().apply { add(index.coerceIn(0, size), tool.id) }
        } else {
            current.common - tool.id
        }
        val more = if (toCommon) {
            current.more - tool.id
        } else {
            current.more.toMutableList().apply { add(index.coerceIn(0, size), tool.id) }
        }
        apply(WyuToolLayout(common = common, more = more))
    }

    /** Moves the icon at [from] to [to]; the rest close the gap (iOS reflow). */
    fun moveInMore(from: Int, to: Int) {
        val current = _state.value
        if (from == to) return
        if (from !in current.more.indices || to !in current.more.indices) return
        val next = current.more.toMutableList().apply { add(to, removeAt(from)) }
        apply(current.copy(more = next))
    }

    fun resetToDefault() {
        apply(WyuToolLayout(defaultCommon(), defaultMore()))
    }

    private fun apply(layout: WyuToolLayout) {
        _state.value = layout
        prefs?.edit()?.putString(KEY, encode(layout))?.apply()
    }

    private fun encode(layout: WyuToolLayout): String = JSONObject().apply {
        put("v", VERSION)
        put("c", JSONArray(layout.common))
        put("m", JSONArray(layout.more))
    }.toString()

    private fun decode(raw: String?): WyuToolLayout {
        if (raw.isNullOrBlank()) return WyuToolLayout(defaultCommon(), defaultMore())
        val json = runCatching { JSONObject(raw) }.getOrNull()
            ?: return WyuToolLayout(defaultCommon(), defaultMore())
        // A layout written before 今日 existed has no version field: that is
        // the one case where a tool the file never knew about is worth putting
        // on 常用 instead of dropping it into 更多.
        val legacy = !json.has("v")
        val version = json.optInt("v", 0)
        val common = json.optJSONArray("c").toStringList()
        val more = json.optJSONArray("m").toStringList()
        val seen = mutableSetOf<String>()
        val cleanCommon = common
            .mapNotNull(WyuTool::fromId)
            .filter { seen.add(it.id) }
            .map { it.id }
        val cleanMore = more
            .mapNotNull(WyuTool::fromId)
            .filter { seen.add(it.id) }
            .map { it.id }
        // Anything the file forgot (new tool, corrupted entry) lands in 更多.
        val missing = WyuTool.entries
            .filter { it.id !in seen }
            .map { it.id }
        val promoteToday = legacy && WyuTool.Today.id in missing
        val promotedCommon = if (promoteToday) {
            listOf(WyuTool.Today.id) + cleanCommon
        } else {
            cleanCommon
        }
        return WyuToolLayout(
            common = if (version < 3) withScheduleFirst(promotedCommon) else promotedCommon,
            more = cleanMore + missing.filterNot { promoteToday && it == WyuTool.Today.id },
        )
    }

    /** One-time migration: 课表 used to sit behind 今日. */
    private fun withScheduleFirst(common: List<String>): List<String> {
        val id = WyuTool.Schedule.id
        if (id !in common || common.firstOrNull() == id) return common
        return listOf(id) + common.filterNot { it == id }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                optString(i, "").takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }
}
