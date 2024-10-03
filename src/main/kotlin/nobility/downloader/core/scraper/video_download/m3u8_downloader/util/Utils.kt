package nobility.downloader.core.scraper.video_download.m3u8_downloader.util

import org.apache.commons.lang3.StringUtils
import java.io.File
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import java.util.function.Function
import kotlin.experimental.and

object Utils {

    @JvmField
    val EMPTY_BIN: ByteBuffer = ByteBuffer.wrap(ByteArray(0))

    private const val SLASH = '/'

    private const val BACKSLASH = '\\'

    private val SPECIAL_SUFFIX = arrayOf<CharSequence>("tar.bz2", "tar.Z", "tar.gz", "tar.xz")

    private fun isFileSeparator(c: Char): Boolean {
        return SLASH == c || BACKSLASH == c
    }

    fun mainName(fileName: String): String {
        var len = fileName.length
        if (0 == len) {
            return fileName
        }
        for (specialSuffix in SPECIAL_SUFFIX) {
            if (fileName.endsWith(".$specialSuffix")) {
                return fileName.substring(0, len - specialSuffix.length - 1)
            }
        }
        if (isFileSeparator(fileName[len - 1])) {
            len--
        }
        var c: Char
        var begin = 0
        var end = len
        for (i in len - 1 downTo 0) {
            c = fileName[i]
            if (len == end && '.' == c) {
                end = i
            }
            if (isFileSeparator(c)) {
                begin = i + 1
                break
            }
        }
        return fileName.substring(begin, end)
    }

    fun secondsFormat(seconds: Long): String {
        if (seconds <= 0) {
            return "0sec"
        }

        val hours = seconds / 3600
        val minutes = ((seconds % 3600) / 60).toInt()
        val secs = (seconds % 60).toInt()

        val buf = StringBuilder()
        if (hours != 0L) {
            buf.append(hours).append("hour")
        }
        if (minutes != 0) {
            buf.append(minutes).append("min")
        }
        if (secs != 0 || buf.isEmpty()) {
            buf.append(secs).append("sec")
        }
        return buf.toString()
    }

    fun genIdentity(uri: URI): String {
        Preconditions.checkNotNull(uri)
        var suffix = uri.path
        if (suffix.length > 50) {
            suffix = Paths.get(suffix).fileName.toString()
        }
        return uri.host + ":" + suffix
    }

    fun bytesFormat(v: Long, scale: Int): String {
        if (v < 1024) {
            return "$v B"
        }
        val z = (63 - java.lang.Long.numberOfLeadingZeros(v)) / 10
        val decimal: BigDecimal =
            BigDecimal.valueOf(v).divide(BigDecimal.valueOf((1L shl (z * 10))), scale, RoundingMode.HALF_UP)
        return String.format("%s %sB", decimal, "BKMGTPE"[z])
    }

    fun bytesFormat(bigDecimal: BigDecimal, scale: Int): String {
        Preconditions.checkNotNull(bigDecimal)
        return bytesFormat(bigDecimal.toLong(), scale)
    }

    fun equals(bigNum1: BigDecimal?, bigNum2: BigDecimal?): Boolean {
        if (bigNum1 === bigNum2) {
            return true
        }
        if (bigNum1 == null || bigNum2 == null) {
            return false
        }
        return 0 == bigNum1.compareTo(bigNum2)
    }

    fun parseHexadecimal(hexString: String): ByteArray {
        Preconditions.checkNotBlank(hexString)
        Preconditions.checkArgument(hexString.startsWith("0x") || hexString.startsWith("0X"))

        val length = hexString.length
        Preconditions.checkArgument((length and 1) == 0, "invalid Hexadecimal string")

        val bytes = ByteArray((length - 2) / 2)
        var i = 2
        var j = 0
        while (i < length) {
            bytes[j] = (hexString.substring(i, i + 2).toShort(16) and 0xFF).toByte()
            i += 2
            ++j
        }
        return bytes
    }

    fun md5(str: String): String {
        val messageDigest: MessageDigest

        try {
            messageDigest = MessageDigest.getInstance("MD5")
            messageDigest.reset()
            messageDigest.update(str.toByteArray(StandardCharsets.UTF_8))
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException(e)
        }

        val byteArray: ByteArray = messageDigest.digest()
        val md5StrBuff = StringBuilder()
        for (b in byteArray) {
            if (Integer.toHexString(0xFF and b.toInt()).length == 1) {
                md5StrBuff.append("0").append(Integer.toHexString(0xFF and b.toInt()))
            } else {
                md5StrBuff.append(Integer.toHexString(0xFF and b.toInt()))
            }
        }
        return md5StrBuff.toString()
    }

    fun isFileNameTooLong(filePath: String): Boolean {
        Preconditions.checkNotNull(filePath)
        val len = filePath.length
        return len >= 254
    }

    fun isValidURL(uri: URI?): Boolean {
        if (null == uri) {
            return false
        }
        try {
            uri.toURL()
        } catch (ex: Exception) {
            return false
        }
        return true
    }

    fun getPreviousStackTrace(upper: Int): String {
        val throwable = Throwable()
        val stackTrace = throwable.stackTrace
        return stackTrace[(upper + 1) % stackTrace.size].toString()
    }

    @JvmStatic
    val defaultUserAgent: String
        get() {
            val os = System.getProperty("os.name")
            if (StringUtils.isNotBlank(os)) {
                if (os.startsWith("Windows")) {
                    return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.36"
                }
                if (os.startsWith("Mac")) {
                    return "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.36"
                }
                if (os.startsWith("Linux")) {
                    return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.36"
                }
            }
            return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.36"
        }

    fun <S, T> mapToNullable(`object`: S?, mapper: Function<S, T>): T? {
        return if (`object` != null) mapper.apply(`object`) else null
    }

    fun rate(a: Long, b: Long): BigDecimal {
        return BigDecimal.valueOf(a).multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(b), 3, RoundingMode.HALF_UP)
    }

    @Throws(IOException::class)
    fun checkAndCreateDir(dir: Path, dirName: String): Path {
        Preconditions.checkNotBlank(dirName)
        if (Files.exists(dir)) {
            Preconditions.m3u8Check(
                Files.isDirectory(dir),
                "%s is not a directory: %s",
                dirName,
                dir
            )
        } else {
            Files.createDirectory(dir)
        }
        return dir
    }

    @Throws(IOException::class)
    fun checkAndCreateDir(
        directory: String,
        dirName: String
    ): File {
        Preconditions.checkNotBlank(dirName)
        val directoryFile = File(directory)
        if (directoryFile.exists()) {
            Preconditions.m3u8Check(
                directoryFile.isDirectory,
                "%s is not a directory: %s",
                dirName,
                directoryFile
            )
        } else {
            directoryFile.mkdirs()
        }
        return directoryFile
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Throwable, R> sneakyThrow(t: Throwable): R {
        throw t as T
    }
}

