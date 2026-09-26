# ShortsForeverBlocker

Ứng dụng Android chặn YouTube Shorts trong app YouTube chính chủ bằng Accessibility Service.

## Mục tiêu
- Không root.
- Điện thoại và tablet Android.
- Không quyền Internet.
- Chỉ lắng nghe package `com.google.android.youtube`.
- Chạm tab Shorts hoặc mở Shorts player -> tự Back.
- Accessibility đã được người dùng bật sẽ được Android bind lại sau reboot.
- Có `RECEIVE_BOOT_COMPLETED` và màn hình hướng dẫn bỏ tối ưu pin.

## Cài sau khi build
1. Cài APK.
2. Mở app -> **BẬT CHẶN SHORTS (TRỢ NĂNG)**.
3. Chọn **Chặn YouTube Shorts** -> Cho phép.
4. Quay lại app -> **CHO PHÉP CHẠY NỀN / TẮT TỐI ƯU PIN**.
5. Nếu ROM có mục Auto launch/Tự khởi động, bật cho app.

## Build bằng Android Studio
Mở thư mục này bằng Android Studio, chờ Gradle sync, sau đó Build > Build APK(s).

## Build bằng GitHub Actions
Push project lên GitHub. Workflow `.github/workflows/build-apk.yml` sẽ tạo artifact `ShortsForeverBlocker-debug`.

## Ghi chú kỹ thuật
YouTube có thể đổi cây Accessibility theo phiên bản. Logic detector cố tình dùng nhiều tín hiệu thay vì một resource-id duy nhất để bền hơn. Nếu YouTube thay đổi UI lớn, cập nhật `ShortsBlockerService.java`.
