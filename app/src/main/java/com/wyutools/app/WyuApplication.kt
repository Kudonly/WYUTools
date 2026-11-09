package com.wyutools.app

import android.app.Application
import com.wyutools.app.auth.AuthViewModel
import com.wyutools.app.elec.ElecViewModel
import com.wyutools.app.gym.GymViewModel
import com.wyutools.app.lxt.LxtViewModel
import com.wyutools.app.schedule.ScheduleViewModel
import com.wyutools.app.ui.WyuAppearance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WyuApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        // Decode the custom wallpaper before the first activity composes, so the
        // saved background is already there instead of popping in after launch.
        WyuAppearance.ensureLoaded(this)
        appScope.launch { WyuAppearance.loadCustomWallpaper(this@WyuApplication) }
    }

    val authViewModel: AuthViewModel by lazy { AuthViewModel(this) }
    val lxtViewModel: LxtViewModel by lazy { LxtViewModel(this) }
    val elecViewModel: ElecViewModel by lazy { ElecViewModel(this) }
    val gymViewModel: GymViewModel by lazy { GymViewModel(this) }
    val scheduleViewModel: ScheduleViewModel by lazy {
        ScheduleViewModel(this, authViewModel)
    }
}
