package com.wyutools.app.ui

import top.yukonga.miuix.kmp.blur.BackdropEffectScope

/**
 * Miuix 0.9.5 exposes the backdrop downscale factor directly. [exp] is the
 * base-2 exponent the call sites were written against (1 -> 2x).
 */
internal fun BackdropEffectScope.forceDownscaleExp(exp: Int) {
    downscaleFactor = (1 shl exp.coerceIn(0, 4)).toFloat()
}
