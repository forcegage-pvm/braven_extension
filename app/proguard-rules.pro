# Braven Karoo Dashboard ProGuard Rules

# Keep NanoHTTPD
-keep class fi.iki.elonen.** { *; }
-keep class org.nanohttpd.** { *; }

# Keep Karoo SDK
-keep class io.hammerhead.karooext.** { *; }
