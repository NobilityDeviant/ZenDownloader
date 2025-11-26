package nobility.downloader.core.scraper.video_download.m3u8_downloader.util

import java.io.File
import java.math.BigDecimal
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import kotlin.experimental.and

object Utils {

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
        } catch (_: Exception) {
            return false
        }
        return true
    }

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

}

