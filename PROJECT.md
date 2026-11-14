# Project: OrcaAuto Unified Active Session Engine & Zero Task Loss

## Architecture
OrcaAuto is an Android automation and smart assistant app. The assistant stack consists of:
- **Presentation Layer**: `FloatingBubbleService.kt` (floating overlay, bubble card, task rows, streaming text display).
- **Domain Pipeline**: `AssistantPipeline.kt` (multi-stage assistant pipeline, session context retrieval, intent routing, tool actions).
- **Retrieval Engine**: `LocalRetriever.kt` (Round 1 & Round 2 prompt construction, Room DB context aggregation: active alarms, previous shift summaries, notes catalog, 24h messages).
- **Remote AI Client**: `StreamingAiClient.kt` (Gemini, OpenAI, OpenRouter SSE streaming client).
- **Data Layer**: Room DB (`AppDatabase`) with `SmartAlarmDao`, `SummaryDao`, `SharedNoteDao`, `NotificationDao`.

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | R1: Unified Active Session Engine | Prompt Vòng 2 in LocalRetriever nạp đầy đủ: tin nhắn 24h của phiên, activeAlarms từ smart_alarms, previousSummary từ summary_history, chỉ dẫn người dùng, mục lục smart_notes. AssistantPipeline nạp sessionId và smart_alarms. | M1 | ORIGINAL_REQUEST §R1 |
| 2 | R3: Task vs Note Disambiguation | Prompt trong LocalRetriever phân định rõ Nhiệm Vụ (Bảng Nhiệm Vụ) vs Kho Ghi Chú. Khi sửa/xóa nhiệm vụ, AI không tìm trong ghi chú, không đòi ID ghi chú, hướng dẫn thao tác nhanh. | M1 | ORIGINAL_REQUEST §R3 |
| 3 | R2: Zero Task Loss in Floating Bubble | Khi AI trả lời câu chat thông thường (AssistantStep.COMPLETED), Bảng Nhiệm Vụ không bị xóa. expandFloatingCard luôn bảo toàn và hiển thị danh sách nhiệm vụ từ Room DB smart_alarms khi mở lại bóng chat. | M2 | ORIGINAL_REQUEST §R2 |
| 4 | R4: System Prompt Prepending & AI Connection Status | StreamingAiClient tự động gộp System Prompt vào đầu User Prompt cho OpenAI/OpenRouter. FloatingBubbleService hiển thị `⏳ Đang kết nối máy chủ AI...` khi chờ token đầu từ OpenRouter. | M2 | ORIGINAL_REQUEST §R4 |
| 5 | R5: Unit Tests, Verification, APK Build & Git Push | Bổ sung unit tests (LocalRetrieverTest, FloatingBubbleServiceEmpiricalChallengeTest, StreamingAiClientTest). Chạy gradlew testDebugUnitTest đạt 100% PASS. Đóng gói assembleDebug ra app-debug.apk. Tự động commit và push git lên main. | M3 | ORIGINAL_REQUEST §R5 |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | M1: Backend Engine & Prompt Disambiguation | `LocalRetriever.kt`, `AssistantPipeline.kt`, `LocalRetrieverTest.kt` | Survey | PLANNED |
| 2 | M2: UI Preservation & Streaming Enhancements | `FloatingBubbleService.kt`, `StreamingAiClient.kt`, `FloatingBubbleServiceEmpiricalChallengeTest.kt`, `StreamingAiClientTest.kt` | M1 | PLANNED |
| 3 | M3: Full Verification, APK Build & Git Push | Full `testDebugUnitTest` (100% pass), `assembleDebug` (`app-debug.apk`), git commit & push | M1, M2 | PLANNED |

## Interface Contracts
### `LocalRetriever.kt`
- Signature:
  ```kotlin
  suspend fun retrieve(
      userPrompt: String,
      decision: Round1Decision,
      excludedNotificationIds: Set<Long> = emptySet(),
      persona: PromptPersona = PromptPersona.GENTLE_ASSISTANT,
      activeAlarms: List<SmartAlarmEntity> = emptyList(),
      previousSummary: String? = null,
      sessionInfo: String? = null,
      notesCatalog: List<SharedNoteEntity> = emptyList()
  ): RetrievedContext
  ```
- Defaults ensure backward compatibility with all existing tests.
- System instruction contains explicit rules:
  1. Distinguish between Tasks in Shift Task Table and Notes in Notes Catalog.
  2. For task reschedule/cancel: guide user "nhấn giữ thẻ để chỉnh sửa, vuốt phải hoàn thành, vuốt trái hủy", never ask for Note ID.
  3. Include Active Alarms, Previous Summary, Session Info, and Notes Catalog in Round 2 prompt.

### `FloatingBubbleService.kt`
- In `AssistantStep.COMPLETED`:
  - If `allTasks.isNotEmpty()`: update `latestSummaryText` and render new tasks.
  - If `allTasks.isEmpty()`: do NOT overwrite `latestSummaryText` with casual chat. Keep active tasks displayed from `alarmDbMap` in `targetTasksContainer`.
- In `expandFloatingCard`:
  - Synthesize and render active alarms from Room DB `alarmDbMap` if parsed tasks from `latestSummaryText` are empty.
- In `AssistantStep.STAGE_4_STREAMING_ANSWER`:
  - If `uiState.streamedContentClean.isBlank()`: show `"⏳ Đang kết nối máy chủ AI..."`.

### `StreamingAiClient.kt`
- In `buildOpenAiCompatibleRequest`:
  - Prepend `systemInstruction` to `prompt` into a single `user` message: `"$sysPrompt\n\n$prompt"`.

## Code Layout
- `app/src/main/java/com/orca/orcaauto/domain/assistant/LocalRetriever.kt`
- `app/src/main/java/com/orca/orcaauto/domain/assistant/AssistantPipeline.kt`
- `app/src/main/java/com/orca/orcaauto/service/FloatingBubbleService.kt`
- `app/src/main/java/com/orca/orcaauto/data/remote/StreamingAiClient.kt`
- `app/src/test/java/com/orca/orcaauto/domain/assistant/LocalRetrieverTest.kt`
- `app/src/test/java/com/orca/orcaauto/domain/assistant/AssistantPipelineTest.kt`
- `app/src/test/java/com/orca/orcaauto/service/FloatingBubbleServiceEmpiricalChallengeTest.kt`
- `app/src/test/java/com/orca/orcaauto/data/remote/StreamingAiClientTest.kt`
