# Nguyên Tắc Làm Việc Trong OrcaAuto

## 1. Quy Định Build APK
- Chỉ chạy lệnh biên dịch APK (`.\gradlew.bat assembleDebug`) khi hoàn tất các đợt cập nhật lớn cần kiểm thử thực tế trên thiết bị, sau khi toàn bộ unit test đã PASS 100%.
- Đối với các sửa đổi nhỏ, tinh chỉnh logic hoặc refactor nhẹ: chỉ chạy kiểm thử nhanh (`testDebugUnitTest`), không lạm dụng build APK liên tục để tiết kiệm thời gian.

## 2. Kỷ Luật Phạm Vi Tuyệt Đối (No Unrequested Work)
- **KHÔNG TỰ VẼ:** Chỉ thực hiện đúng các hạng mục, file và tính năng người dùng đã chỉ định hoặc xác nhận. Không tự ý thêm service, tính năng phụ, tối ưu ngoài luồng hay chỉnh sửa UI chưa được yêu cầu.
- Khi người dùng yêu cầu kiểm tra, đọc log, đối chiếu hiện trạng hoặc đánh giá vấn đề: chỉ kiểm tra và báo cáo trung thực kết quả thực tế, **không tự ý sửa code**.

## 3. Quy Định Commit & Push Git
- Cho phép tự động commit và `git push` lên remote ngay sau khi hoàn tất đúng phạm vi công việc đã thống nhất và vượt qua toàn bộ unit test kiểm chứng (không cần chờ người dùng nhắc từng lệnh commit/push).
