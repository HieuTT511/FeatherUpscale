# UpScale — Quy Tắc Dự Án (Project Rules)

Tài liệu này quy định các quy tắc bắt buộc áp dụng cho toàn bộ quá trình phát triển, kiểm thử, đóng gói và phát hành ứng dụng **UpScale** (FeatherUpscale).

---

## 📌 Quy Tắc 1: Định Danh Ứng Dụng Sau Khi Release
- Tên hiển thị của ứng dụng trên thiết bị Android: **UpScale** (`android:label="UpScale"`).
- Tên tệp tin APK phát hành sau khi build release:
  - Bản theo phiên bản: `UpScale-v{MAJOR.MINOR.PATCH}.apk` (ví dụ: `UpScale-v1.0.0.apk`).
  - Bản symlink / direct alias: `UpScale.apk`.

---

## 📌 Quy Tắc 2: Quy Tắc Tự Động Bump Version (Semantic Versioning)
Mỗi lần phát hành bản build mới, hệ thống tự động nhảy version theo quy tắc Semantic Versioning:
- **Format**: `MAJOR.MINOR.PATCH` đi kèm `versionCode = (MAJOR * 100) + (MINOR * 10) + PATCH`.
- **Thêm tính năng mới (New Feature)**:
  - Tăng `MINOR`: Ví dụ `1.0.0` $\rightarrow$ `1.1.0` (`versionCode = 110`).
- **Sửa lỗi / Tinh chỉnh UX / Tối ưu nhỏ (Bug Fix / Polish)**:
  - Tăng `PATCH`: Ví dụ `1.0.0` $\rightarrow$ `1.0.1` (`versionCode = 101`).
- **Thay đổi kiến trúc cốt lõi / Phá vỡ tương thích (Breaking Change)**:
  - Tăng `MAJOR`: Ví dụ `1.0.0` $\rightarrow$ `2.0.0` (`versionCode = 200`).

---

## 📌 Quy Tắc 3: Quy Tắc Sửa Lỗi Không Ảnh Hưởng Module Khác (Regression Protection)
- Bất kỳ bản sửa lỗi (Fix) hay tính năng mới nào cũng phải tuân thủ nguyên tắc độc lập module qua Interface / Adapter.
- **Bắt buộc**: Trước mỗi lần build release hoặc commit, phải chạy toàn bộ JVM Unit Test suite:
  ```powershell
  & ".\gradle\wrapper\dists\...\gradle.bat" :app:testDebugUnitTest --no-daemon
  ```
  Chỉ khi đạt **100% PASS** mới được tiến hành đóng gói release.

---

## 📌 Quy Tắc 4: Không Tự Động Xóa File (File Deletion Safety)
- Tuyệt đối **KHÔNG** tự ý xóa bất kỳ file nào trong dự án mà không có sự kiểm tra, review và xác nhận trực tiếp từ người dùng.
- Khi cần dọn dẹp hoặc refactor mã nguồn, phải liệt kê danh sách tệp dự kiến xóa và xin ý kiến người dùng trước.

---

## 📌 Quy Tắc 5: Luôn Build Signed Release APK với R8 Compiler
- Bản build phát hành luôn phải là **Signed Release APK**:
  - Trình biên dịch tối ưu hóa mã nguồn: **R8 Compiler** (`isMinifyEnabled = true`).
  - Trình dọn dẹp tài nguyên thừa: **Resource Shrinker** (`isShrinkResources = true`).
  - Chứng chỉ số bảo mật: Ký bằng Keystore phát hành (`feather_release.jks`, hỗ trợ đồng thời Signature Scheme V1 và V2).

---

## 📌 Quy Tắc 6: Tự Động Quản Lý Git & Đẩy Lên GitHub
- Tự động tạo kho lưu trữ Git và đẩy lên tài khoản GitHub đang đăng nhập trên VS Code (`HieuTT511`).
- Duy trì branch chính thức `main` luôn ở trạng thái mã nguồn sạch, build được và đã kiểm thử thành công.

