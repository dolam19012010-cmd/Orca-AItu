package com.orca.orcaauto.domain.filter

import androidx.compose.runtime.Immutable

@Immutable
enum class TaskStatus(val displayName: String, val badgeColor: String) {
    TODO("Phải làm", "#EF4444"),
    DONE("Đã làm", "#10B981"),
    PLANNED("Kế hoạch", "#F59E0B");

    fun getDisplayName(language: com.orca.orcaauto.ui.locale.AppLanguage): String = when (language) {
        com.orca.orcaauto.ui.locale.AppLanguage.VIETNAMESE -> displayName
        com.orca.orcaauto.ui.locale.AppLanguage.ENGLISH -> when (this) {
            TODO -> "To-Do"
            DONE -> "Done"
            PLANNED -> "Planned"
        }
    }
}

@Immutable
data class TaskItem(
    val title: String,
    val source: String = "",
    val deadline: String = "",
    val status: TaskStatus = TaskStatus.TODO
)

@Immutable
data class ParsedSummary(
    val rawCleanText: String,
    val reasoningNotes: String? = null,
    // Phần 1: Phản biện & Lời khuyên
    val conciseAdvice: String,
    val criticalQuestions: List<String>,
    // Phần 2: Bảng nhiệm vụ 3 trạng thái
    val tasksToDo: List<TaskItem>,
    val tasksDone: List<TaskItem>,
    val tasksPlanned: List<TaskItem>
) {
    val totalTaskCount: Int
        get() = tasksToDo.size + tasksDone.size + tasksPlanned.size
}

object ScratchpadExtractor {

    private val SCRATCHPAD_REGEX = Regex(
        pattern = """<scratchpad>([\s\S]*?)(?:</scratchpad>|$)""",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    private val NATIVE_THINK_REGEX = Regex(
        pattern = """<(?:think|thought|reasoning)>([\s\S]*?)(?:</(?:think|thought|reasoning)>|$)""",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    fun extractReasoning(rawText: String): String? {
        val scratchMatch = SCRATCHPAD_REGEX.find(rawText)
        val scratchContent = scratchMatch?.groups?.get(1)?.value?.trim()

        val thinkMatch = NATIVE_THINK_REGEX.find(rawText)
        val thinkContent = thinkMatch?.groups?.get(1)?.value?.trim()

        return when {
            !scratchContent.isNullOrBlank() && !thinkContent.isNullOrBlank() -> "$scratchContent\n$thinkContent"
            !scratchContent.isNullOrBlank() -> scratchContent
            !thinkContent.isNullOrBlank() -> thinkContent
            else -> null
        }
    }

    fun stripReasoning(rawText: String): String {
        return rawText
            .replace(SCRATCHPAD_REGEX, "")
            .replace(NATIVE_THINK_REGEX, "")
            .replace(Regex("(?i)</?(answer|response|summary|output|scratchpad|think|thought|reasoning)>"), "")
            .replace(Regex("(?m)^```[a-zA-Z]*\\s*$"), "")
            .replace(Regex("(?m)^```\\s*$"), "")
            .replace(Regex("(?i)^(dưới đây là|here is the|bản tóm tắt|đây là bản tóm tắt).*?:\\s*\\n+"), "")
            .trim()
    }
}

object SummaryParser {

    fun cleanMarkdown(text: String): String {
        return text
            .replace(Regex("(?i)</?(answer|response|summary|output|think|thought|scratchpad|reasoning)>"), "")
            .replace(Regex("\\*\\*"), "")
            .replace(Regex("__"), "")
            .replace(Regex("`"), "")
            // Only clean question marks if they were used as bullets or stray formatting with leading space
            .replace(Regex("^\\?\\s*"), "")
            .replace(Regex("(?<=\\s)\\?\\s*"), "")
            .replace(Regex("\\[(.*?)\\]"), "$1")
            .trim()
    }

    /**
     * Cleans advice/greeting text by stripping persona tags (e.g. "[Nhẹ Nhàng]:", "Nhẹ Nhàng]:")
     * and boilerplate label prefixes like "Lời nhắn:", "Lời khuyên:".
     */
    fun cleanAdviceText(text: String): String {
        var s = text.trim()
        s = cleanMarkdown(s)
        // Strip bracketed headers e.g. [Nhẹ Nhàng], [Lời Nhắn], [Persona]
        s = s.replace(Regex("^\\[[^\\]]*\\]:?\\s*"), "")
        s = s.replace(Regex("^\\([^\\)]*\\):?\\s*"), "")
        // Strip trailing bracket persona leftovers e.g. "Nhẹ Nhàng]:", "Cố Vấn]:"
        s = s.replace(Regex("^(?i)[a-zA-ZÀ-ỹ\\s\\-&]+[\\]\\)]:?\\s*"), "")
        // Strip label prefixes
        s = s.replace(Regex("^(?i)(lời nhắn & điểm lưu ý|lời nhắn nhẹ nhàng|lời nhắn|lời khuyên|lưu ý tinh tế|điểm cần chú ý|tóm tắt trọng tâm|ghi chú nhanh|lưu ý nhanh|chú ý|tóm tắt|gentle message|core summary|strategic advice|quick note|subtle notes|points of attention|critical questions|urgent notice|advice|note|notes|summary)[:\\-–\\s]*"), "")
        // Strip leading symbols
        s = s.replace(Regex("^[\\-•*#:>\\s]+"), "").trim()
        return s
    }

    /**
     * Parses the AI response into 2 distinct executive sections:
     * Section 1: Critical Thinking, Doubts, Contradictions, and Concise Advice.
     * Section 2: Task Dispatch Board (To-Do, Done, Planned) with clear deadlines.
     */
    fun parse(rawText: String): ParsedSummary {
        val reasoning = ScratchpadExtractor.extractReasoning(rawText)
        val cleaned = ScratchpadExtractor.stripReasoning(rawText)

        var advice = ""
        val criticalQuestions = mutableListOf<String>()
        val tasksToDo = mutableListOf<TaskItem>()
        val tasksDone = mutableListOf<TaskItem>()
        val tasksPlanned = mutableListOf<TaskItem>()

        var currentSection = 0 // 1: Advice/Questions, 2: To-Do, 3: Done, 4: Planned

        val lines = cleaned.lines()
        for (line in lines) {
            val trimmed = line.trim()
            val lower = trimmed.lowercase()
            if (trimmed.isBlank()) continue

            val isBullet = trimmed.startsWith("-") || trimmed.startsWith("•") || trimmed.startsWith("*") || trimmed.startsWith("?") || trimmed.matches(Regex("^\\d+\\..*"))

            // Section Heading checks (Must not be a bullet item)
            when {
                !isBullet && (lower.contains("phần 1") || lower.contains("part 1") || lower.contains("section 1") || (lower.contains("góc nhìn") && lower.contains("phản biện")) || (lower.contains("advice") && lower.contains("note")) || lower.startsWith("=== phần 1") || lower.startsWith("### phần 1") || lower.startsWith("=== part 1") || lower.startsWith("### part 1")) -> {
                    currentSection = 1
                    continue
                }
                lower.contains("nhiệm vụ phải làm") || (lower.contains("to-do") && !isBullet) || (lower.contains("todo") && !isBullet) -> {
                    currentSection = 2
                    continue
                }
                lower.contains("nhiệm vụ đã làm") || (lower.contains("done") && !isBullet) || (lower.contains("completed") && !isBullet) -> {
                    currentSection = 3
                    continue
                }
                lower.contains("kế hoạch dự kiến") || (lower.contains("planned") && !isBullet) || (lower.contains("upcoming") && !isBullet) -> {
                    currentSection = 4
                    continue
                }
                !isBullet && (lower.contains("phần 2") || lower.contains("part 2") || lower.contains("bảng điều phối") || lower.contains("task dispatch board") || lower.contains("nhiệm vụ điều phối")) -> {
                    currentSection = 2
                    continue
                }
            }

            // Section 1 parsing: Advice / Gentle message vs Notes / Questions
            if (currentSection == 1 || currentSection == 0) {
                val isNoteOrQuestion = lower.contains("chất vấn") || lower.contains("hoài nghi") || lower.contains("mâu thuẫn") || lower.contains("lưu ý") || lower.contains("chú ý") || lower.contains("điểm cần") || lower.contains("questions") || lower.contains("doubts") || lower.contains("points of attention") || lower.contains("subtle notes") || lower.contains("urgent notice")

                if (!isNoteOrQuestion && (lower.contains("lời khuyên") || lower.contains("lời nhắn") || lower.contains("tóm tắt") || lower.contains("ghi chú") || lower.contains("[lời") || lower.contains("advice") || lower.contains("message") || lower.contains("core summary") || lower.contains("quick note"))) {
                    advice = cleanAdviceText(trimmed)
                    currentSection = 1
                    continue
                } else if (!isBullet && isNoteOrQuestion) {
                    currentSection = 1
                    // Marker for questions / notes
                } else if (isBullet) {
                    val questionText = trimmed.replace(Regex("^[\\-•*?\\d.]+\\s*"), "").trim()
                    val cleanedQ = cleanAdviceText(questionText)
                    val lowerQ = cleanedQ.lowercase()
                    // Filter out stray header lines like ". Lưu Ý Tinh Tế:", "Lưu Ý Tinh Tế", "Điểm Cần Chú Ý", etc.
                    val isStrayHeader = (lowerQ.length < 35 && (lowerQ.contains("lưu ý tinh tế") ||
                                        lowerQ.contains("điểm cần chú ý") ||
                                        lowerQ.contains("lưu ý nhanh") ||
                                        lowerQ.contains("chất vấn & điểm nghi vấn") ||
                                        lowerQ.contains("subtle notes") ||
                                        lowerQ.contains("points of attention") ||
                                        lowerQ.contains("critical questions") ||
                                        lowerQ.contains("urgent notice") ||
                                        lowerQ.endsWith(":"))) ||
                                        lowerQ.startsWith(". lưu ý") ||
                                        lowerQ.startsWith("lưu ý:") ||
                                        lowerQ.startsWith("chú ý:") ||
                                        lowerQ.startsWith("note:") ||
                                        lowerQ.startsWith("notes:")

                    if (!isStrayHeader && cleanedQ.isNotBlank()) {
                        criticalQuestions.add(cleanedQ)
                        currentSection = 1
                    }
                }
            }

            // Section 2, 3, 4: Tasks parsing
            if (currentSection in 2..4) {
                if (trimmed.startsWith("-") || trimmed.startsWith("•") || trimmed.startsWith("*") || trimmed.startsWith("[") || trimmed.matches(Regex("^\\d+\\..*"))) {
                    val taskItem = parseTaskLine(trimmed, when (currentSection) {
                        2 -> TaskStatus.TODO
                        3 -> TaskStatus.DONE
                        else -> TaskStatus.PLANNED
                    })
                    if (taskItem != null) {
                        when (currentSection) {
                            2 -> tasksToDo.add(taskItem)
                            3 -> tasksDone.add(taskItem)
                            4 -> tasksPlanned.add(taskItem)
                        }
                    }
                } else if (trimmed.startsWith("Hạn:") || trimmed.startsWith("Hạn chót:") || trimmed.startsWith("Nguồn:") || trimmed.startsWith("Thời gian:") || trimmed.startsWith("Due:") || trimmed.startsWith("Deadline:") || trimmed.startsWith("Source:") || trimmed.startsWith("Time:") || trimmed.startsWith("Target:") || trimmed.startsWith("Goal:")) {
                    // This was a sub-line attached to the previous task
                    val targetList = when (currentSection) {
                        2 -> tasksToDo
                        3 -> tasksDone
                        else -> tasksPlanned
                    }
                    val lastTask = targetList.lastOrNull()
                    if (lastTask != null) {
                        targetList.removeAt(targetList.size - 1)
                        var s = lastTask.source
                        var d = lastTask.deadline
                        val subParts = trimmed.split("•", "|")
                        for (part in subParts) {
                            val lowerPart = part.trim().lowercase()
                            if (lowerPart.startsWith("nguồn:") || lowerPart.startsWith("source:") || lowerPart.startsWith("from:")) s = cleanMarkdown(part.trim().substringAfter(":"))
                            if (lowerPart.startsWith("hạn:") || lowerPart.startsWith("hạn chót:") || lowerPart.startsWith("thời gian:") || lowerPart.startsWith("due:") || lowerPart.startsWith("deadline:") || lowerPart.startsWith("time:") || lowerPart.startsWith("target:") || lowerPart.startsWith("goal:")) {
                                d = cleanMarkdown(part.trim().substringAfter(":"))
                            }
                        }
                        targetList.add(lastTask.copy(source = s, deadline = d))
                    }
                }
            }
        }

        // Fallback advice if not cleanly separated by headers
        if (advice.isBlank()) {
            advice = if (criticalQuestions.isNotEmpty()) {
                criticalQuestions.first()
            } else {
                cleaned.lines().firstOrNull { it.isNotBlank() }?.let { cleanMarkdown(it) }.orEmpty()
            }
        }

        // Deduplicate tasks to avoid repeated items
        val distinctToDo = tasksToDo.distinctBy { it.title.lowercase().trim() }
        val distinctDone = tasksDone.distinctBy { it.title.lowercase().trim() }
        val distinctPlanned = tasksPlanned.distinctBy { it.title.lowercase().trim() }

        return ParsedSummary(
            rawCleanText = cleaned,
            reasoningNotes = reasoning,
            conciseAdvice = advice,
            criticalQuestions = criticalQuestions.distinct(),
            tasksToDo = distinctToDo,
            tasksDone = distinctDone,
            tasksPlanned = distinctPlanned
        )
    }

    private fun parseTaskLine(line: String, status: TaskStatus): TaskItem? {
        if (line.isBlank()) return null

        // Format expected: [Title] | Nguồn: [Source] | Hạn chót: [Deadline]
        val parts = line.split("|").map { it.trim() }
        var rawTitle = parts.getOrNull(0) ?: line
        // Strip bullet / number prefix: "- ", "* ", "1. ", etc.
        rawTitle = rawTitle.replace(Regex("^[\\-•*\\d.]+\\s*"), "")
        // Strip markdown checkbox: "[ ] ", "[x] ", "[X] ", etc.
        rawTitle = rawTitle.replace(Regex("^\\[[ xX]?\\]\\s*"), "")
        rawTitle = rawTitle.trim()
        val title = cleanMarkdown(rawTitle)
        if (title.isBlank()) return null

        var source = ""
        var deadline = ""

        for (i in 1 until parts.size) {
            val part = parts[i]
            val lower = part.lowercase()
            when {
                lower.startsWith("nguồn:") || lower.startsWith("source:") || lower.startsWith("from:") -> source = cleanMarkdown(part.substringAfter(":"))
                lower.startsWith("hạn chót:") || lower.startsWith("hạn:") || lower.startsWith("thời gian:") || lower.startsWith("mục tiêu:") ||
                lower.startsWith("due:") || lower.startsWith("deadline:") || lower.startsWith("time:") || lower.startsWith("target:") || lower.startsWith("goal:") -> {
                    deadline = cleanMarkdown(part.substringAfter(":"))
                }
                source.isBlank() -> source = cleanMarkdown(part)
                deadline.isBlank() -> deadline = cleanMarkdown(part)
            }
        }

        return TaskItem(
            title = title,
            source = source,
            deadline = deadline,
            status = status
        )
    }
}
