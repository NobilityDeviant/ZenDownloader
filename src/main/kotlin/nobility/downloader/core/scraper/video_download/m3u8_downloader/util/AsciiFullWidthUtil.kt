package nobility.downloader.core.scraper.video_download.m3u8_downloader.util

/**
 * FW: full-width
 * HF: half-width
 *
 *
 * ASCII space 32 => 12288
 * ASCII [33,126] => unicode [65281,65374]
 */
object AsciiFullWidthUtil {

    private const val HW_SPACE = 32.toChar()

    private const val ASCII_END = 126.toChar()

    private const val ASCII_START = 33.toChar()

    const val FW_SPACE: Char = 12288.toChar()

    private const val HW2FW_STEP = 65248.toChar()

    private const val UNICODE_END = 65374.toChar()

    private const val UNICODE_START = 65281.toChar()

    fun fw2hw(ch: Char): Char {
        if (ch == FW_SPACE) {
            return HW_SPACE
        }

        if (ch >= UNICODE_START && ch <= UNICODE_END) {
            return (ch.code - HW2FW_STEP.code).toChar()
        }

        return ch
    }

    fun fw2hw(str: String?): String? {
        if (str == null) {
            return null
        }
        val c = str.toCharArray()
        for (i in c.indices) {
            c[i] = fw2hw(c[i])
        }
        return String(c)
    }

    fun hw2fw(ch: Char): Char {
        if (ch == HW_SPACE) {
            return FW_SPACE
        }
        if (ch >= ASCII_START && ch <= ASCII_END) {
            return (ch.code + HW2FW_STEP.code).toChar()
        }
        return ch
    }

    fun hw2fw(src: String?): String? {
        if (src == null) {
            return null
        }

        val c = src.toCharArray()
        for (i in c.indices) {
            c[i] = hw2fw(c[i])
        }

        return String(c)
    }

    fun isAsciiPrintable(ch: Char): Boolean {
        return ch.code >= 32 && ch.code < 127
    }
}
