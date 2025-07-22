package nobility.downloader.ui.components.console

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import nobility.downloader.Page
import nobility.downloader.core.Core
import java.util.*

class ConsoleState(
    private val errorMode: Boolean
) {

    private val lines = mutableStateListOf<String>()
    val consoleText get() = lines.joinToString("\n")
    var consolePoppedOut by mutableStateOf(false)

    private val sb = StringBuilder()
    var unreadErrors by mutableStateOf(false)

    var lineCount = 0
        private set
    private val maxLines = 1000
    private val maxLineChars = 300

    fun appendChar(c: Char) {
        if (c == '\r') {
            return
        }
        if (c == '\n') {
            flushLine()
        } else {
            sb.append(c)
        }
        if (errorMode) {
            if (Core.Companion.currentPage != Page.ERROR_CONSOLE && !consolePoppedOut) {
                unreadErrors = true
            }
        }
    }

    private fun flushLine() {

        if (sb.isEmpty()) {
            return
        }

        val rawLine = sb.toString()
        sb.clear()

        val shouldFilter = filters.any { filter ->
            rawLine.contains(filter, ignoreCase = true)
        }

        if (shouldFilter) {
            return
        }

        val prefix = if (errorMode) "" else "[${time()}] "
        rawLine.chunked(maxLineChars).forEachIndexed { index, chunk ->
            val line = if (index == 0) "$prefix$chunk" else "→ $chunk"
            lines += line
            lineCount++
            if (lineCount > maxLines) {
                lines.removeFirst()
                lineCount--
            }
        }
    }

    fun clear() {
        lines.clear()
        lineCount = 0
        sb.clear()
        unreadErrors = false
    }

    private fun time(): String {
        val now = Calendar.getInstance()
        val h = now[Calendar.HOUR_OF_DAY].toString().padStart(2, '0')
        val m = now[Calendar.MINUTE].toString().padStart(2, '0')
        val s = now[Calendar.SECOND].toString().padStart(2, '0')
        return "$h:$m:$s"
    }

    companion object {
        private val filters = listOf<String>(
            "Hint: use closeThreadResources() to avoid finalizing recycled transactions"
        )
    }
}
