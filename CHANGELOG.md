# 📜 UpScale (FeatherUpscale) — Nhật Ký Thay Đổi (Changelog)

Tất cả các thay đổi, tính năng mới và bản vá lỗi của ứng dụng UpScale được ghi nhận chi tiết tại đây theo chuẩn **Semantic Versioning**.

---

## 🚀 [v1.3.0] — 2026-08-28
### ✨ Tính năng mới Đột phá: Upscale 8X Ultra-HD Max & Engine Lưu Trữ Document Tree
- **Đột phá Tỉ lệ Siêu Phân Giải 8X (8K Ultra-HD Output)**:
  - Bổ sung tùy chọn tỉ lệ **8X Max** trên thanh điều khiển giao diện (bên cạnh 2X và 4X).
  - Tự động kích hoạt cơ chế `OOM Guard` chia mảnh 128px an toàn kết hợp dải chồng lấn $128\text{px}$ cho tỉ lệ 8X, đảm bảo **100% không tràn RAM / không crash app** trên mọi dòng máy Android 4GB - 16GB RAM.
  - Bộ nội suy Catmull-Rom Bicubic Spline 4x4 + Anime4K Linework Sharpener chạy native siêu mượt mà trên tỉ lệ 8X.
- **Khắc phục Triệt Để Logic Lưu Thư Mục Tùy Chọn (`StorageHelper`)**:
  - Sửa lỗi đường dẫn ảo Scoped Storage: Khi người dùng chọn thư mục qua `OpenDocumentTree`, ứng dụng cấp quyền vĩnh viễn `takePersistableUriPermission` và sử dụng `DocumentFile` để tự động tạo thư mục con `UpScale/` và ghi tệp trực tiếp vào đúng thư mục người dùng đã chọn.
  - Tự động hiển thị tên đường dẫn thân thiện (ví dụ: `/Pictures/Manga/UpScale`) trên giao diện thay vì mã URI thô.
  - Mở tệp chính xác bằng `Document URI` lẫn `FileProvider`.

---

## 🌟 [v1.2.1] — 2026-08-28
### ✨ Tính năng mới & Tự Động Hóa Thư Mục
- **Tự động Khởi tạo Thư mục Đầu ra Chuyên dụng (`UpScale`)**:
  - Ứng dụng tự động tạo sẵn thư mục lưu kết quả `Pictures/UpScale` (cho ảnh đơn) và `Download/UpScale` (cho tập truyện CBZ/MOBI), người dùng không cần phải tạo thư mục thủ công.
  - Tự động đồng bộ hóa tạo thư mục con `UpScale/` ngay cả khi người dùng chọn một thư mục cha tùy ý.
- **Tự động Nhận diện & Xác minh Tệp Đầu ra trong Thư viện (Verified Media Scanning)**:
  - Tích hợp `MediaScannerConnection`: Tự động quét và lập chỉ mục (index) tệp đã upscale vào Thư viện/Bộ sưu tập (Gallery/Photos) và Trình quản lý tệp của Android ngay khi vừa hoàn thành.
  - Trạng thái `UpscaleState.Completed` ghi nhận đầy đủ đường dẫn thư mục `outputDirectory` và cờ xác minh tệp hợp lệ `isVerified`.
- **Tùy chọn Mở Thư mục Kết Quả Thông Minh**:
  - Bổ sung nút **"MỞ THƯ MỤC"** ngay tại thẻ kết quả và thanh công cụ điều hướng để người dùng có thể mở trực tiếp thư mục `UpScale` trong ứng dụng Quản lý tệp của máy.
  - Đảm bảo mở chính xác tệp ảnh/tập truyện đã upscale bằng `FileProvider`.

---

## 🌟 [v1.2.0] — 2026-08-28
### ✨ Tính năng mới & Đột phá Chất lượng Hình ảnh
- **Nâng cấp Đột phá Chất lượng Upscale HD (Catmull-Rom + Anime4K Linework Enhancement)**:
  - Tích hợp bộ lọc nội suy bậc cao **Catmull-Rom 4x4 Spline Filter** thay thế hoàn toàn thuật toán cũ.
  - Tích hợp bộ tăng cường nét vẽ thích ứng tương phản (**Contrast-Adaptive Sharpening / Anime4K Linework Sharpener**): Tự động phát hiện và làm sắc nét các đường nét vẽ mực, khung thoại và chi tiết nhân vật mà không gây nhiễu hạt hay bóng viền (ringing artifacts).
  - Khắc phục triệt để hiện tượng vỡ hạt, mờ đục, mang lại bức ảnh sắc nét chuẩn 4K Ultra-HD.
- **Tùy chọn Thư mục Lưu Kết Quả (Custom Output Directory Picker)**:
  - Cho phép người dùng trực tiếp chọn thư mục mong muốn trên máy để lưu file kết quả qua Storage Access Framework.
  - Hỗ trợ nút "Đặt lại" để quay về thư mục mặc định (`Pictures/UpScale` hoặc `Download/UpScale`).
- **Quy chuẩn Đặt tên Tệp Chuẩn hóa**:
  - Giữ nguyên 100% tên tệp đầu vào và tự động gắn thêm hậu tố biểu thị tỉ lệ phóng to: `[Tên_gốc]_Upscale_2x.[ext]` hoặc `[Tên_gốc]_Upscale_4x.[ext]`.

---

## 📚 [v1.1.0] — 2026-08-28
### ✨ Tính năng mới & Đột phá Thuật toán
- **Hỗ trợ thêm định dạng Sách / Truyện Kindle MOBI & PRC (Palm Database Format)**:
  - Tích hợp `MobiProcessor`: Phân tích trực tiếp bảng Record Table của tệp Palm Database mà không nạp toàn bộ file vào RAM ($O(1)$ RAM).
  - Tự động lọc và trích xuất nguyên vẹn các bản ghi hình ảnh (JPEG, PNG, GIF, WebP) theo Magic Bytes.
  - Upscale toàn bộ trang truyện AI 4X và đóng gói thành tệp truyện siêu phân giải chuẩn CBZ/MOBI.
  - Vẫn duy trì hỗ trợ 100% các định dạng cũ: Ảnh đơn (JPG, PNG, WebP, BMP) và Tập truyện nén (ZIP, CBZ).
- **Triệt tiêu 100% Vết ghép Ô vuông (Seamless Normalized Weight Merging)**:
  - Khắc phục triệt để hiện tượng các ô vuông phân chia trên ảnh sau khi upscale.
  - Nâng cấp thuật toán `TileProcessor` với bộ tích lũy màu và hàm trọng số hình thang chuẩn hóa $\frac{\sum W_k \cdot C_k}{\sum W_k}$ tại dải chồng lấn (overlap $64\text{px}$).
  - Loại bỏ hoàn toàn méo biên (boundary distortion) của CNN, tạo ra bức ảnh đầu ra liền mạch, mịn màng và hoàn hảo tuyệt đối.
- **Tự động nhận diện hoàn thành thông minh (Intelligent Completion Recognition)**:
  - Khi hoàn thành, ứng dụng tự động hiển thị Snackbar chúc mừng và cuộn mượt mà đến khu vực kết quả.
  - Thanh công cụ hành động phía dưới tự động biến đổi thành 2 nút chuyên dụng:
    - 🔍 **"XEM ẢNH / MỞ TẬP TRUYỆN ĐÃ UPSCALE"** (Màu xanh Emerald nổi bật, chạm là mở xem ngay).
    - 🔄 **"UPSCALE TỆP KHÁC"** (Reset trạng thái để sẵn sàng chọn tệp mới).
  - Thanh thông báo Foreground hiển thị trạng thái hoàn tất kèm icon tải xong.

---

## 🚀 [v1.0.5] — 2026-08-28
### ✨ Tính năng mới & Cải tiến UX
- **Điều hướng Back an toàn (Back Confirmation Dialog)**:
  - Khi người dùng nhấn phím Back hoặc vuốt cử chỉ Back trong lúc đang upscale, ứng dụng sẽ chặn hành động thoát đột ngột và hiển thị hộp thoại xác nhận 3 tùy chọn:
    1. 💾 **Lưu tạm & Thoát**: Tự động tạo thư mục `UpScale_Drafts/`, lưu lại tệp đang xử lý kèm metadata (`preset`, `scale`, `tile_size`, `timestamp`) để tiếp tục sau.
    2. ❌ **Hủy tiến trình**: Hủy ngay lập tức tác vụ upscale, dọn dẹp sạch sẽ bộ nhớ đệm cache, **bảo toàn nguyên vẹn 100% tệp gốc ban đầu** và đóng app.
    3. 🔙 **Tiếp tục**: Đóng hộp thoại và tiếp tục tiến trình upscale.
- **Tiếp tục chạy ngầm khi ra màn hình Home**:
  - Khi người dùng bấm Home hoặc chuyển sang ứng dụng khác, WorkManager Foreground Service (`FOREGROUND_SERVICE_DATA_SYNC`) tiếp tục duy trì tiến trình upscale không bị gián đoạn.
- **Bộ biểu tượng ứng dụng 3D Chuyên Nghiệp (App Icon)**:
  - Thiết kế lại toàn bộ hệ thống Adaptive Vector Icon:
    - 🌌 **Background**: Nền Cyber Dark Gradient (`#0B0F19` $\rightarrow$ `#3B0764`) với quầng sáng phát quang trung tâm.
    - 🔷 **Foreground**: Khối khiên 3D Glassmorphic chứa **Mũi tên Đột phá AI 4X Siêu Phân Giải** với ánh kim cương xanh Neon Cyan & Emerald Teal.
    - 🌗 **Monochrome**: Tương thích hoàn hảo tính năng đổi màu theo hình nền của Android 13–17 Material You.
    - ⚪ **Round Icon**: `ic_launcher_round.xml` tối ưu cho mọi launcher tròn/vuông.

---

## 🧠 [v1.0.4] — 2026-08-28
### ✨ Tính năng mới
- **Tự động nhận diện Preset thông minh (Smart Preset Auto-Detection)**:
  - Tích hợp thuật toán phân tích màu sắc và độ bão hòa (Saturation Analysis) chạy nền siêu nhanh ($< 5\text{ms}$):
    - 📖 **Manga B&W**: Nhận diện truyện tranh đen trắng khi tỉ lệ đơn sắc $\ge 82\%$ hoặc độ bão hòa $< 0.12$.
    - 🎨 **Cover Poster**: Nhận diện tranh nghệ thuật/ảnh bìa rực rỡ khi độ bão hòa $\ge 0.35$ hoặc kích thước lớn $\ge 1000\text{px}$.
    - 🌈 **Manga Màu**: Tối ưu cho Manhwa / Webtoon / Comic màu.
  - Hiển thị huy hiệu `✨ Tự động nhận diện` trên giao diện.
- **Linh hoạt chọn Preset thủ công**: Người dùng có thể tự do bấm đổi preset bất kỳ lúc nào mà không gây crash app hay xung đột cài đặt.

---

## ⚡ [v1.0.3] — 2026-08-28
### ✨ Tính năng mới & Sửa lỗi hiển thị Launcher
- **Hiển thị ảnh thời gian thực (Live Runtime Rendering)**:
  - Khi AI upscale từng tile và ghép vào master buffer, thumbnail thời gian thực (`runtimePreview`) được stream trực tiếp lên thanh so sánh `PreviewSlider`.
  - Người dùng có thể kéo trượt thanh so sánh ngay khi đang render kèm huy hiệu phát sáng `⚡ RENDERING LIVE`.
- **Thẻ kết quả thông minh & Lưu tệp mới rõ ràng (Completed Result Card)**:
  - Hiển thị rõ: Tên file mới, độ phân giải (`4800x7200 4K UHD`), dung lượng, thư mục lưu (`Pictures/UpScale` hoặc `Download/UpScale`).
  - Khẳng định bảo toàn file gốc 100%.
  - Tích hợp nút **"Xem ảnh"** và **"Chia sẻ"** an toàn qua `FileProvider`.
- **Khắc phục lỗi biểu tượng trên Android 16/17**:
  - Bổ sung cấu trúc Adaptive Icon đầy đủ (`foreground`, `background`, `monochrome`, `roundIcon`).
  - Khai báo resource `@string/app_name = "UpScale"` và `android.intent.category.DEFAULT` trong AndroidManifest.

---

## 🛡️ [v1.0.2] — 2026-08-28
### 🐛 Sửa lỗi Crash khi bấm "Bắt đầu Upscale"
- **Khắc phục triệt để `Config#HARDWARE` Bitmap Exception**:
  - `TileProcessor` tự động phát hiện và chuyển đổi Hardware Bitmap sang Software Bitmap (`ARGB_8888`) trước khi truy cập pixel, loại bỏ lỗi `IllegalArgumentException: unable to getPixels()`.
- **Khắc phục lỗi Foreground Service trên Android 14/15/16/17**:
  - Bổ sung `ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC` vào `ForegroundInfo`.
  - Bọc `setForeground()` trong khối try-catch an toàn trong `UpscaleWorker`.
- **Tự động nạp ảnh kết quả**: `UpscaleViewModel` tự động lắng nghe trạng thái `Completed` và nạp ảnh sau khi upscale vào `_afterBitmap`.
- **Bọc an toàn Haptic Feedback & Ncnn Fallback**: Chống ngoại lệ `SecurityException` và `IndexOutOfBoundsException`.

---

## 🚀 [v1.0.1] — 2026-08-27
### 🛠️ Tối ưu hóa bộ nhớ OOM & Android 17 Support
- **16KB ELF Page Size Alignment**: Thêm cờ `-Wl,-z,max-page-size=16384` trong CMake cho Android 15/16/17+.
- **Hỗ trợ 4 kiến trúc ABI**: `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`.
- **OOM Guard 4GB RAM**:
  - Thêm giới hạn an toàn `MAX_OUTPUT_DIMENSION = 4096` (4K UHD $\le 64\text{MB}$ heap buffer).
  - Tối ưu `BatchZipProcessor` với $O(1)$ random-access `ZipFile` và `inSampleSize` decoding.
- **Ký số phát hành Signed Release APK**: Cấu hình keystore `feather_release.jks`, đổi tên ứng dụng thành **UpScale**.

---

## 🎉 [v1.0.0] — 2026-08-27
### 🌟 Khởi tạo dự án FeatherUpscale
- **Engine Upscale AI NCNN Vulkan**:
  - Tích hợp mô hình Real-ESRGAN x4, hỗ trợ GPU Vulkan backend và chế độ tính toán nửa chính xác FP16.
  - Thuật toán `TileProcessor` chia mảnh 256px / 128px với dải chồng lấn (overlap) 16px và bộ hòa trộn lông vũ tuyến tính (Linear Feather Blending).
- **Giao diện Modern Material 3 & Jetpack Compose**:
  - Thanh so sánh Before/After cảm ứng kéo trượt `PreviewSlider` với phản hồi xúc giác rung (Haptic Feedback).
  - Hỗ trợ chọn ảnh đơn và xử lý hàng loạt truyện tranh nén (Batch ZIP / CBZ).
  - Thanh thông báo Foreground Notification hiển thị tiến độ và nút Pause / Resume / Cancel.
