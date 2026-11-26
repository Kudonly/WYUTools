# 校园网密码层（native）

这里的 C 源码来自开源项目 **ESurfingClient-CVersion**
（https://github.com/BadGhost520/ESurfingClient-CVersion ，Apache-2.0），
原样保留，只做了两处改动性的**外围**处理，密码算法本身一行未改：

- `include/core/States.h`、`include/utils/log/Logger.h`：原项目的进程状态机与
  日志系统的极简替身，只提供密码层真正引用的 `g_prog_status[].auth_cfg.cipher`
  与 `tl_thread_idx`，日志宏全部编译为空。
- `src/jni_bridge.c`：JNI 入口，定义上面两个符号，暴露
  `supported / init / encrypt / decrypt / release` 五个方法给
  `com.wyutools.app.campus.CampusCipher`。
- `src/stub_unsupported.c`：原项目里唯一依赖 OpenSSL EVP 的 3DES-ECB（仅旧版
  Android 渠道）不参与编译，这里给一个返回 NULL 的替身，使工厂代码仍可链接。

`KeyData.c` 是各 Algo-ID 对应的 key/iv 常量表；`CipherFactory.c` 把 Algo-ID 映射到
具体工厂。本次只编译当前 Android 渠道会下发的算法族。

## 为什么用 native 而不是 Kotlin 重写

门户每次会话随机下发一个 Algo-ID，对应的是厂商自定义变体（转置 key schedule 的
AES、改过的 SM4/SNOW3G、三层 modified-TEA 等），用标准 JCE/BC 无法复现。重写一遍
等于重造四千多行密码代码，任何一位差错都表现为「认证失败」而无法定位；直接用原
实现编译，正确性由上游保证，Kotlin 侧只负责协议与界面。

## 预编译产物

`app/src/main/jniLibs/{arm64-v8a,armeabi-v7a}/libesurf_cipher.so` 由 NDK r27
（`aarch64-linux-android26-clang` / `armv7a-linux-androideabi26-clang`）编译，
CI 与本地构建都不需要 NDK。重新编译的方法：

```bash
clang -shared -fPIC -O2 -w \
  -Iinclude -Isrc \
  -o ../jniLibs/arm64-v8a/libesurf_cipher.so \
  src/cipher/CipherFactory.c src/cipher/CipherUtils.c src/cipher/KeyData.c \
  $(ls src/cipher/algo/*.c | grep -v desede_ecb) \
  src/jni_bridge.c src/stub_unsupported.c
```

## 许可

Apache License 2.0，见上游仓库。分发本仓库的 APK 时该声明随之保留。
