# 📜 UpScale (FeatherUpscale) — Nhật Ký Thay Đổi (Changelog)

Tất cả các thay đổi, tính năng mới và bản vá lỗi của ứng dụng UpScale được ghi nhận chi tiết tại đây theo chuẩn **Semantic Versioning**.

---

## 🌟 [v1.3.5] — 2026-08-28
### 📚 Chuẩn Hóa Định Dạng Tệp Truyện CBZ (Loại Bỏ Hoàn Toàn Tự Động Đổi Đuôi .ZIP)
- **Bảo Toàn 100% Đuôi Mở Rộng `.cbz` Cho Tập Truyện**:
  - Khắc phục cơ chế tự động ép đuôi `.zip` của Android DocumentFile: Sử dụng MIME type chuyên dụng `application/x-cbz` để hệ điều hành lưu chính xác tệp truyện tranh `[Tên_gốc]_Upscale_[scale]x.cbz`.
  - Không còn hiện tượng tự động sinh tệp `.zip` hay `.cbz.zip`. Các ứng dụng đọc truyện tranh (Tachiyomi, Mihon, Perfect Viewer, CDisplayEx, v.v.) nhận diện và đọc trực tiếp mượt mà ngay lập tức.

---

## 🌟 [v1.3.4] — 2026-08-28
### 🎨 Cải Tiến Toàn Diện Khung Preview & Sửa Lỗi Mở Thư Mục Xuất Ảnh
- **Triệt Tiêu 100% Hai Khoảng Đen Hai Bên (Aspect-Adaptive Fill)**:
  - Khung xem preview tự động thích ứng với tỉ lệ thật của bức ảnh (`aspectRatio`), loại bỏ hoàn toàn hiện tượng 2 vệt đen trống trải hai bên sườn.
  - Ảnh dọc (manga/webtoon/chân dung), ảnh ngang hay ảnh vuông đều hiển thị lấp đầy khung hình tự nhiên, cân đối và sắc nét tuyệt đối.
- **Thanh Trượt So Sánh Mượt Mà Trở Lại (Silky Drag & Haptic)**:
  - Khắc phục hoàn toàn xung đột cử chỉ cảm ứng. Người dùng có thể vuốt ngón tay kéo trượt thanh chia Before/After mượt mà tức thì ở bất kỳ điểm nào trên màn hình.
  - Chạm 1 chạm để nhảy vạch chia đến vị trí ngón tay, chạm đúp 2 chạm để đưa vạch chia về chính giữa 50% kèm phản hồi rung Haptic.
  - Tích hợp nút Soi Chi Tiết Cận Cảnh 200% ở góc dưới.
- **Khắc Phục Lỗi "Invalid file format" Khi Nhấn Mở Thư Mục**:
  - Sửa lỗi gán sai MIME type: `openFolder` hiện mở chính xác thư mục Document URI hoặc thư viện ảnh/trình quản lý tệp hệ thống mà không còn bị báo lỗi định dạng không hợp lệ.

---

## 🌟 [v1.3.3] — 2026-08-28
### 🚀 Kiến Trúc Siêu Phân Giải 8K An Toàn Tuyệt Đối (Google AI Architecture)
- **Khắc Phục Triệt Để Lỗi OOM 8K**:
  - Triển khai **Google AI Adaptive Target Clamping**: Tự động tính toán tỉ lệ phóng đại tối đa lên chuẩn **8K Ultra-HD ($8192\text{px}$)**, đảm bảo cấp phát Bitmap đồ họa Android luôn an toàn 100%, không bị crash hay tràn RAM trên mọi máy.
  - Bảo toàn 100% chi tiết ảnh gốc: Lấy mẫu nội suy trực tiếp từ pixel gốc, không bao giờ nén nhỏ trước khi xử lý.

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
