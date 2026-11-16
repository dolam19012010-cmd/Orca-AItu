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
  - Lỗi mạng thực sự (`UnknownHostException`, `SocketTimeoutException`, `ConnectException`).
  - Lỗi hết hạn mức API (`429 Too Many Requests`, `RateLimitDetector`).
  - Lỗi từ phía model AI (`500/503`, JSON malformed).
  - Lỗi do người dùng chủ động thao tác (hủy bỏ, đóng thẻ).
