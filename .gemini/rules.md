# Project Rules for UpScale (FeatherUpscale)

Tất cả các quy tắc dưới đây là **bắt buộc tuân thủ tuyệt đối**:

## Quy Tắc 1: Định danh App sau khi release
- Đổi tên app thành **UpScale** (`android:label="UpScale"`).
- File release apk đổi tên thành `UpScale-v{version}.apk` và `UpScale.apk`.

## Quy Tắc 2: Quy tắc Bump Version (Semantic Versioning)
- Thêm feature -> bump MINOR (ví dụ: `1.0.0` -> `1.1.0`, `versionCode = 110`).
- Sửa fix / polish -> bump PATCH (ví dụ: `1.0.0` -> `1.0.1`, `versionCode = 101`).
- Breaking change -> bump MAJOR (ví dụ: `1.0.0` -> `2.0.0`, `versionCode = 200`).

## Quy Tắc 3: Quy tắc Fix không ảnh hưởng function khác (Regression Protection)
- Module hóa qua interface/adapter độc lập.
- Bắt buộc chạy và pass 100% JVM unit tests trước khi build release apk.

## Quy Tắc 4: Không được tự động xóa file
- Không tự ý xóa bất kỳ file nào mà không được user review và đồng ý trước.

## Quy Tắc 5: Luôn build signed release apk file
- Luôn build bằng cấu hình `release` với R8 compiler enabled (`isMinifyEnabled = true`, `isShrinkResources = true`).
- Ký bằng release keystore (`feather_release.jks`).

## Quy Tắc 6: Tự động quản lý Git Repo & đẩy lên GitHub
- Tự động tạo git repo (nếu chưa có) và push lên GitHub account đang đăng nhập trong VS Code (`HieuTT511`).

## Quy Tắc 7: Tìm hiểu thuật toán trước khi triển khai
- Trước khi triển khai hoặc thay đổi bất kỳ thuật toán nào, phải tìm hiểu đầy đủ nguyên lý hoạt động, đầu vào/đầu ra, các giả định, trường hợp biên và giới hạn áp dụng của thuật toán.
- Phải đánh giá tính đúng đắn, độ phức tạp thời gian/bộ nhớ và các đánh đổi liên quan trước khi lựa chọn hoặc triển khai.
- Khi có nhiều phương án, cần so sánh các phương án và nêu rõ lý do chọn phương án được sử dụng trong mã nguồn.

## Quy Tắc 8: Ghi nhật ký thay đổi (Changelog) cho mỗi bản release build
- Mọi bản build release phát hành đều phải được ghi nhận chi tiết trong tệp `CHANGELOG.md` tại thư mục gốc của dự án.
- Nội dung changelog bao gồm: Số phiên bản, ngày phát hành, danh sách tính năng mới, các cải tiến UX/giao diện và danh mục các lỗi đã được khắc phục.

## Quy Tắc 9: Tự động thực thi toàn diện (Autonomous Execution - Zero Prompting)
- Không cần hỏi người dùng accept hay review giữa chừng.
- Tự động chủ động triển khai trọn vẹn mọi bước: Nghiên cứu -> Lập trình -> Viết Unit Tests & chạy Test pass 100% -> Cập nhật Changelog -> Bump version -> Build Signed Release APK & Verify -> Git Commit & Push GitHub.
