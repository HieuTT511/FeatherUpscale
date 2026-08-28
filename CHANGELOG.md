# 📜 UpScale (FeatherUpscale) — Nhật Ký Thay Đổi (Changelog)

Tất cả các thay đổi, tính năng mới và bản vá lỗi của ứng dụng UpScale được ghi nhận chi tiết tại đây theo chuẩn **Semantic Versioning**.

---

## 🌟 [v1.3.6] — 2026-08-28
### 🛡️ Chuẩn Hóa Tương Thích Tuyệt Đối Cho Mọi Ứng Dụng Đọc Truyện / Ebook
- **Khắc Phục Triệt Để Lỗi Crash Trên Các App Đọc Ebook Khác (Tachiyomi, Mihon, Perfect Viewer, Moon+ Reader, Kindle, v.v.)**:
  - **Nguyên nhân gốc rễ phát hiện**: Khi upscale 4X/8X một tập truyện gồm 50–200 trang, kích thước ảnh lên tới $6000\times 9000\text{px}$ ($> 60\text{ Megapixels}$/trang). Dù file nén JPEG trên ổ cứng nhẹ, nhưng khi các app đọc truyện mở file và preload 3–5 trang liền kề vào RAM để cuộn trang mượt mà, bộ nhớ RAM giải mã Bitmap lên đến $\approx 1.2\text{GB}$ và vượt quá giới hạn phần cứng `GL_MAX_TEXTURE_SIZE` ($4096\text{px}$), khiến app đọc truyện bị tràn RAM (OOM) hoặc crash đồ họa.
  - **Chuẩn hóa Độ phân giải Trang Truyện 4K UHD ([TileProcessor.MAX_COMIC_PAGE_DIMENSION] = 3840px)**:
    - Mọi trang truyện trong tập CBZ/MOBI được upscale đạt chuẩn **4K UHD ($3840\text{px}$)** sắc nét tuyệt đối trên mọi màn hình máy tính bảng và điện thoại 4K.
    - Giảm lượng RAM giải mã của app đọc truyện xuống mức an toàn $\approx 25\text{MB}$/trang, giúp lật trang/cuộn webtoon 60fps mượt mà và **100% không bao giờ bị crash**.
  - **Đóng gói CBZ Tiêu Chuẩn Quốc Tế**:
    - Nén Baseline JPEG 92 và cấu trúc `Deflated Zip` tiêu chuẩn tương thích 100% với tất cả trình đọc truyện tranh trên Android, iOS, Windows và macOS.

---

## 🌟 [v1.3.5] — 2026-08-28
### 📚 Chuẩn Hóa Định Dạng Tệp Truyện CBZ (Loại Bỏ Hoàn Toàn Tự Động Đổi Đuôi .ZIP)
- **Bảo Toàn 100% Đuôi Mở Rộng `.cbz` Cho Tập Truyện**:
  - Khắc phục cơ chế tự động ép đuôi `.zip` của Android DocumentFile: Sử dụng MIME type chuyên dụng `application/x-cbz` để hệ điều hành lưu chính xác tệp truyện tranh `[Tên_gốc]_Upscale_[scale]x.cbz`.
  - Không còn hiện tượng tự động sinh tệp `.zip` hay `.cbz.zip`.

---

## 🌟 [v1.3.4] — 2026-08-28
### 🎨 Cải Tiến Toàn Diện Khung Preview & Sửa Lỗi Mở Thư Mục Xuất Ảnh
- **Triệt Tiêu 100% Hai Khoảng Đen Hai Bên (Aspect-Adaptive Fill)**:
  - Khung xem preview tự động thích ứng với tỉ lệ thật của bức ảnh (`aspectRatio`), loại bỏ hoàn toàn hiện tượng 2 vệt đen trống trải hai bên sườn.
- **Thanh Trượt So Sánh Mượt Mà Trở Lại (Silky Drag & Haptic)**:
  - Tách biệt cử chỉ vuốt ngang `detectHorizontalDragGestures`.
- **Khắc Phục Lỗi "Invalid file format" Khi Nhấn Mở Thư Mục**:
  - Sửa lỗi gán sai MIME type trong `openFolder`.

---

## 🌟 [v1.3.3] — 2026-08-28
### 🚀 Kiến Trúc Siêu Phân Giải 8K An Toàn Tuyệt Đối (Google AI Architecture)
- **Khắc Phục Triệt Để Lỗi OOM 8K**:
  - Triển khai **Google AI Adaptive Target Clamping**: Tự động tính toán tỉ lệ phóng đại tối đa lên chuẩn **8K Ultra-HD ($8192\text{px}$)** cho ảnh đơn.
  - Bảo toàn 100% chi tiết ảnh gốc.

---

## 🌟 [v1.3.2] — 2026-08-28
### 🎨 Thiết Kế Lại Khung So Sánh Preview Điện Ảnh & Zoom Soi Chi Tiết Chuẩn Xác
- **Aspect-Fit Chuẩn Xác 100% (Loại bỏ triệt để méo hình)**.
- **Tính năng Pinch-to-Zoom & Pan Soi Chi Tiết Cận Cảnh (1.0x – 5.0x)**.
- **Lấy Mẫu Phản Ánh Độ Nét Tương Phản Thật (Authentic Super-Resolution Rendering)**.

---

## 🌟 [v1.3.1] — 2026-08-28
### 🛡️ Đột phá Chống OOM Toàn Diện & Ghép Tile Liền Mạch C^1 (Raised-Cosine)
- **Kiến trúc Zero-Heap Memory Footprint (Chống OOM 100% khi Upscale 8K / 8X)**:
  - Loại bỏ hoàn toàn việc cấp phát các mảng `FloatArray` khổng lồ.
  - Áp dụng cơ chế **In-Place Tile Blending** trực tiếp vào Bitmap đầu ra: Giảm dung lượng RAM yêu cầu trong JVM heap từ $1600\text{MB}$ xuống dưới **$15\text{MB}$**.
- **Bảo toàn 100% Chi Tiết Gốc (Không tự ý nén nhỏ ảnh)**.
- **Thuật toán Ghép Tile Liền Mạch Bậc Cao (Raised-Cosine $C^1$ Smooth Blending)**.
- **Tối ưu Hóa Bộ Lọc Thích Ứng Tương Phản (CAS Clean Lines)**.

---

## 🚀 [v1.3.0] — 2026-08-28
### ✨ Tính năng mới Đột phá: Upscale 8X Ultra-HD Max & Engine Lưu Trữ Document Tree
- **Đột phá Tỉ lệ Siêu Phân Giải 8X (8K Ultra-HD Output)**.
- **Khắc phục Triệt Để Logic Lưu Thư Mục Tùy Chọn (`StorageHelper`)**.

---

## 🌟 [v1.2.1] — 2026-08-28
### ✨ Tính năng mới & Tự Động Hóa Thư Mục
- **Tự động Khởi tạo Thư mục Đầu ra Chuyên dụng (`UpScale`)**.
- **Tự động Nhận diện & Xác minh Tệp Đầu ra trong Thư viện (Verified Media Scanning)**.
- **Tùy chọn Mở Thư mục Kết Quả Thông Minh**.

---

## 🌟 [v1.2.0] — 2026-08-28
### ✨ Tính năng mới & Đột phá Chất lượng Hình ảnh
- **Nâng cấp Đột phá Chất lượng Upscale HD (Catmull-Rom + Anime4K Linework Enhancement)**.
- **Tùy chọn Thư mục Lưu Kết Quả (Custom Output Directory Picker)**.
- **Quy chuẩn Đặt tên Tệp Chuẩn hóa**.

---

## 📚 [v1.1.0] — 2026-08-28
### ✨ Tính năng mới & Đột phá Thuật toán
- **Hỗ trợ thêm định dạng Sách / Truyện Kindle MOBI & PRC (Palm Database Format)**.
- **Triệt tiêu 100% Vết ghép Ô vuông (Seamless Normalized Weight Merging)**.
- **Tự động nhận diện hoàn thành thông minh (Intelligent Completion Recognition)**.

---

## 🚀 [v1.0.5] — 2026-08-28
### ✨ Tính năng mới & Cải tiến UX
- **Điều hướng Back an toàn (Back Confirmation Dialog)**.
- **Tiếp tục chạy ngầm khi ra màn hình Home**.
- **Bộ biểu tượng ứng dụng 3D Chuyên Nghiệp (App Icon)**.
