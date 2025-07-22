package nobility.downloader.core.driver.undetected_chrome

import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

class Patcher(
    private val driverExecutablePath: String
) {

    /**
     * Used to determine if the changes have been successful.
     */
    private val consoleLine = "nothing to see here! xx"

    fun auto() {
        if (!isBinaryPatched) {
            patchExe()
        }
    }

    private val isBinaryPatched: Boolean
        get() {
            if (driverExecutablePath.isEmpty()) {
                throw RuntimeException("driverExecutablePath is required.")
            }
            val file = File(driverExecutablePath)
            try {
                val br = BufferedReader(FileReader(file, StandardCharsets.ISO_8859_1))
                br.use { reader ->
                    reader.readLines().forEach {
                        if (it.contains(consoleLine)) {
                            return true
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return false
        }

    private fun patchExe(): Int {
        val linect = 0
        var file: RandomAccessFile? = null
        try {
            file = RandomAccessFile(driverExecutablePath, "rw")
            val buffer = ByteArray(1024)
            val stringBuilder = StringBuilder()
            var read: Long
            while (true) {
                read = file.read(buffer, 0, buffer.size).toLong()
                if (read == 0L || read == -1L) {
                    break
                }
                stringBuilder.append(String(
                    buffer,
                    0,
                    read.toInt(),
                    StandardCharsets.ISO_8859_1
                ))
            }
            val content = stringBuilder.toString()
            val pattern = Pattern.compile("\\{window\\.cdc.*?;}")
            val matcher = pattern.matcher(content)
            if (matcher.find()) {
                val group = matcher.group()
                val newTarget = StringBuilder("{console.log(\"$consoleLine\"}")
                val k = group.length - newTarget.length
                for (i in 0 until k) {
                    newTarget.append(" ")
                }
                val newContent = content.replace(group, newTarget.toString())
                file.seek(0)
                file.write(newContent.toByteArray(StandardCharsets.ISO_8859_1))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (file != null) {
                try {
                    file.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return linect
    }

    @Suppress("UNUSED")
    private fun genRandomCdc(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz"
        val cdc = CharArray(27)
        for (i in 0..26) {
            cdc[i] = chars.random()
        }
        return String(cdc)
    }
}