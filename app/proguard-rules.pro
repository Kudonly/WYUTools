# SharedViewKit ships its own keep rules; Compose, Navigation, OkHttp and
# zxing carry consumer rules or are fully referenced. App code uses no
# reflection, so no extra keeps are required yet.

# JNI: the native side looks these members up by name, so R8 must keep them.
-keep class com.wyutools.app.campus.CampusCipher { *; }
