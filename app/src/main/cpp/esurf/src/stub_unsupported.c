/*
 * Placeholders for upstream cipher factories that are deliberately not compiled.
 *
 * desede_ecb.c is the only algorithm backed by the OpenSSL-style SimEvp shim, and
 * the Algo-ID it serves belongs to the legacy Android channel this app does not
 * target. Returning NULL makes cipher_supported() report it as unavailable, which
 * the Kotlin side surfaces as "unsupported Algo-ID" instead of crashing, and it
 * keeps the factory in the reference source linkable without editing it.
 */
#include <stddef.h>
#include <stdint.h>

#include "cipher/CipherInterface.h"

cipher_interface_t* create_desede_ecb_cipher(const uint8_t* key1, const uint8_t* key2)
{
    (void)key1;
    (void)key2;
    return NULL;
}
