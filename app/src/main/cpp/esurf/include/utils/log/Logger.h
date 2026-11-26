/*
 * Logging is compiled out of the native cipher library: the app has its own
 * switchable log channel, and the cipher layer would only ever emit debug noise
 * that cannot reach it across JNI. All levels collapse to a no-op.
 */
#ifndef ESURF_CIPHER_LOGGER_STUB_H
#define ESURF_CIPHER_LOGGER_STUB_H

#define LOG_TRACE(...) ((void)0)
#define LOG_DEBUG(...) ((void)0)
#define LOG_INFO(...) ((void)0)
#define LOG_WARN(...) ((void)0)
#define LOG_ERROR(...) ((void)0)
#define LOG_FATAL(...) ((void)0)

#endif
