package com.wyutools.app.ui

import top.yukonga.miuix.kmp.blur.BackdropEffectScope

/**
 * Miuix 0.9.4 derives the backdrop downscale exponent from the blur radius and offers no
 * public override. This helper drives the same internal level selection the library uses
 * for its own LOD passes, so the per-level variance compensation stays intact.
 */
@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
internal fun BackdropEffectScope.forceDownscaleExp(exp: Int) {
    (this as top.yukonga.miuix.kmp.blur.BackdropEffectScopeImpl).forcedDownscaleExp = exp
}
