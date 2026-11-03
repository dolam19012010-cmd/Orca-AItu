# 📱 OrcaAuto - Trợ Lý AI Tóm Tắt Thông Báo Công Việc

OrcaAuto là ứng dụng Android hiện đại được xây dựng hoàn toàn bằng **Kotlin** và **Jetpack Compose** (Material 3), kết hợp sức mạnh của **Google Gemini API** để tự động quét, lọc nhiễu và tổng hợp báo cáo công việc từ các ứng dụng nhắn tin phổ biến (**Zalo, Telegram, Facebook Messenger**).

---

## 🌟 Tính Năng Nổi Bật

### 1. 🎯 Lắng Nghe & Lọc Thông Báo Thông Minh
- **NotificationListenerService (`NotificationMonitorService`)**: Lắng nghe theo thời gian thực các thông báo đến từ Zalo (`com.zing.zalo`), Telegram (`org.telegram.messenger`), và Messenger (`com.facebook.orca`).
- **Bộ Lọc Khử Nhiễu (`NotificationFilter`)**:
  - Tự động phát hiện và loại bỏ mã OTP, mã xác thực ngân hàng qua hệ thống biểu thức chính quy (Regex).
  - Loại trừ các tin nhắn rác, quảng cáo, khuyến mãi ("săn deal", "voucher", "vay nhanh"...).
  - Loại trừ các thông báo hệ thống và thông báo đang tiến hành (*Ongoing / Foreground Progress* như đang tải ảnh, đang đồng bộ).
- **Bảo Vệ Quyền Riêng Tư (`PiiSanitizer`)**: Tự động che mờ số tài khoản ngân hàng, số thẻ tín dụng, số điện thoại trước khi lưu trữ hoặc gửi lên AI.

### 2. ⚡ Khắc Chế Diệt Ứng Dụng Ngầm & Chạy 24/7 (OEM Survival)
- **Foreground Service (`KeepAliveForegroundService`)**: Khởi chạy tiến trình ngầm thường trực với Notification hiển thị trạng thái hệ thống, ngăn hệ điều hành thu hồi tài nguyên (Low Memory Killer).
- **Khắc phục Doze Mode**: Nút một chạm điều hướng xin quyền bỏ qua tối ưu hóa pin (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
- **Khắc phục Xiaomi / Samsung / Oppo (`OemSurvivalHelper`)**: Tích hợp Intent mở trực tiếp trang cấp quyền **Tự khởi chạy (Autostart)** và **Khóa ứng dụng trong đa nhiệm**.
- **Tự động Phục hồi (`BootReceiver` & Auto Re-bind)**: Tự động khởi động lại dịch vụ ngay khi điện thoại khởi động lại (`BOOT_COMPLETED`), tự kết nối lại nếu hệ thống vô tình unbind listener.

### 3. 🤖 Tóm Tắt Bằng Google Gemini API & Lập Lịch WorkManager
- **Lập lịch Định kỳ 2 Lần / Ngày (`WorkScheduler`)**: Sử dụng Android Jetpack `WorkManager` lập lịch chạy chuẩn xác vào **12:00** và **18:00** hàng ngày.
- **Cơ Chế Thử Lại (Retry 15 Phút)**:
  - Nếu mất kết nối mạng hoặc Gemini API trả về mã lỗi (429 Rate Limit, 5xx), **toàn bộ dữ liệu tin nhắn được giữ nguyên trong Room Database**.
  - Tự động kích hoạt cơ chế `BackoffPolicy.EXPONENTIAL` thử lại sau **15 phút**.
  - Chỉ khi Gemini API trả về kết quả thành công, ứng dụng mới đánh dấu `isProcessed = true` trong Room Transaction.
- **Prompt Chuyên Dụng Cho Trợ Lý Điều Hành (`GeminiPromptBuilder`)**: Bóc tách nội dung thành:
  - 🎯 **Việc Cần Làm Ngay (Action Items)**
  - ⏰ **Hạn Chót & Lịch Hẹn (Deadlines / Schedule)**
  - 📌 **Thông Tin Cập Nhật Quan Trọng (Key Updates)**
  - 💡 **Ghi Chú Đề Xuất**

### 4. 🎨 Giao Diện Jetpack Compose Material 3 Hiện Đại
- **Bảng Điều Khiển (Dashboard)**:
  - Kiểm tra trạng thái cấp quyền Notification Access (có nút dẫn thẳng vào Cài đặt hệ thống).
  - Trạng thái Foreground Service và công tắc bật/tắt.
  - Số lượng tin nhắn đang chờ xử lý trong Room Database.
  - Nút **"Tóm Tắt AI Ngay Bây Giờ"** để thử nghiệm tức thì mà không cần đợi đến giờ định kỳ.
- **Cấu Hình (Settings)**:
  - Nhập Gemini API Key (bảo vệ với Android Keystore / `EncryptedSharedPreferences`).
  - Nút **"Kiểm Tra Key"** xác thực kết nối ngay lập tức.
  - Công tắc Bật/Tắt quét riêng cho từng app (Zalo, Telegram, Messenger).
  - Xem khung giờ báo cáo (12:00 & 18:00).
- **Lịch Sử (History)**:
  - Hiển thị các bản tóm tắt AI theo dòng thời gian.
  - Nút **Sao chép nội dung tóm tắt** vào Clipboard để gửi email, báo cáo nhanh.

---

## 🛠️ Cấu Trúc Thư Mục Dự Án

```
OrcaAuto/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/orca/orcaauto/
│       │   ├── OrcaApplication.kt            # Application Class khởi tạo Database, Worker, Service
│       │   ├── data/
│       │   │   ├── local/                    # Room Database (Entity, DAO, Database)
│       │   │   ├── preferences/              # EncryptedSharedPreferences (API Key, Toggles)
│       │   │   └── remote/                   # Gemini API Client (Google AI SDK + REST fallback)
│       │   ├── domain/
│       │   │   ├── filter/                   # Lọc OTP, Spam, Khử PII
│       │   │   └── prompt/                   # Chế tác Prompt tóm tắt công việc
│       │   ├── service/                      # NotificationListener, ForegroundService, BootReceiver
│       │   ├── worker/                       # WorkManager SummaryWorker & Scheduler
│       │   └── ui/                           # Jetpack Compose Screens, Themes, Navigation
│       └── res/                              # Icons, Colors, Strings, XML Rules
├── gradle/
│   └── libs.versions.toml                    # Version Catalog quản lý phiên bản
├── build.gradle.kts
├── settings.gradle.kts
└── gradlew.bat
```

---

## 🚀 Hướng Dẫn Biên Dịch File APK Debug

### Yêu Cầu Môi Trường
1. **JDK 17** hoặc **JDK 21+** (Đã kiểm tra tương thích).
2. **Android SDK** (API 34 - Android 14) hoặc mở trực tiếp bằng **Android Studio Hedgehog / Iguana / Jellyfish / Koala**.

### Cách 1: Sử Dụng Lệnh Gradle (Terminal / PowerShell)

Mở PowerShell tại thư mục dự án (`d:\du an\OrcaAuto`):

```powershell
# 1. Biên dịch ra file APK Debug
.\gradlew.bat assembleDebug

# Trên macOS / Linux:
# ./gradlew assembleDebug
```

File APK hoàn chỉnh sau khi build thành công sẽ nằm tại:
📂 `app/build/outputs/apk/debug/app-debug.apk`

Cài đặt trực tiếp lên điện thoại Android qua ADB:
```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

### Cách 2: Mở Trong Android Studio

1. Mở **Android Studio** -> Chọn **File** -> **Open...**
2. Trỏ tới thư mục `d:\du an\OrcaAuto` và bấm **OK**.
3. Đợi Android Studio đồng bộ Gradle (Sync Project with Gradle Files).
4. Vào menu **Build** -> **Build Bundle(s) / APK(s)** -> **Build APK(s)**.
5. Khi build xong, bấm vào chữ **locate** ở góc dưới bên phải để lấy file `app-debug.apk`.

---

## 📋 Các Bước Thiết Lập Khi Mở Ứng Dụng Lần Đầu

1. **Nhập Gemini API Key**:
   - Vào tab **Cấu hình** -> Nhập Gemini API Key lấy từ [Google AI Studio](https://aistudio.google.com/).
   - Bấm **Kiểm tra Key** để đảm bảo key hoạt động tốt -> Bấm **Lưu Key**.
2. **Cấp Quyền Truy Cập Thông Báo**:
   - Vào tab **Trạng thái** -> Bấm **Cấp quyền ngay** -> Tìm **OrcaAuto** và gạt công tắc cho phép.
3. **Tắt Tối Ưu Hóa Pin**:
   - Bấm **Bỏ qua pin** -> Chọn Cho phép để app chạy ngầm không bị Doze Mode đóng băng.
4. *(Dành cho máy Xiaomi / Oppo / Vivo)*:
   - Bấm **Cài đặt tự khởi chạy** -> Bật Tự khởi chạy (Autostart) cho OrcaAuto.
