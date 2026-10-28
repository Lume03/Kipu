# Kipu release keep rules. Libraries ship their own consumer rules; add only what R8 reports.

# Coil discovers its network fetcher (coil-network-okhttp) through ServiceLoader; R8 merged the
# interface away and dropped META-INF/services, so remote images silently failed to load.
-keep class * implements coil3.util.FetcherServiceLoaderTarget { *; }
-keep interface coil3.util.FetcherServiceLoaderTarget
-keep class * implements coil3.util.DecoderServiceLoaderTarget { *; }
-keep interface coil3.util.DecoderServiceLoaderTarget
