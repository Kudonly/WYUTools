package com.wyutools.app

import android.app.Application
import com.wyutools.app.auth.AuthViewModel
import com.wyutools.app.elec.ElecViewModel
import com.wyutools.app.gym.GymViewModel
import com.wyutools.app.lxt.LxtViewModel

class WyuApplication : Application() {
    val authViewModel: AuthViewModel by lazy { AuthViewModel(this) }
    val lxtViewModel: LxtViewModel by lazy { LxtViewModel(this) }
    val elecViewModel: ElecViewModel by lazy { ElecViewModel(this) }
    val gymViewModel: GymViewModel by lazy { GymViewModel(this) }
}
