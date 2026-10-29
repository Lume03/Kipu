# Kipu release keep rules. Libraries ship their own consumer rules; add only what R8 reports.

# Coil discovers its network fetcher (coil-network-okhttp) through ServiceLoader; R8 merged the
# interface away and dropped META-INF/services, so remote images silently failed to load.
-keep class * implements coil3.util.FetcherServiceLoaderTarget { *; }
-keep interface coil3.util.FetcherServiceLoaderTarget
-keep class * implements coil3.util.DecoderServiceLoaderTarget { *; }
-keep interface coil3.util.DecoderServiceLoaderTarget

# ML Kit text recognition reads its bundled model/config objects reflectively (protobuf-lite style).
# R8 optimized those fields away and OCR failed with an NPE inside the recognizer, so every Yape
# receipt was reported as unreadable in release only.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_common.** { *; }
