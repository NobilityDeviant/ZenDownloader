
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.text.CharacterIterator
import java.text.StringCharacterIterator
import javax.net.ssl.HttpsURLConnection

fun main(args: Array<String>) {

    val latestJar = findJar()

    if (latestJar == null) {
        println(
            """
                The ZenDownloader JAR wasn't found.
                Make sure to keep the file structure the same as it was downloaded.
                The JAR should be located inside the app folder.
            """.trimIndent()
        )
        return
    }

    val exitCode = launchJar(latestJar, args).waitFor()

    if (exitCode == AppInfo.UPDATE_CODE) {

        println("Update requested. Downloading new version...")

        val releaseResult = Updater.parseLatestRelease()
        val releaseData = releaseResult.data

        if (releaseData != null) {
            runBlocking(Dispatchers.IO) {
                val downloadResult = downloadUpdate(releaseData)
                val downloadData = downloadResult.data

                if (downloadData != null) {
                    if (downloadData.extension == "jar") {
                        println("Update complete. Restarting...")
                        latestJar.delete()
                        launchJar(downloadData, args).waitFor()
                    } else {
                        println(
                            """
                                Failed to download the new release jar file.
                                Please reopen the launcher or manually download the update from:
                    
                                ${AppInfo.RELEASES_LINK}
                    
                                And replace the ZenDownloader-x.x.x.jar inside the app folder with the new one.
                            """.trimIndent()
                        )
                    }
                } else {
                    println(downloadResult.message)
                }
            }
        } else {
            println(releaseResult.message)
            println(
                """
                    Failed to download the new release.
                    Please reopen the launcher or manually download the update from:
                    
                    ${AppInfo.RELEASES_LINK}
                    
                    And replace the ZenDownloader-x.x.x.jar inside the app folder with the new one.
                """.trimIndent()
            )
        }
    }
}

private fun launchJar(
    jar: File,
    args: Array<String>
): Process {
    val javaBin = File("runtime/bin/java")
    val command = mutableListOf(
        javaBin.absolutePath,
        "-Xmx3G",
        "-jar",
        jar.absolutePath
    )
    command.addAll(args)
    return ProcessBuilder(command)
        .inheritIO()
        .start()
}

private fun findJar(): File? {
    val appDir = File("app")
    return appDir
        .listFiles { f -> f.name.matches(Regex("""ZenDownloader-\d+\.\d+\.\d+\.jar""")) }
        ?.maxWithOrNull(Comparator { f1, f2 ->
            val v1 = extractVersion(f1.name)
            val v2 = extractVersion(f2.name)
            v1.zip(v2)
                .map { it.first.compareTo(it.second) }
                .firstOrNull { it != 0 } ?: 0
        })

}

private fun extractVersion(name: String): List<Int> {
    return Regex("""(\d+)\.(\d+)\.(\d+)""")
        .find(name)
        ?.groupValues
        ?.drop(1)
        ?.map { it.toInt() }
        ?: listOf(0, 0, 0)
}

private suspend fun downloadUpdate(
    update: Update
): Resource<File> = withContext(Dispatchers.IO) {
    val downloadFolderPath = ".${File.separator}app${File.separator}"
    val downloadFolder = File(downloadFolderPath)
    if (!downloadFolder.exists() && !downloadFolder.mkdirs()) {
        return@withContext Resource.Error(
            """
                Failed to find or make the download folder.
                Make sure the ZenDownloader folder has the correct permissions for writing.
            """.trimIndent()
        )
    }
    val downloadedUpdate = File(
        downloadFolderPath + update.downloadName
    )
    var con: HttpsURLConnection? = null
    var bis: BufferedInputStream? = null
    var fos: FileOutputStream? = null
    var bos: BufferedOutputStream? = null
    val finished: Boolean
    try {
        if (downloadedUpdate.exists()) {
            downloadedUpdate.delete()
        }
        con = URI(update.downloadLink).toURL()
            .openConnection() as HttpsURLConnection
        con.addRequestProperty(
            "Accept",
            update.downloadType
        )
        con.addRequestProperty("Accept-Encoding", "gzip, deflate, br")
        con.addRequestProperty("Accept-Language", "en-US,en;q=0.9")
        con.addRequestProperty("Connection", "keep-alive")
        con.connectTimeout = 30_000
        con.readTimeout = 30_000
        bis = BufferedInputStream(con.inputStream)
        val completeFileSize = con.contentLengthLong
        if (completeFileSize == -1L) {
            return@withContext Resource.Error("Failed to find update file size.")
        }
        val buffer = ByteArray(8192)
        fos = FileOutputStream(downloadedUpdate, true)
        bos = BufferedOutputStream(fos, buffer.size)
        var count: Int
        var total = 0L
        var lastProgress = -1
        while (bis.read(buffer).also { count = it } != -1) {
            total += count.toLong()
            bos.write(buffer, 0, count)
            val progress = (100.0 * total / completeFileSize).toInt()
            if (progress != lastProgress) {
                val barWidth = 50
                val filled = (progress * barWidth) / 100
                val bar = "=".repeat(filled) + " ".repeat(barWidth - filled)
                print("\r[$bar] $progress% | ${bytesToString(total)}/${bytesToString(completeFileSize)}")
                lastProgress = progress
            }
        }
        println()
        finished = total >= completeFileSize
    } catch (e: Exception) {
        return@withContext Resource.Error(
            """
                        Failed to download the new release.
                        Please reopen the launcher or manually download the update from:
                        
                        ${update.downloadLink}
                        
                        And replace the ZenDownloader-x.x.x.jar inside the app folder with the new one.
                    """.trimIndent(),
            e
        )
    } finally {
        bos?.close()
        fos?.close()
        con?.disconnect()
        bis?.close()
    }
    return@withContext if (finished) {
        Resource.Success(downloadedUpdate)
    } else {
        Resource.Error(
            """
                    Failed to download the new release.
                    Please reopen the launcher or manually download the update from:
                    
                    ${update.downloadLink}
                    
                    And replace the ZenDownloader-x.x.x.jar inside the app folder with the new one.
                """.trimIndent()
        )
    }
}

private fun bytesToString(bytes: Long): String {
    var mBytes = bytes
    if (-1000 < mBytes && mBytes < 1000) {
        return "$mBytes B"
    }
    val ci: CharacterIterator = StringCharacterIterator("kMGTPE")
    while (mBytes <= -999950 || mBytes >= 999950) {
        mBytes /= 1000
        ci.next()
    }
    return String.format("%.1f %cB", mBytes / 1000.0, ci.current())
}


