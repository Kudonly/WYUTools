package com.wyutools.app.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SportsBasketball
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector
import com.wyutools.app.SharedViewTransition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** 考试 is its own page; it is not part of the shared-element tool set. */
const val ROUTE_EXAM = "exam"

/** 成绩 / 教评 / 体测 同样是独立页面，只从「更多」打开。 */
const val ROUTE_GRADES = "grades"
const val ROUTE_GPA = "grades/gpa"
const val ROUTE_EVAL = "eval"
const val ROUTE_PHYSICAL = "physical"

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
    Schedule("schedule", "课表", "本周课程 · 今日安排", false),
    Lxt("lxt", "洗澡", "热水余额 · 开阀用水", false),
    Elec("elec", "电费", "用电查询 · 扫码充值", false),
    Gym("gym", "体育馆预约", "场地查询 · 在线预约", false),
    Exam("exam", "考试", "考试安排查询", false),
    Grades("grades", "成绩", "学期成绩 · 单科分析单", false),
    Eval("eval", "教评", "结课评价 · 评价明细", false),
    Physical("physical", "体测", "体测总分 · 各项成绩", false),
    ;

    val icon: ImageVector
        get() = when (this) {
            Schedule -> Icons.Outlined.CalendarMonth
            Lxt -> Icons.Outlined.WaterDrop
            Elec -> Icons.Outlined.Bolt
            Gym -> Icons.Outlined.SportsBasketball
            Exam -> Icons.Outlined.Assignment
            Grades -> Icons.Outlined.School
            Eval -> Icons.Outlined.RateReview
            Physical -> Icons.Outlined.FitnessCenter
        }

    val route: String
        get() = when (this) {
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

    private var prefs: SharedPreferences? = null

    private val _state = MutableStateFlow(WyuToolLayout(defaultCommon(), defaultMore()))
    val state: StateFlow<WyuToolLayout> = _state.asStateFlow()

    private fun defaultCommon(): List<String> = listOf(
        WyuTool.Schedule.id,
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
            WyuToolLayout(
                common = (current.common + tool.id).sortedBy { id -> WyuTool.fromId(id)?.ordinal ?: 99 },
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
        put("c", JSONArray(layout.common))
        put("m", JSONArray(layout.more))
    }.toString()

    private fun decode(raw: String?): WyuToolLayout {
        if (raw.isNullOrBlank()) return WyuToolLayout(defaultCommon(), defaultMore())
        val json = runCatching { JSONObject(raw) }.getOrNull()
            ?: return WyuToolLayout(defaultCommon(), defaultMore())
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
        return WyuToolLayout(common = cleanCommon, more = cleanMore + missing)
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
