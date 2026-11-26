package com.wyutools.app.campus

/**
 * JNI bridge to the compiled portal cipher suite.
 *
 * The portal hands out a random algorithm id per session and expects every packet
 * to be encrypted with that specific (non-standard) suite, so the implementations
 * live in C next to the per-id key table rather than being re-derived in Kotlin.
 * Only the Algo-IDs of the current Android channel are compiled in; anything else
 * reports [supported] == false and the caller must surface it instead of guessing.
 *
 * All calls are serialized: the native side keeps a single active cipher slot.
 */
object CampusCipher {
    private val loaded: Boolean = runCatching { System.loadLibrary("esurf_cipher") }.isSuccess

    /** False on ABIs without a packaged .so (or a broken load); UI must degrade. */
    val available: Boolean get() = loaded

    private external fun nativeSupported(algoId: String): Boolean
    private external fun nativeInit(algoId: String): Boolean
    private external fun nativeEncrypt(text: String): String?
    private external fun nativeDecrypt(hex: String): String?
    private external fun nativeRelease()

    @Synchronized
    fun supported(algoId: String): Boolean =
        loaded && algoId.isNotBlank() && runCatching { nativeSupported(algoId) }.getOrDefault(false)

    @Synchronized
    fun install(algoId: String): Boolean =
        loaded && algoId.isNotBlank() && runCatching { nativeInit(algoId) }.getOrDefault(false)

    @Synchronized
    fun encrypt(plain: String): String? =
        if (loaded) runCatching { nativeEncrypt(plain) }.getOrNull() else null

    @Synchronized
    fun decrypt(hex: String): String? =
        if (loaded) runCatching { nativeDecrypt(hex) }.getOrNull() else null

    @Synchronized
    fun release() {
        if (loaded) runCatching { nativeRelease() }
    }
}
