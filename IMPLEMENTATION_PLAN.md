# FeatherUpscale — Kế hoạch triển khai

App Android upscale ảnh truyện tranh bằng **NCNN (Real-ESRGAN)** với **Vulkan backend**.

## Kiến trúc tổng quan

```
┌─────────────────────────────┐
│ UI (Jetpack Compose)        │  Tuần 2: chọn ảnh, preview slider, tiến độ
├─────────────────────────────┤
│ TileProcessor.kt            │  Chia tiles 256px / overlap 16px, feather blend
│   ├ OOM guard (low-ram)     │  tile 128 nếu isLowRamDevice, retry tile nhỏ hơn
│   └ stitchTiles()           │  pure-Kotlin, unit-testable
├─────────────────────────────┤
│ NcnnUpscaler.kt             │  JNI wrapper, load model từ assets → filesDir
├─────────────────────────────┤
│ native: upscale_jni.cpp     │  NCNN + Vulkan GPU pipeline (CMake)
│ libfeatherup.so             │  link -fopenmp -lvulkan, STL c++_shared
└─────────────────────────────┘
```

### Thành phần chính

- `app/src/main/cpp/CMakeLists.txt` — build JNI, tự phát hiện ncnn prebuilt tại
  `cpp/ncnn/<abi>/` (nếu không có → stub build để vẫn compile được khi dev).
- `upscale_jni.cpp` — `nativeUpscaleTile(pixels: ByteArray, w, h, scale): ByteArray`,
  placeholder nearest-scale; phần FEATHER_HAS_NCNN sẽ activate khi đặt model thật.
- `NcnnUpscaler.kt` — `System.loadLibrary("featherup")`; copy
  `models/realesrgan-x4.param/.bin` từ assets sang filesDir lần chạy đầu.
- `TileProcessor.kt` — chia tile, upscale từng phần, ghép bằng linear feather blend.

### Yêu cầu cài bổ sung để build native

1. **NDK**: `sdkmanager "ndk;26.3.11579264"` (SDK đã có tại `C:/Android/sdk`).
2. **CMake**: `sdkmanager "cmake;3.22.1"`.
3. **NCNN prebuilt**: tải từ https://github.com/Tencent/ncnn/releases
   (`ncnn-<ver>-android-vulkan.zip`) → giải nén vào
   `app/src/main/cpp/ncnn/<abi>/` với cấu trúc `<abi>/include`, `<abi>/lib`
   cho các ABI: `arm64-v8a`, `x86_64`.
4. **Model Real-ESRGAN NCNN** (`realesrgan-x4.param/.bin`) vào
   `app/src/main/assets/models/`.

## Roadmap 3 tuần

### Tuần 1 (hiện tại) — Nền tảng ✅
- [x] Gradle Kotlin DSL (AGP 8.5.2, Kotlin 2.0.20, Compose BOM), minSdk 26 / targetSdk 35.
- [x] Manifest + MainActivity Compose ("Hello FeatherUpscale").
- [x] CMake module `libfeatherup.so` + JNI skeleton + hướng dẫn ncnn prebuilt.
- [x] `NcnnUpscaler.kt` (JNI + copy model assets→filesDir).
- [x] `TileProcessor.kt`: tiles 256px, overlap 16px, linear feather blend,
      OOM guard low-ram (tile 128, cache ≤1 tile, check mem trước mỗi tile,
      catch OutOfMemoryError).
- [x] Unit test `TileProcessorTest` (stitch/feather mép pixel).

### Tuần 2 — UI & Background processing
- [ ] Màn hình Compose: pick ảnh (Photo Picker), chọn scale 2x/4x.
- [ ] Preview before/after slider (horizontal drag comparator).
- [ ] WorkManager worker chạy upscale nền: pause/resume/cancel, progress theo tile.
- [ ] Toast/snackbar lỗi OOM; retry tự động hạ tile.
- [ ] Unit test WorkManager (TestListenableWorkerBuilder).

### Tuần 3 — Batch & polish
- [ ] Batch ZIP/CBZ: chọn nhiều trang, xuất ZIP nén sau upscale.
- [ ] Foreground notification + progress bar + hành động pause/resume.
- [ ] FP16 (ncnn opt.use_fp16_packed) toggle tiết kiệm VRAM.
- [ ] Haptic feedback khi hoàn tất trang; blur placeholder trong preview.
- [ ] Test 5 device thật (máy 4GB + máy flagship, Mali + Adreno).
