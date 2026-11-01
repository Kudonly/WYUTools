package com.wyutools.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.itgsa.animation.sharedview.ViewAnimationConfig
import com.itgsa.animation.sharedview.ViewAnimationExtras
import com.itgsa.animation.sharedview.ViewAnimationListener
import com.itgsa.animation.sharedview.ViewAnimationManager
import java.util.Locale

data class SharedCardSnapshot(
    val bitmap: Bitmap,
    val bounds: Rect,
    val radiusPx: Float,
    val title: String? = null,
    val titleBounds: Rect? = null,
    val titleFontSizePx: Float = 0f,
    val titleLineHeightPx: Float = 0f,
)

object SharedViewTransition {
    private const val TAG = "SharedViewTransition"

    const val EXTRA_START_ROUTE = "com.wyutools.app.extra.START_ROUTE"
    const val ROUTE_SCHEDULE = "schedule"
    const val ROUTE_LXT = "lxt"
    const val ROUTE_ELEC = "elec"
    const val ROUTE_GYM = "gym"
    const val ROUTE_SETTINGS = "settings"

    val featureRoutes =
        setOf(ROUTE_SCHEDULE, ROUTE_LXT, ROUTE_ELEC, ROUTE_GYM, ROUTE_SETTINGS)
    private val protocolVersion: Int by lazy {
        runCatching { ViewAnimationManager.isViewAnimationSupported() }.getOrDefault(0)
    }
    private val extras: Map<String, String> by lazy {
        if (!isSupported()) {
            emptyMap()
        } else {
            runCatching { ViewAnimationManager.getViewAnimationExtras() }.getOrDefault(emptyMap())
        }
    }

    val boundsUseWindowCoordinate: Boolean by lazy {
        val meta = extras[ViewAnimationExtras.EXTRA_BOUNDS]
        if (meta.isNullOrBlank()) {
            false
        } else {
            val token = meta.split('|').getOrNull(1)?.trim()?.uppercase(Locale.ROOT)
            when (token) {
                "WINDOW" -> true
                "SCREEN" -> false
                else -> meta.uppercase(Locale.ROOT).contains("WINDOW")
            }
        }
    }

    fun isSupported(): Boolean = protocolVersion > 0

    fun featureRouteOrNull(route: String?): String? = route?.takeIf(featureRoutes::contains)

    fun launchTool(
        activity: Activity?,
        route: String,
        card: SharedCardSnapshot?,
    ): Boolean {
        if (activity == null || card == null || !isSupported()) return false
        if (route !in featureRoutes || card.bounds.isEmpty || card.bitmap.isRecycled) return false

        val intent = Intent(activity, MainActivity::class.java)
            .putExtra(EXTRA_START_ROUTE, route)
        val config = ViewAnimationConfig().setRadius(card.radiusPx)
        val listener = object : ViewAnimationListener {
            override fun onAnimationStart(animMode: Int) {
                Log.i(TAG, "start mode=$animMode route=$route")
            }

            override fun onAnimationCancel(animMode: Int) {
                Log.i(TAG, "cancel mode=$animMode route=$route")
            }

            override fun onAnimationEnd(animMode: Int) {
                Log.i(TAG, "end mode=$animMode route=$route")
            }
        }
        val options = runCatching {
            ViewAnimationManager.makeViewAnimationOptions(
                card.bitmap,
                card.bounds,
                card.radiusPx,
                config.toBundle(),
                listener,
            )
        }.getOrNull() ?: return false

        return runCatching {
            activity.startActivity(intent, options.toBundle())
            Log.i(TAG, "launch route=$route bounds=${card.bounds}")
            true
        }.getOrElse { error ->
            Log.w(TAG, "launch failed", error)
            false
        }
    }
}
