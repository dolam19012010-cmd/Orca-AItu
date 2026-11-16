# Engineering Learnings & Incident Post-Mortems (OrcaAuto)

Tài liệu này ghi lại các bài học xương máu, phân tích sự cố thực tế (Post-Mortems) và các quy tắc kỹ thuật rút ra trong quá trình phát triển dự án OrcaAuto. Mọi kỹ sư và agent khi tham gia dự án phải đọc và áp dụng nghiêm ngặt các quy tắc này.

---

## Sự Cố #001: Bẫy Test Xanh & Lỗi Watchdog Tự Bóp Nghẹt Streaming ("Mất Kết Nối Mạng")

- **Ngày ghi nhận:** 16/11/2026
- **Mức độ nghiêm trọng:** P0 (Bloker - 100% câu hỏi gửi đi từ bóng chat nổi đều báo lỗi mạng)
- **Cam kết kiểm thử trước đó:** 259/259 unit test pass 100%, 5 agent thẩm định đều APPROVE.

### 1. Hiện tượng thực tế (Symptom & Impact)
Sau khi hoàn thành đợt refactor cải thiện độ ổn định và bàn giao bản APK (`f58b4fd`), người dùng cài đặt lên điện thoại thật và gửi bất kỳ câu hỏi nào trong bóng chat nổi đều nhận được thông báo lỗi:
> *"⚠️ Mất kết nối mạng. Chạm để thử lại"*

Lỗi xảy ra 100% các lần thử, bất kể mạng 4G/WiFi đang hoạt động hoàn toàn bình thường.

### 2. Phân tích nguyên nhân gốc rễ (Root Cause Analysis)
1. **Sai lệch pha của Watchdog (Phase Mismatch):**
   - Trong `FloatingBubbleService.kt`, bộ đếm `SSE_WATCHDOG_TIMEOUT_MS` (15 giây) được kích hoạt và bấm giờ ngay từ thời điểm $t=0$ khi người dùng bấm nút "Gửi".
   - Tuy nhiên, kiến trúc xử lý của app là pipeline 2 vòng (`LiveAiChatHelper.summarizeSession`):
     - **Vòng 1 (Round 1 Router):** Gọi non-streaming LLM để phân loại chỉ thị, lọc spam và routing ghi chú (mất 5–10 giây).
     - **Vòng 2 (Round 2 SSE Streaming):** Kết nối tới máy chủ OpenRouter/Gemini, xếp hàng chờ nhận byte đầu tiên (TTFB mất thêm 5–10 giây).
   - Tổng thời gian từ lúc bấm nút tới khi chữ đầu tiên (`onTokenDelta`) xuất hiện là **15–22 giây**.
   - Đúng giây thứ 15.0, trước khi token đầu tiên kịp về, Coroutine Watchdog phát hiện `elapsed >= 15000L` nên đã chủ động hủy `activeStreamingJob` và hiển thị UI timeout mạng.

2. **Cái bẫy "Unit Test Xanh" (The Green-Test Trap):**
   - Trong bài unit test `streamWatchdog_whenTokensArriveContinuously`, kỹ sư đã giả lập kịch bản lý tưởng: token bắt đầu bắn về ngay từ mili-giây thứ 40 (`delay(40)`).
   - Test chạy trên JVM vô trùng, không có độ trễ phân loại của Vòng 1, không có độ trễ bắt tay TLS 4G, không có thời gian xếp hàng của nhà cung cấp AI.
   - Test kiểm tra đúng logic máy móc: "có token đều thì không timeout, không có token thì timeout" -> **Test PASS 100%**, nhưng mô hình giả lập hoàn toàn xa rời thực tế vật lý.

3. **Sự mù quáng của hội đồng kiểm thử (Auditor Blindness):**
   - 5 agent kiểm thử chỉ nhìn vào con số "259/259 test xanh" và "code sạch, không leak bộ nhớ", hoàn toàn bỏ qua việc tính toán **Ngân sách độ trễ thực tế (Real-World Latency Budget)**.
   - Đây là sự vi phạm trực tiếp nguyên tắc *End-to-End User Empathy & Pre-Mortem* trong Hiến pháp kỹ thuật: không ai chịu đóng vai người dùng thực tế đang bấm nút trên mạng di động.

---

### 3. Quy tắc kỹ thuật đúc kết (Learned Rules & Invariants)

#### Quy tắc 1: Chống mù quáng vì Test Xanh (Anti-Mock Blindness)
* Bộ unit test đạt 100% trên JVM chỉ chứng minh tính nhất quán về cú pháp của logic được mock—**TUYỆT ĐỐI KHÔNG chứng minh tính khả thi ngoài đời thực**.
* Mọi cơ chế timeout, watchdog, retry, circuit breaker phải được stress-test toán học dựa trên điều kiện xấu nhất ngoài đời thực (Latency Budget):
  $$\text{Latency Budget} = \text{Độ trễ đóng gói} + \text{Độ trễ Vòng 1 LLM} + \text{Bắt tay TLS 4G} + \text{Thời gian xếp hàng TTFB}$$
* Nghiêm cấm việc căn chỉnh các thông số timeout dựa trên các bài test mock có độ trễ nén ảo (`delay(40)`).

#### Quy tắc 2: Bất biến 2 Pha cho Streaming & Watchdog (Two-Phase Streaming Invariant)
Mọi cơ chế Watchdog hoặc phát hiện nghẽn dòng (stream stall detector) **BẮT BUỘC PHẢI TÁCH RẠCH RÒI 2 PHA**:
1. **Pha 1: Thời gian chờ chữ đầu tiên (TTFB / Connection Window):**
   - Phải dành một khoảng thời gian kết nối rộng rãi (**tối thiểu 45–60 giây**, tương thích với cấu hình `readTimeout(60, SECONDS)` của OkHttp) để chờ đóng gói context, gọi Vòng 1 Router và xếp hàng server AI.
2. **Pha 2: Canh chừng đứt nghẽn giữa dòng (In-Flight Stream Stall Watchdog):**
   - Bộ đếm thời gian chết giữa chừng (chuẩn hóa 25 giây) **TUYỆT ĐỐI KHÔNG ĐƯỢC PHÉP BẤM GIỜ** cho đến khi token đầu tiên đã thực sự xuất hiện trên màn hình (`hasReceivedFirstToken == true`).

#### Quy tắc 3: Bắt buộc giải trình Latency Budget trong nghiệm thu (Latency Budget in Handoff)
Mọi báo cáo nghiệm thu hoặc đề xuất thay đổi liên quan đến mạng/concurrency phải giải trình rõ ràng:
- Tổng thời gian hoàn thành dự kiến trong trường hợp xấu nhất ($P_{99}$).
- Ngưỡng timeout cấu hình có lớn hơn ít nhất 1.5 lần so với $P_{99}$ của pha đó hay không.

#### Quy tắc 4: Phân định thông báo lỗi minh bạch (Explicit Error Disambiguation)
* Tuyệt đối không quy chụp mọi lỗi streaming thành "Mất kết nối mạng".
* Phải phân định rõ ràng giữa:
  - Lỗi mạng thực sự (`UnknownHostException`, hoặc `ConnectivityManager` báo ngắt Internet).
  - Lỗi hết hạn mức API (`429 Too Many Requests`, `RateLimitDetector`).
  - Lỗi API Key từ chối / sai cấu hình (`HTTP 401/403`).
  - Lỗi từ phía model/server AI (`HTTP 500/502/503/504`).
  - Lỗi timeout phản hồi quá 90s (`INITIAL_CONNECTION_TIMEOUT`).
  - Lỗi do người dùng chủ động thao tác (hủy bỏ, đóng thẻ).

---

## Sự Cố #002: Lỗi Kẹt Failover Khi Stream & Chụp Mũ Lỗi Mạng Thay Vì Tự Động Chuyển Key Dự Phòng

- **Ngày ghi nhận:** 16/11/2026
- **Mức độ nghiêm trọng:** P1 (Key chính gặp sự cố thì không tự động chuyển sang Key dự phòng khi đang stream, gom mọi lỗi server thành "Mất kết nối mạng")

### 1. Phân tích nguyên nhân gốc rễ
1. **Lỗi kẹt chọn lại Key hỏng trong `StreamingAiClient`:**
   - Khi gọi stream, `exhaustedKeyIds` chỉ ghi nhận key nếu `isRateLimit == true` (HTTP 429).
   - Nếu key chết vì HTTP 401, 404, 500, 503, hoặc SocketTimeout, key đó **không bị đưa vào `exhaustedKeyIds`**.
   - Khi gọi `findNextAvailableKey()`, hệ thống lại bốc trúng đúng cái key vừa chết đó để thử lại, không bao giờ chuyển sang key dự phòng thứ 2 trong luồng stream!
2. **"Chụp mũ" toàn bộ lỗi thành "Mất kết nối mạng":**
   - Hàm `isNetworkException` kiểm tra `curr.message?.contains("timeout")`. Bất kỳ lỗi nào từ server AI như `504 Gateway Timeout` hay OkHttp ReadTimeout đều bị hàm này coi là "Mất mạng".
   - Giao diện `renderStreamTimeoutUi` gán cứng dòng chữ *"Mất kết nối mạng. Chạm để thử lại"*, khiến người dùng lầm tưởng điện thoại rớt mạng trong khi mạng vẫn căng sóng.

### 2. Quy tắc kỹ thuật đúc kết
1. **Toàn diện hóa Failover khi Stream:** Mọi worker thất bại vì bất kỳ ngoại lệ nào (401, 404, 429, 500, 503, Timeout) đều phải lập tức đưa key đó vào `exhaustedKeyIds` cho phiên gọi hiện tại, tự động chuyển ngay sang key dự phòng tiếp theo mà không bắt người dùng can thiệp thủ công.
2. **Xác thực Mạng Vật Lý Thực Sự:** Chỉ được hiển thị *"Mất kết nối mạng"* khi `ConnectivityManager` xác nhận thiết bị mất kết nối Internet hoặc lỗi DNS `UnknownHostException`. Toàn bộ các lỗi khác phải hiển thị đúng bản chất kèm nút thử lại.

---

## Sự Cố #003: Lỗ Hổng Kiến Trúc & Bẫy Trải Nghiệm OEM Trên Chuỗi Commit 16/11

- **Ngày ghi nhận:** 16/11/2026
- **Mức độ nghiêm trọng:** P1 (Rò rỉ tài nguyên, phá vỡ Structured Concurrency, treo vô hạn khi failover và lỗi giao diện trên các dòng máy OEM)
- **Bối cảnh:** Sau các đợt vá lỗi Sự cố #001 và #002, chuỗi 10 commit liên tiếp được đẩy lên để bổ sung Text Shimmer, cơ chế chọn văn bản nổi và tái thiết kế thanh nhập liệu. Việc triển khai thiếu kiểm soát vòng đời và chưa lường hết các ràng buộc thực tế đã dẫn đến chuỗi 7 lỗ hổng nghiêm trọng.

### 1. Hiện tượng & Phân tích nguyên nhân gốc rễ (7 Lỗ hổng)

1. **Rò rỉ ValueAnimator Shimmer Chạy Vĩnh Viễn (`FloatingBubbleService.kt`):**
   - *Hiện tượng:* Sau khi bong bóng đóng lại hoặc trạng thái kết nối kết thúc, CPU và pin của thiết bị tiếp tục bị tiêu hao ngầm.
   - *Nguyên nhân:* `startTextShimmer` gọi `textView.post { ... }` để khởi tạo `ValueAnimator.INFINITE`. Nếu hàm `stopTextShimmer` được gọi trước khi `post` kịp thực thi trên Main Looper, lệnh hủy trong `stopTextShimmer` bị vô hiệu hóa; Runnable trong hàng đợi sau đó vẫn chạy và kích hoạt một Animator vô tận mồ côi (orphaned animator).
2. **Phá vỡ Structured Concurrency do Nuốt `CancellationException` (`FloatingBubbleService.kt`):**
   - *Hiện tượng:* Khi người dùng chủ động đóng bong bóng hoặc hủy yêu cầu, Coroutine cha và các tác vụ con không thể đồng bộ dọn dẹp sạch sẽ, gây treo trạng thái ngầm.
   - *Nguyên nhân:* Trong khối `catch (e: CancellationException)` của `executeAiCall`, ngoại lệ bị giữ lại và không được ném lại khi `!isWatchdogTimedOut.get()`. Việc nuốt `CancellationException` phá vỡ cơ chế lan truyền tín hiệu hủy của Kotlin Coroutines.
3. **Data Race Trên Buffer `accumulatedLive` Chưa Đồng Bộ (`FloatingBubbleService.kt`):**
   - *Hiện tượng:* Khi luồng stream nhận token với tần suất cao hoặc xảy ra chuyển đổi trạng thái mạng đồng thời, ứng dụng có nguy cơ ném ngoại lệ chỉ mục chuỗi hoặc hiển thị nội dung chắp vá, lỗi ký tự.
   - *Nguyên nhân:* Đối tượng `StringBuilder` được đọc/ghi đồng thời từ các luồng callback bất đồng bộ (`onTokenDelta` và `onStatusUpdate`) mà không có khóa đồng bộ hóa (`synchronized`).
4. **Bẫy Treo Vô Hạn Do Reset Timeout 90s Mỗi Lần Nhảy Key (`FloatingBubbleService.kt`):**
   - *Hiện tượng:* Người dùng bị giữ ở trạng thái chờ đợi kéo dài hàng phút (lên tới 4.5 phút nếu có 3 key dự phòng) mà không có thông báo dừng hay phản hồi dứt khoát.
   - *Nguyên nhân:* Cứ mỗi lần nhảy key trong `onStatusUpdate`, mốc thời gian `requestStartedAt` lại bị reset về thời điểm hiện tại. Hệ thống thiếu một mốc thời gian bắt đầu toàn cục duy nhất để giới hạn trần thời gian tối đa cho toàn bộ phiên gửi.
5. **Mất An Toàn Hiển Thị Bộ Nhớ Trên `alarmDbMap` (`FloatingBubbleService.kt`):**
   - *Hiện tượng:* Dữ liệu nhiệm vụ và trạng thái báo thức hiển thị trên giao diện nổi không cập nhật kịp thời, xuất hiện trạng thái cũ/mới chập chờn khi tương tác nhanh.
   - *Nguyên nhân:* Biến `alarmDbMap` được cập nhật từ luồng nền của Flow và đọc/ghi trên UI thread mà không được khai báo `@Volatile`, vi phạm quy tắc ranh giới bộ nhớ (memory barrier).
6. **Áp Lực Bộ Nhớ GC Khi Gõ Phím & Xác Thực Mạng Chưa Đầy Đủ (`FloatingBubbleService.kt`):**
   - *Hiện tượng 6A:* Gõ văn bản trong ô nhập liệu gây giật khung hình nhẹ (frame drop) trên các thiết bị cấu hình trung bình/thấp.
   - *Nguyên nhân 6A:* Trong `TextWatcher`, mỗi ký tự nhập vào đều gọi `updateSendBtnState`, cấp phát mới toàn bộ một `GradientDrawable` dù trạng thái logic (`hasText`) không hề thay đổi.
   - *Hiện tượng 6B:* Thiết bị kết nối vào Wi-Fi không có kết nối ra Internet thực tế (hoặc mạng captive portal) nhưng ứng dụng vẫn cố gửi và báo lỗi sai bản chất.
   - *Nguyên nhân 6B:* Hàm `isPhysicalNetworkOffline` chỉ kiểm tra cờ kết nối tầng liên kết `NET_CAPABILITY_INTERNET`, bỏ qua cờ xác thực WAN thực tế `NET_CAPABILITY_VALIDATED` từ hệ điều hành Android.
7. **Xung Đột Cơ Chế Chọn Văn Bản Trên Overlay OEM & Kẹt Tay Cầm Compose:**
   - *Hiện tượng 7A:* Trên các dòng máy Xiaomi (MIUI/HyperOS), Oppo (ColorOS) và Samsung (OneUI), việc đặt `setTextIsSelectable(true)` trên `EditText` trong cửa sổ nổi (`TYPE_APPLICATION_OVERLAY`) làm bàn phím ảo mất tiêu điểm, văng thanh công cụ hệ thống hoặc gây crash.
   - *Nguyên nhân 7A:* Cửa sổ nổi không có cấu trúc Window Decor tiêu chuẩn của một Activity để chứa ActionMode bar của hệ thống OEM.
   - *Hiện tượng 7B:* Trong `AssistantChatSheet.kt`, khi người dùng đang bôi đen văn bản trong mục "Quá trình suy luận" (Reasoning Accordion) rồi bấm thu gọn, tay cầm bôi đen (selection drag handles) bị kẹt lơ lửng trên màn hình.
   - *Nguyên nhân 7B:* Thành phần `SelectionContainer` bên trong `AnimatedVisibility` không tự động giải phóng vùng chọn đang active khi bị co rút.

---

### 2. Quy Tắc Kỹ Thuật Đúc Kết (7 Bài Học Xương Máu)

#### Quy tắc 1: Bất Biến Vòng Đời Animation & Asynchronous Post Queue
Mọi `ValueAnimator` lặp vô hạn (`INFINITE`) được khởi tạo bên trong callback bất đồng bộ (`post`, `postDelayed`) BẮT BUỘC phải giữ tham chiếu `Runnable` để hủy qua `removeCallbacks`, kết hợp cờ trạng thái (`AtomicBoolean` / `isShimmering`). Tuyệt đối không để animation mồ côi chạy ngầm khi giao diện đã chuyển sang trạng thái dừng.

#### Quy tắc 2: Bất Biến Không Nuốt Tín Hiệu Hủy Coroutine (Structured Concurrency)
Trong mọi khối `try/catch` của Coroutine, ngoại lệ `CancellationException` KHÔNG BAO GIỜ được phép bị nuốt, trừ trường hợp được xử lý đặc biệt và có chủ đích (ví dụ watchdog kích hoạt hủy nội bộ có kiểm soát: `if (!isWatchdogTimedOut.get()) throw e`). Mọi luồng xử lý phải bảo toàn cơ chế lan truyền tín hiệu hủy.

#### Quy tắc 3: Bất Biến Đồng Bộ Hóa Buffer Đa Luồng (Thread-Safe Streaming Buffer)
Mọi bộ đệm chuỗi hoặc cấu trúc dữ liệu tích lũy được chia sẻ qua các callback bất đồng bộ (streaming deltas, status updates) BẮT BUỘC phải được bảo vệ bằng cơ chế đồng bộ hóa (`synchronized(buffer)`) hoặc sử dụng cấu trúc dữ liệu thread-safe.

#### Quy tắc 4: Bất Biến Ngân Sách Thời Gian Toàn Cục (Global Latency Ceiling Invariant)
Toàn bộ một phiên gửi chỉ thị AI qua nhiều tầng failover BẮT BUỘC phải có một mốc thời gian bắt đầu duy nhất (`globalRequestStartedAt`) và bị chặn cứng bởi một trần thời gian tối đa toàn cục (không quá 120 giây). Việc nhảy key dự phòng chỉ được phép sử dụng phần thời gian còn lại trong ngân sách, không bao giờ được phép reset lại đồng hồ đếm ngược vô hạn.

#### Quy tắc 5: Bất Biến Hiển Thị Bộ Nhớ Trong Đa Luồng (Memory Visibility & Volatile State)
Mọi biến trạng thái chia sẻ giữa các luồng/coroutine (đặc biệt là map cache bộ nhớ giữa Flow collector và UI thread) BẮT BUỘC phải được đánh dấu `@Volatile` hoặc bọc trong cấu trúc dữ liệu nguyên tử (`AtomicReference` / `StateFlow`), bảo đảm tính nhất quán bộ nhớ tức thời (happens-before relationship) giữa các lõi CPU.

#### Quy tắc 6: Bất Biến Tiết Kiệm Cấp Phát Giao Diện & Xác Thực Mạng WAN Thực Tế
1. Trong mọi bộ lắng nghe gõ phím (`TextWatcher`), UI chỉ được phép tái cấu hình khi trạng thái logic thực sự thay đổi; các đối tượng đồ họa (`Drawable`, `Paint`, `Color`) phải được cache tái sử dụng, tuyệt đối không cấp phát mới trên từng phím gõ.
2. Để khẳng định thiết bị có Internet thực sự, BẮT BUỘC phải kiểm tra năng lực mạng `NetworkCapabilities.NET_CAPABILITY_VALIDATED` do Android OS kiểm chứng qua WAN probe, loại trừ hoàn toàn bẫy mạng nội bộ không có Internet hoặc captive portal.

#### Quy tắc 7: Bất Biến Tương Thích Cửa Sổ Nổi OEM & Dọn Dẹp Vùng Chọn Compose
1. Trong cửa sổ Overlay (`TYPE_APPLICATION_OVERLAY`), không lạm dụng `setTextIsSelectable(true)` trên trường nhập liệu `EditText`. Cung cấp tương tác thông minh trực tiếp: Nhấn giữ (`onLongClick`) để đọc `ClipboardManager` hệ thống, chèn text tại vị trí con trỏ kèm phản hồi xúc giác (`HapticFeedbackConstants.LONG_PRESS`) và Toast "✓ Đã dán", bảo đảm 100% tương thích mượt mà trên mọi thiết bị Xiaomi, Oppo, Samsung mà không làm ô nhiễm giao diện với các nút bấm thừa thãi.
2. Trong Jetpack Compose, mọi khối `SelectionContainer` nằm trong các thành phần co giãn (`AnimatedVisibility`, Accordion) BẮT BUỘC phải đồng bộ dọn dẹp vùng chọn khi thu gọn để triệt tiêu hoàn toàn hiện tượng tay cầm vùng chọn ma (ghost selection handles) lơ lửng trên màn hình.
