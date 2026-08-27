# UpScale Project Rules

1. Sau khi build xong released app đổi tên thành UpScale (Tệp phát hành: UpScale-v{version}.apk & UpScale.apk, nhãn app: UpScale).
2. Sau mỗi bản build tùy vào feature hay fix mà nhảy bump version (Feature: bump MINOR, Fix/Polish: bump PATCH, Breaking: bump MAJOR).
3. Quy tắc fix không ảnh hưởng function khác (Luôn chạy test suite kiểm tra regression trước khi build release).
4. Không được tự động xóa file thiết lập, cần review và có sự đồng ý của user khi xóa.
5. Luôn build release signed apk file với R8 compiler (minify + shrink resources).
6. Tạo git repo tự động rồi đẩy lên github account đang login vào VS code tự động (HieuTT511/FeatherUpscale hoặc HieuTT511/UpScale).
