package nobility.downloader.core.scraper.video_download

import AppInfo
import Resource
import kotlinx.coroutines.*
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.Core
import nobility.downloader.core.driver.DriverBase
import nobility.downloader.core.driver.DriverBaseImpl
import nobility.downloader.core.driver.undetected_chrome.SysUtil
import nobility.downloader.core.entities.Download
import nobility.downloader.core.scraper.video_download.m3u8_downloader.core.M3u8Download
import nobility.downloader.core.scraper.video_download.m3u8_downloader.core.M3u8DownloadListener
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.config.HttpRequestManagerConfig
import nobility.downloader.core.settings.Defaults
import nobility.downloader.utils.FrogLog
import nobility.downloader.utils.JavascriptHelper
import nobility.downloader.utils.Tools
import nobility.downloader.utils.source
import nobility.downloader.utils.user_agents.UserAgents
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
        addReferer: Boolean = true,
        skipSimpleMode: Boolean = false,
        fullModeFunction: (suspend (DriverBase) -> Unit)? = null
    ): Resource<StringBuilder> = withContext(Dispatchers.IO) {
        var simpleRetries = 0
        var exception: Exception? = null
        if (!skipSimpleMode) {
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
                    if (sb.contains("404 - Page not Found")) {
                        return@withContext Resource.Error("404 Page not found.")
                    } else if (sb.contains("403 Forbidden")) {
                        simpleRetries++
                        delay(500)
                        exception = Exception("403 Forbidden")
                        continue
                    } else if (sb.contains("Sorry, you have been blocked")) {
                        simpleRetries++
                        delay(500)
                        exception = Exception("Blocked by Cloudflare")
                        continue
                    } else if (sb.contains("Just a moment...")) {
                        simpleRetries++
                        delay(500)
                        exception = Exception("Blocked by Cloudflare")
                        continue
                    } else {
                        return@withContext Resource.Success(sb)
                    }
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
            @Suppress("KotlinConstantConditions")
            if (AppInfo.DEBUG_MODE) {
                data.error(
                    "Failed to find source code with simple mode.",
                    exception
                )
            }
        }
        var fullModeRetries = 0
        var fullModeException: Exception? = null
        while (fullModeRetries <= Defaults.FULL_RETRIES.int()) {
            try {
                data.base.executeJs(
                    JavascriptHelper.changeUrlInternally(url)
                )
                data.base.waitForPageJs()
                fullModeFunction?.invoke(data.base)
                val source = data.base.driver.source()
                if (source.contains("404 - Page not Found")) {
                    return@withContext Resource.Error("[Selenium] 404 Page not found.")
                } else if (source.contains("403 Forbidden")) {
                    return@withContext Resource.Error("[Selenium] 403 Forbidden")
                } else if (source.contains("Sorry, you have been blocked")) {
                    return@withContext Resource.Error("[Selenium] Blocked by Cloudflare")
                } else if (source.contains("<title>Just a moment")) {
                    return@withContext Resource.Error("[Selenium] Blocked by Cloudflare")
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
            fullModeException
        )
    }

    /**
     * Created as to not initialize WDM early.
     */
    suspend fun readUrlLines(
        url: String,
        customTag: String = "readUrlLines",
        addReferer: Boolean = true,
        userAgent: String = UserAgents.random,
        skipSimpleMode: Boolean = false,
        fullModeFunction: (suspend (DriverBase) -> Unit)? = null
    ): Resource<StringBuilder> = withContext(Dispatchers.IO) {
        var simpleRetries = 0
        var exception: Exception? = null
        if (!skipSimpleMode) {
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
                    if (sb.contains("404 - Page not Found")) {
                        return@withContext Resource.Error("404 Page not found.")
                    } else if (sb.contains("403 Forbidden")) {
                        simpleRetries++
                        delay(500)
                        exception = Exception("403 Forbidden")
                        continue
                    } else if (sb.contains("Sorry, you have been blocked")) {
                        simpleRetries++
                        delay(500)
                        exception = Exception("Blocked by Cloudflare")
                        continue
                    } else if (sb.contains("<title>Just a moment")) {
                        FrogLog.error("Blocked by cloudflare. Just a moment...")
                        simpleRetries++
                        delay(500)
                        exception = Exception("Blocked by Cloudflare")
                        continue
                    } else {
                        return@withContext Resource.Success(sb)
                    }
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
            @Suppress("KotlinConstantConditions")
            if (AppInfo.DEBUG_MODE) {
                FrogLog.error(
                    "[$customTag] Failed to read webpage with simple mode.",
                    exception
                )
            }
        }
        val driverBase = DriverBaseImpl(
            userAgent = userAgent,
            manualSetup = true
        )
        driverBase.setup()
        var fullModeRetries = 0
        var fullModeException: Exception? = null
        while (fullModeRetries <= Defaults.FULL_RETRIES.int()) {
            try {
                driverBase.executeJs(
                    JavascriptHelper.changeUrlInternally(url)
                )
                driverBase.waitForPageJs()
                fullModeFunction?.invoke(driverBase)
                val source = driverBase.driver.source()
                if (source.contains("404 - Page not Found")) {
                    return@withContext Resource.Error("[Selenium] 404 Page not found.")
                } else if (source.contains("403 Forbidden")) {
                    return@withContext Resource.Error("[Selenium] 403 Forbidden")
                } else if (source.contains("Sorry, you have been blocked")) {
                    return@withContext Resource.Error("[Selenium] Blocked by Cloudflare")
                } else if (source.contains("<title>Just a moment")) {
                    return@withContext Resource.Error("[Selenium] Blocked by Cloudflare")
                }
                val sb = StringBuilder()
                source.lines().forEach {
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
                data.message(
                    "Stopping video download at ${Tools.bytesToString(totalBytesRead)}/${
                        Tools.bytesToString(
                            completeFileSize
                        )
                    }"
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
        val builder = M3u8Download.builder()
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
        if (saveFile.extension == "m4a" || saveFile.extension == "srt") {
            builder.apply {
                mergeWithoutConvertToMp4()
            }
        }
        return builder.build()
    }

    fun killChromeProcesses() {
        if (SysUtil.isWindows) {
            Runtime.getRuntime().exec(arrayOf(
                "taskkill",
                "/F",
                "/IM",
                "chromedriver.exe",
                "/T"
            ))
            val process = Runtime.getRuntime().exec(
                arrayOf(
                    "tasklist",
                    "/FI",
                    "\"IMAGENAME eq chrome.exe\"",
                    "/FI",
                    "\"STATUS eq running\"",
                    "/FO LIST"
                )
            )
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            val lines = output.split("\n")
            var foundChrome = false
            for (line in lines) {
                if (line.contains("chrome.exe", ignoreCase = true)) {
                    foundChrome = true
                    break
                }
            }
            if (foundChrome) {
                Runtime.getRuntime().exec(
                    arrayOf(
                        "taskkill",
                        "/F",
                        "/IM",
                        "chrome.exe",
                        "/T"
                    )
                )
            }
        } else {
            Runtime.getRuntime().exec(
                arrayOf(
                    "pkill",
                    "-f",
                    "chromedriver"
                )
            )
            Runtime.getRuntime().exec(
                arrayOf(
                    "pkill",
                    "-f",
                    "chrome"
                )
            )
        }
    }

}