package nobility.downloader.utils

import java.io.PrintStream

class ErrorStreamFilter(
    private val original: PrintStream
) : PrintStream(nullOutputStream()) {

    private val buffer = StringBuilder()
    private var skipping = false

    override fun write(b: Int) {
        processChar(b.toChar())
    }

    override fun write(buf: ByteArray, off: Int, len: Int) {
        for (i in off until (off + len)) {
            processChar(buf[i].toInt().toChar())
        }
    }

    private fun processChar(c: Char) {
        buffer.append(c)

        if (c == '\n') {
            val line = buffer.toString()
            buffer.clear()
            processLine(line)
        }
    }

    private fun processLine(line: String) {
        val isStartOfSeleniumBlock =
            line.contains("org.openqa.selenium.remote.http.WebSocket") ||
                    line.contains("Connection reset") ||
                    line.contains("SocketException")

        if (isStartOfSeleniumBlock) {
            skipping = true
            return
        }

        if (skipping) {

            val trimmed = line.trim()

            if (trimmed.startsWith("at ")) {
                return
            }

            if (trimmed.isEmpty() || !trimmed.startsWith("at ")) {
                skipping = false
            }
        }

        if (!skipping) {
            original.print(line)
        }
    }
}
