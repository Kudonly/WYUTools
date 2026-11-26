/*
 * JNI surface for the eSurfing portal cipher suite.
 *
 * The cipher implementations (custom AES/3DES/TEA/SM4/SNOW3G variants, plus the
 * per-Algo-ID key table) are taken verbatim from the Apache-2.0 reference client
 * and compiled as-is; only the surrounding program state is replaced by the
 * stubs in include/core and include/utils. Nothing here reimplements crypto.
 *
 * Contract, mirrored by com.wyutools.app.campus.CampusCipher:
 *   nativeSupported(id) -> whether this Algo-ID has an implementation
 *   nativeInit(id)      -> install the cipher for that Algo-ID (single slot)
 *   nativeEncrypt(text) -> uppercase hex string, or null on failure
 *   nativeDecrypt(hex)  -> plaintext, or null on failure
 *
 * The app drives one account from one thread, so a single cipher slot and a
 * fixed tl_thread_idx are enough; both are still declared as the upstream code
 * expects them rather than patched into the cipher sources.
 */
#include <jni.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "cipher/CipherInterface.h"
#include "cipher/CipherUtils.h"
#include "core/States.h"

esurf_prog_status_t g_prog_status[4];
int tl_thread_idx = 0;

static cipher_interface_t* current_cipher(void)
{
    return g_prog_status[tl_thread_idx].auth_cfg.cipher;
}

JNIEXPORT jboolean JNICALL
Java_com_wyutools_app_campus_CampusCipher_nativeSupported(
    JNIEnv* env, jobject thiz, jstring algo_id)
{
    (void)thiz;
    if (algo_id == NULL) return JNI_FALSE;
    const char* id = (*env)->GetStringUTFChars(env, algo_id, NULL);
    if (id == NULL) return JNI_FALSE;
    const jboolean ok = cipher_supported(id) ? JNI_TRUE : JNI_FALSE;
    (*env)->ReleaseStringUTFChars(env, algo_id, id);
    return ok;
}

JNIEXPORT jboolean JNICALL
Java_com_wyutools_app_campus_CampusCipher_nativeInit(
    JNIEnv* env, jobject thiz, jstring algo_id)
{
    (void)thiz;
    if (algo_id == NULL) return JNI_FALSE;
    const char* id = (*env)->GetStringUTFChars(env, algo_id, NULL);
    if (id == NULL) return JNI_FALSE;

    destroy_cipher_factory();
    const jboolean ok = init_cipher(id) ? JNI_TRUE : JNI_FALSE;
    (*env)->ReleaseStringUTFChars(env, algo_id, id);
    return ok;
}

JNIEXPORT jstring JNICALL
Java_com_wyutools_app_campus_CampusCipher_nativeEncrypt(
    JNIEnv* env, jobject thiz, jstring text)
{
    (void)thiz;
    if (text == NULL || current_cipher() == NULL) return NULL;
    const char* plain = (*env)->GetStringUTFChars(env, text, NULL);
    if (plain == NULL) return NULL;

    char* hex = session_encrypt(plain);
    (*env)->ReleaseStringUTFChars(env, text, plain);
    if (hex == NULL) return NULL;

    jstring result = (*env)->NewStringUTF(env, hex);
    free(hex);
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_wyutools_app_campus_CampusCipher_nativeDecrypt(
    JNIEnv* env, jobject thiz, jstring hex)
{
    (void)thiz;
    if (hex == NULL || current_cipher() == NULL) return NULL;
    const char* encoded = (*env)->GetStringUTFChars(env, hex, NULL);
    if (encoded == NULL) return NULL;

    char* plain = session_decrypt(encoded);
    (*env)->ReleaseStringUTFChars(env, hex, encoded);
    if (plain == NULL) return NULL;

    jstring result = (*env)->NewStringUTF(env, plain);
    free(plain);
    return result;
}

JNIEXPORT void JNICALL
Java_com_wyutools_app_campus_CampusCipher_nativeRelease(
    JNIEnv* env, jobject thiz)
{
    (void)env;
    (void)thiz;
    destroy_cipher_factory();
}
