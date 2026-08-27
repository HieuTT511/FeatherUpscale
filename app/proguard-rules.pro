# FeatherUpscale — R8 / Proguard Rules

# 1. Native & JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class com.feather.upscale.NcnnUpscaler { *; }
-keep class com.feather.upscale.TileProcessor** { *; }

# 2. WorkManager Workers
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.feather.upscale.worker.UpscaleWorker { *; }
-keep class com.feather.upscale.worker.UpscaleState** { *; }

# 3. Kotlin Coroutines & Flow
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# 4. Compose runtime
-keep class androidx.compose.runtime.** { *; }
