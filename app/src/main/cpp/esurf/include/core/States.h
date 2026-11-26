/*
 * Minimal stand-in for the reference implementation's core/States.h.
 *
 * The cipher sources only ever touch `g_prog_status[tl_thread_idx].auth_cfg.cipher`
 * (plus tl_thread_idx itself), so the whole supervisor/threading state machine is
 * replaced by these two symbols. Kept in sync by hand — if the upstream cipher code
 * grows a new dependency this header has to grow with it.
 */
#ifndef ESURF_CIPHER_STATES_STUB_H
#define ESURF_CIPHER_STATES_STUB_H

#include "cipher/CipherInterface.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    cipher_interface_t* cipher;
} esurf_auth_cfg_t;

typedef struct {
    esurf_auth_cfg_t auth_cfg;
} esurf_prog_status_t;

/* One slot is enough for the app (single account, single thread), a few extra
 * slots just keep an accidental out-of-range index from corrupting memory. */
extern esurf_prog_status_t g_prog_status[4];
extern int tl_thread_idx;

#ifdef __cplusplus
}
#endif

#endif
