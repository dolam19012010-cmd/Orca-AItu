package com.orca.orcaauto.domain.filter

import androidx.compose.runtime.Immutable

@Immutable
enum class TaskStatus(val displayName: String, val badgeColor: String) {
    TODO("Phải làm", "#EF4444"),
    DONE("Đã làm", "#10B981"),
    PLANNED("Kế hoạch", "#F59E0B")
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

object SummaryParser {

    fun cleanMarkdown(text: String): String {
        return text
            .replace(Regex("(?i)</?(answer|response|summary|output|think|thought)>"), "")
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
        s = s.replace(Regex("^(?i)(lời nhắn & điểm lưu ý|lời nhắn nhẹ nhàng|lời nhắn|lời khuyên|lưu ý tinh tế|điểm cần chú ý|tóm tắt trọng tâm|ghi chú nhanh|lưu ý nhanh|chú ý|tóm tắt)[:\\-–\\s]*"), "")
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
        // 1. Strip reasoning blocks from DeepSeek R1 / Gemini Thinking and markdown fences
        val cleaned = rawText
            .replace(Regex("(?s)<think>.*?</think>"), "")
            .replace(Regex("(?s)<thought>.*?</thought>"), "")
            .replace(Regex("(?i)</?(answer|response|summary|output)>"), "")
            .replace(Regex("(?m)^```[a-zA-Z]*\\s*$"), "")
            .replace(Regex("(?m)^```\\s*$"), "")
            .replace(Regex("(?i)^(dưới đây là|here is the|bản tóm tắt|đây là bản tóm tắt).*?:\\s*\\n+"), "")
            .trim()

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
                !isBullet && (lower.contains("phần 1") || (lower.contains("góc nhìn") && lower.contains("phản biện")) || lower.startsWith("=== phần 1") || lower.startsWith("### phần 1")) -> {
                    currentSection = 1
                    continue
                }
                lower.contains("nhiệm vụ phải làm") || (lower.contains("to-do") && !isBullet) -> {
                    currentSection = 2
                    continue
                }
                lower.contains("nhiệm vụ đã làm") || (lower.contains("done") && !isBullet) -> {
                    currentSection = 3
                    continue
                }
                lower.contains("kế hoạch dự kiến") || (lower.contains("planned") && !isBullet) -> {
                    currentSection = 4
                    continue
                }
                !isBullet && (lower.contains("phần 2") || lower.contains("bảng điều phối") || lower.contains("nhiệm vụ điều phối")) -> {
                    currentSection = 2
                    continue
                }
            }

            // Section 1 parsing: Advice / Gentle message vs Notes / Questions
            if (currentSection == 1 || currentSection == 0) {
                if (lower.contains("lời khuyên") || lower.contains("lời nhắn") || lower.contains("tóm tắt") || lower.contains("ghi chú") || lower.contains("[lời")) {
                    advice = cleanAdviceText(trimmed)
                    currentSection = 1
                    continue
                } else if (!isBullet && (lower.contains("chất vấn") || lower.contains("hoài nghi") || lower.contains("mâu thuẫn") || lower.contains("lưu ý") || lower.contains("chú ý") || lower.contains("điểm cần"))) {
                    currentSection = 1
                    // Marker for questions / notes
                } else if (isBullet) {
                    val questionText = trimmed.replace(Regex("^[\\-•*?\\d.]\\s*"), "").trim()
                    val cleanedQ = cleanMarkdown(questionText)
                    val lowerQ = cleanedQ.lowercase()
                    // Filter out stray header lines like ". Lưu Ý Tinh Tế:", "Lưu Ý Tinh Tế", "Điểm Cần Chú Ý", etc.
                    val isStrayHeader = lowerQ.contains("lưu ý tinh tế") ||
                                        lowerQ.contains("điểm cần chú ý") ||
                                        lowerQ.contains("lưu ý nhanh") ||
                                        lowerQ.contains("chất vấn & điểm nghi vấn") ||
                                        lowerQ.startsWith(". lưu ý") ||
                                        lowerQ.startsWith("lưu ý:") ||
                                        lowerQ.startsWith("chú ý:") ||
                                        (lowerQ.length < 25 && lowerQ.endsWith(":"))

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
                } else if (trimmed.startsWith("Hạn:") || trimmed.startsWith("Hạn chót:") || trimmed.startsWith("Nguồn:") || trimmed.startsWith("Thời gian:")) {
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
                            if (lowerPart.startsWith("nguồn:")) s = cleanMarkdown(part.trim().substringAfter(":"))
                            if (lowerPart.startsWith("hạn:") || lowerPart.startsWith("hạn chót:") || lowerPart.startsWith("thời gian:")) {
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
                lower.startsWith("nguồn:") -> source = cleanMarkdown(part.substringAfter(":"))
                lower.startsWith("hạn chót:") || lower.startsWith("hạn:") || lower.startsWith("thời gian:") || lower.startsWith("mục tiêu:") -> {
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
