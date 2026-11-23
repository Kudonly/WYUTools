# SharedViewKit ships its own keep rules; Compose, Navigation, OkHttp and
# zxing carry consumer rules or are fully referenced. App code uses no
# reflection, so no extra keeps are required yet.

# Static soft-glass baking reuses miuix internal glass pipeline.
-keep class top.yukonga.miuix.kmp.glass.GlassPipeline { *; }
-keep class top.yukonga.miuix.kmp.glass.WyuGlassPipelineAccess { *; }
-keep class top.yukonga.miuix.kmp.blur.BackdropEffectScopeImpl { *; }
-keep class top.yukonga.miuix.kmp.blur.DrawBackdropNode { *; }
-keep class top.yukonga.miuix.kmp.blur.DrawBackdropNode$effectScope$1 { *; }
