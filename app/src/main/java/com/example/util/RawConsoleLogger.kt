package com.example.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val timestamp: String,
    val level: String, // "INFO", "WARN", "ERROR", "HTTP", "CONSOLE", "SYSTEM"
    val tag: String,
    val message: String
)

object RawConsoleLogger {
    private val _logs = MutableStateFlow<List<LogEntry>>(
        listOf(
            LogEntry(
                timestamp = currentTime(),
                level = "SYSTEM",
                tag = "AgendaPlus",
                message = "Agenda+ Raw CLI Error Console Active [Package: com.aistudio.agendaplus.mkrvpx]"
            )
        )
    )
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private fun currentTime(): String {
        return SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
    }

    fun log(level: String, tag: String, message: String) {
        val entry = LogEntry(
            timestamp = currentTime(),
            level = level.uppercase(),
            tag = tag,
            message = message
        )
        _logs.update { it + entry }
    }

    fun info(tag: String, message: String) = log("INFO", tag, message)
    fun warn(tag: String, message: String) = log("WARN", tag, message)
    fun error(tag: String, message: String) = log("ERROR", tag, message)
    fun http(tag: String, message: String) = log("HTTP", tag, message)
    fun console(tag: String, message: String) = log("CONSOLE", tag, message)

    fun clear() {
        _logs.update {
            listOf(
                LogEntry(
                    timestamp = currentTime(),
                    level = "SYSTEM",
                    tag = "AgendaPlus",
                    message = "Console gewist. Wachten op nieuwe events..."
                )
            )
        }
    }

    fun getRawLogString(): String {
        return _logs.value.joinToString(separator = "\n") { entry ->
            "[${entry.timestamp}] [${entry.level}] <${entry.tag}> ${entry.message}"
        }
    }
}
