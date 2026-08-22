package com.neubofy.remotemanager.ui.logs

enum class LogLevel(val label: String) {
    ALL("All"),
    ERROR("Error"),
    WARN("Warn"),
    NOTICE("Notice"),
    INFO("Info"),
    DEBUG("Debug"),
    SYSTEM("System")
}

data class LogEntry(
    val id: Long,
    val timestamp: String,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val raw: String
) {
    companion object {
        private var idCounter = 0L

        fun parse(line: String): LogEntry {
            val trimmed = line.trim()
            val id = ++idCounter

            var timestamp = ""
            var level = LogLevel.INFO
            var tag = ""
            var message = trimmed

            if (trimmed.contains("[SYSTEM]")) {
                level = LogLevel.SYSTEM
                val parts = trimmed.split(" - [SYSTEM]", limit = 2)
                if (parts.size == 2) {
                    timestamp = parts[0].trim()
                    message = "[SYSTEM]" + parts[1]
                }
            } else if (trimmed.contains(" ERROR ") || trimmed.contains(" ERROR: ") || trimmed.contains(" ERROR :")) {
                level = LogLevel.ERROR
            } else if (trimmed.contains(" WARN ") || trimmed.contains(" WARNING ") || trimmed.contains(" WARN :") || trimmed.contains(" WARN: ")) {
                level = LogLevel.WARN
            } else if (trimmed.contains(" NOTICE:") || trimmed.contains(" NOTICE :") || trimmed.contains(" NOTICE ")) {
                level = LogLevel.NOTICE
            } else if (trimmed.contains(" DEBUG ") || trimmed.contains(" DEBUG :") || trimmed.contains(" DEBUG: ")) {
                level = LogLevel.DEBUG
            } else if (trimmed.contains(" INFO ") || trimmed.contains(" INFO :") || trimmed.contains(" INFO: ")) {
                level = LogLevel.INFO
            }

            if (trimmed.length >= 19 && (trimmed[4] == '-' || trimmed[4] == '/') && (trimmed[7] == '-' || trimmed[7] == '/')) {
                val timeEndIdx = if (trimmed.length >= 23 && trimmed[19] == '.') 23 else 19
                timestamp = trimmed.substring(0, timeEndIdx).trim()
                val remainder = trimmed.substring(timeEndIdx).trimStart(' ', '-', ':')
                message = remainder
            }

            return LogEntry(
                id = id,
                timestamp = timestamp,
                level = level,
                tag = tag,
                message = message,
                raw = trimmed
            )
        }
    }
}
