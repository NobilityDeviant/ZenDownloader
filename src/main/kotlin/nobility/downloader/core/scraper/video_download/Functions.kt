package nobility.downloader.core.scraper.video_download

import kotlinx.coroutines.*
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.Core
import nobility.downloader.core.driver.DriverBaseImpl
import nobility.downloader.core.entities.Download
import nobility.downloader.core.scraper.video_download.m3u8_downloader.core.M3u8Download
import nobility.downloader.core.scraper.video_download.m3u8_downloader.core.M3u8DownloadListener
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.config.HttpRequestManagerConfig
import nobility.downloader.core.settings.Defaults
import nobility.downloader.utils.*
import java.io.*
import java.net.URI
import javax.net.ssl.HttpsURLConnection

object Functions {

    /**
     * A new function used to read the webpage.
     * If it fails simple mode, it will then move on to using selenium instead.
     * Simple mode is a term for using pure http calls.
     * Full mode is when we use selenium.
     */
    suspend fun readUrlLines(
        url: String,
        data: VideoDownloadData,
        addReferer: Boolean = true
    ): Resource<StringBuilder> = withContext(Dispatchers.IO) {
        var simpleRetries = 0
        var exception: Exception? = null
        while (simpleRetries < Defaults.SIMPLE_RETRIES.int()) {
            var con: HttpsURLConnection? = null
            var reader: BufferedReader? = null
            val sb = StringBuilder()
            try {
                con = wcoConnection(
                    url,
                    data.userAgent,
                    false,
                    addReferer
                )
                reader = con.inputStream.bufferedReader()
                reader.readLines().forEach {
                    sb.appendLine(it)
                }
                return@withContext Resource.Success(sb)
            } catch (e: Exception) {
                exception = e
                simpleRetries++
                delay(500)
                continue
            } finally {
                try {
                    con?.disconnect()
                    reader?.close()
                } catch (_: Exception) {
                }
            }
        }
        //data.logError(
          //  "Failed to read webpage with simple mode. Moving on to full mode.",
            //exception
        //)
        var fullModeRetries = 0
        var fullModeException: Exception? = null
        while (fullModeRetries <= Defaults.FULL_RETRIES.int()) {
            try {
                data.driver.navigate().to(url)
                if (data.driver.source().contains("404 - Page not Found")) {
                    return@withContext Resource.Error("404 Page not found.")
                }
                if (data.driver.source().contains("Sorry, you have been blocked")) {
                    fullModeRetries++
                    delay(500)
                    continue
                }
                val sb = StringBuilder()
                data.driver.source().lines().forEach {
                    sb.appendLine(it)
                }
                return@withContext Resource.Success(sb)
            } catch (e: Exception) {
                fullModeException = e
                fullModeRetries++
                continue
            }
        }
        return@withContext Resource.Error(
            "Failed to read lines with selenium.",
            fullModeException
        )
    }

    /**
     * Created as to not initialize WDM early.
     */
    suspend fun readUrlLines(
        url: String,
        customTag: String = "readUrlLines",
        addReferer: Boolean = true
    ): Resource<StringBuilder> = withContext(Dispatchers.IO) {
        val userAgent = UserAgents.random
        var simpleRetries = 0
        var exception: Exception? = null
        while (simpleRetries < Defaults.SIMPLE_RETRIES.int()) {
            var con: HttpsURLConnection? = null
            var reader: BufferedReader? = null
            val sb = StringBuilder()
            try {
                con = wcoConnection(
                    url,
                    userAgent,
                    false,
                    addReferer
                )
                reader = con.inputStream.bufferedReader()
                reader.readLines().forEach {
                    sb.appendLine(it)
                }
                return@withContext Resource.Success(sb)
            } catch (e: Exception) {
                exception = e
                simpleRetries++
                delay(500)
                continue
            } finally {
                try {
                    con?.disconnect()
                    reader?.close()
                } catch (_: Exception) {
                }
            }
        }
        FrogLog.logError(
            "[$customTag] Failed to read webpage with simple mode. Moving on to full mode.",
            exception
        )
        val driverBase = DriverBaseImpl(userAgent = userAgent)
        var fullModeRetries = 0
        var fullModeException: Exception? = null
        while (fullModeRetries <= Defaults.FULL_RETRIES.int()) {
            try {
                driverBase.driver.navigate().to(url)
                if (driverBase.driver.source().contains("404 - Page not Found")) {
                    return@withContext Resource.Error("404 Page not found.")
                }
                if (driverBase.driver.source().contains("Sorry, you have been blocked")) {
                    fullModeRetries++
                    delay(500)
                    continue
                }
                val sb = StringBuilder()
                driverBase.driver.source().lines().forEach {
                    sb.appendLine(it)
                }
                return@withContext Resource.Success(sb)
            } catch (e: Exception) {
                fullModeException = e
                fullModeRetries++
                continue
            } finally {
                driverBase.killDriver()
            }
        }
        return@withContext Resource.Error(
            "[$customTag] Failed to read lines with selenium.",
            fullModeException
        )
    }

    suspend fun wcoConnection(
        url: String,
        userAgent: String,
        supportEncoding: Boolean = true,
        addReferer: Boolean = false
    ): HttpsURLConnection = withContext(Dispatchers.IO) {
        val con = URI(url).toURL().openConnection() as HttpsURLConnection
        con.addRequestProperty(
            "Accept",
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
        )
        if (supportEncoding) {
            con.addRequestProperty("Accept-Encoding", "gzip, deflate, br")
        }
        con.addRequestProperty("Accept-Language", "en-US,en;q=0.9")
        con.addRequestProperty("Connection", "keep-alive")
        con.addRequestProperty("Sec-Fetch-Dest", "document")
        con.addRequestProperty("Sec-Fetch-Mode", "navigate")
        con.addRequestProperty("Sec-Fetch-Site", "cross-site")
        con.addRequestProperty("Sec-Fetch-User", "?1")
        con.addRequestProperty("Upgrade-Insecure-Requests", "1")
        if (addReferer) {
            con.addRequestProperty("Referer", Core.wcoUrlWww)
        }
        //the same user agent as the driver is needed.
        con.addRequestProperty("User-Agent", userAgent)
        con.connectTimeout = Defaults.TIMEOUT.int() * 1000
        con.readTimeout = Defaults.TIMEOUT.int() * 1000
        return@withContext con
    }

    suspend fun fileSize(
        link: String,
        userAgent: String,
        headMode: Boolean
    ): Long = withContext(Dispatchers.IO) {
        val con = wcoConnection(link, userAgent)
        if (!headMode) {
            con.requestMethod = "HEAD"
            con.useCaches = false
        }
        return@withContext con.contentLengthLong
    }

    suspend fun downloadVideo(
        url: String,
        output: File,
        data: VideoDownloadData,
        download: Download = data.currentDownload,
    ) = withContext(Dispatchers.IO) {
        var offset = 0L
        if (output.exists()) {
            offset = output.length()
        }
        val con = wcoConnection(url, data.userAgent)
        con.setRequestProperty("Range", "bytes=$offset-")

        var lastTime = System.currentTimeMillis()
        var lastBytesRead = 0L
        val completeFileSize = con.contentLength + offset
        val buffer = ByteArray(8192)
        val bis = BufferedInputStream(con.inputStream)
        val fos = FileOutputStream(output, true)
        val bos = BufferedOutputStream(fos, buffer.size)
        var bytesRead = 0
        var totalBytesRead: Long = offset
        val updaterJob = launch {

            var remainingSeconds = 0

            while (isActive) {
                val currentTime = System.currentTimeMillis()
                val elapsedTime = (currentTime - lastTime) / 1000
                if (elapsedTime > 0) {
                    val bytesDownloadedSinceLastCheck = totalBytesRead - lastBytesRead
                    val downloadSpeed = bytesDownloadedSinceLastCheck / elapsedTime
                    if (downloadSpeed > 0) {
                        val remainingBytes = completeFileSize - totalBytesRead
                        val remainingTime = remainingBytes / downloadSpeed
                        remainingSeconds = remainingTime.toInt()
                        Core.child.updateDownloadProgress(
                            download,
                            remainingSeconds,
                            downloadSpeed
                        )
                    }
                    //update last checked time and bytes
                    lastTime = currentTime
                    lastBytesRead = totalBytesRead
                    delay(1000)
                }
            }
            Core.child.updateDownloadProgress(
                download,
                remainingSeconds
            )
        }

        while (bis.read(buffer).also { bytesRead = it } != -1) {
            if (!Core.child.isRunning) {
                data.writeMessage(
                    "Stopping video download at ${Tools.bytesToString(totalBytesRead)}/${
                        Tools.bytesToString(
                            completeFileSize
                        )
                    } for: ${download.name}"
                )
                break
            }
            bos.write(buffer, 0, bytesRead)
            totalBytesRead += bytesRead.toLong()
        }
        updaterJob.cancel()
        bos.flush()
        bos.close()
        fos.flush()
        fos.close()
        bis.close()
        con.disconnect()
    }

    fun httpRequestConfig(
        userAgent: String
    ): HttpRequestManagerConfig {
        return HttpRequestManagerConfig.custom()
            .userAgent(userAgent)
            .setTimeoutMillis((Defaults.TIMEOUT.int() * 1000))
            .defaultMaxRetries(10)
            .build()
    }

    fun m3u8Download(
        downloadLink: String,
        saveFile: File,
        downloadListener: M3u8DownloadListener
    ): M3u8Download {
        var builder = M3u8Download.builder()
            .setUri(URI(downloadLink))
            .setFileName(saveFile.name)
            .setWorkHome(System.getProperty("user.home") + "/.m3u8_files/${saveFile.nameWithoutExtension}/")
            .setTargetFiletDir(saveFile.parent)
            .deleteTsOnComplete()
            .forceCacheAssignmentBasedOnFileName()
            .setRetryCount(3)
            .addHttpHeader("Accept", "*/*")
            .addHttpHeader("Cache-Control", "no-cache")
            .addListener(downloadListener)
        if (saveFile.extension == "m4a") {
            builder.apply {
                mergeWithoutConvertToMp4()
            }
        }
        return builder.build()
    }
}