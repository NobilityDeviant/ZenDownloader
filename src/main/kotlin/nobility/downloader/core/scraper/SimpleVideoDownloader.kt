package nobility.downloader.core.scraper

import kotlinx.coroutines.*
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.BoxHelper.Companion.downloadForSlugAndQuality
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.BoxHelper.Companion.seriesForSlug
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.Core
import nobility.downloader.core.driver.DriverBase
import nobility.downloader.core.entities.Download
import nobility.downloader.core.entities.Episode
import nobility.downloader.core.settings.Defaults
import nobility.downloader.core.settings.Quality
import nobility.downloader.utils.*
import nobility.downloader.utils.FrogLog.logError
import nobility.downloader.utils.FrogLog.logInfo
import nobility.downloader.utils.FrogLog.writeMessage
import org.jsoup.Jsoup
import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.*
import java.net.URI
import java.time.Duration
import javax.net.ssl.HttpsURLConnection

class SimpleVideoDownloader(
    private val temporaryQuality: Quality? = null
) : DriverBase() {

    private var mCurrentEpisode: Episode? = null
    private var mCurrentDownload: Download? = null
    private val currentEpisode get() = mCurrentEpisode!!
    private val currentDownload get() = mCurrentDownload!!
    private var retries = 0
    private var resRetries = 0
    private val pageChangeWaitTime = 5_000L //in milliseconds
    private val qualityAndDownloads = mutableListOf<QualityAndDownload>()
    private val taskScope = CoroutineScope(Dispatchers.Default)

    /**
     * An experimental mode to scrape with little use of selenium.
     */
    private var simpleMode = true
    private var simpleRetries = 0

    suspend fun run() = withContext(Dispatchers.IO) {
        while (Core.child.isRunning) {
            if (retries >= Constants.maxRetries) {
                if (mCurrentEpisode != null) {
                    writeMessage("Reached max retries of ${Constants.maxRetries} for ${currentEpisode.name}. Skipping download...")
                }
                finishEpisode()
                resRetries = 0
                retries = 0
                if (simpleRetries < Constants.maxSimpleRetries) {
                    simpleRetries = 0
                    simpleMode = true
                }
                continue
            }
            if (simpleRetries >= Constants.maxSimpleRetries) {
                simpleMode = false
                simpleRetries = 0
                if (mCurrentEpisode != null) {
                    writeMessage("Reached max simple mode retries for ${currentEpisode.name} Turning it off.")
                }
                continue
            }
            if (mCurrentEpisode == null) {
                qualityAndDownloads.clear()
                mCurrentEpisode = Core.child.nextEpisode
                if (mCurrentEpisode == null) {
                    break
                }
                resRetries = 0
                retries = 0
                if (simpleRetries < Constants.maxSimpleRetries) {
                    simpleRetries = 0
                    simpleMode = true
                }
            }
            val slug = currentEpisode.slug
            if (slug.isEmpty()) {
                writeMessage("Skipping video: (${currentEpisode.name}) with no slug.")
                finishEpisode()
                continue
            }
            if (temporaryQuality != null) {
                val tempDownload = downloadForSlugAndQuality(slug, temporaryQuality)
                if (tempDownload != null && tempDownload.isComplete) {
                    writeMessage("[DB] Skipping completed video: " + currentEpisode.name)
                    tempDownload.downloading = false
                    tempDownload.queued = false
                    Core.child.updateDownloadInDatabase(
                        tempDownload,
                        true
                    )
                    finishEpisode()
                    continue
                }
            }
            val movie = Core.child.movieHandler.movieForSlug(slug)
            if (movie != null) {
                handleMovie(movie)
            } else {
                var downloadLink = ""
                var qualityOption = temporaryQuality ?: Quality.qualityForTag(
                    Defaults.QUALITY.string()
                )
                if (qualityAndDownloads.isEmpty()) {
                    if (resRetries < Constants.maxResRetries) {
                        val result = detectAvailableResolutions(slug, qualityOption)
                        if (result.errorCode != -1) {
                            val errorCode = ErrorCode.errorCodeForCode(result.errorCode)
                            if (errorCode == ErrorCode.NO_FRAME) {
                                resRetries++
                                logError(
                                    "Failed to find frame for resolution check. Retrying..."
                                )
                                continue
                            } else if (errorCode == ErrorCode.CLOUDFLARE_FUCK) {
                                logError(
                                    """
                                        Cloudflare has blocked our request.
                                        Unfortunately that means we can't proceed, but well continue anyways...
                                    """.trimIndent()
                                )
                                continue
                            } else if (errorCode == ErrorCode.IFRAME_FORBIDDEN) {
                                resRetries = 3
                                logError(
                                    "Failed to find video frame for: $slug" +
                                            "Please report this in github issues with the video you are trying to download."
                                )
                                continue
                            } else if (errorCode == ErrorCode.FAILED_EXTRACT_RES) {
                                resRetries = 3
                                logError(
                                    "Failed to extract resolution links. Returned an empty list."
                                )
                            } else if (errorCode == ErrorCode.NO_JS) {
                                resRetries = 3
                                writeMessage("This browser doesn't support JavascriptExecutor.")
                            } else if (errorCode == ErrorCode.SIMPLE_MODE_FAILED) {
                                simpleRetries++
                                logError("Failed in simple mode. Retrying with ${simpleRetries}/${Constants.maxSimpleRetries} left.")
                                continue
                            }
                        }
                        if (result.data != null) {
                            qualityAndDownloads.addAll(result.data)
                            qualityOption = Quality.bestQuality(
                                qualityOption,
                                result.data.map { it.quality }
                            )
                            result.data.forEach {
                                if (it.quality == qualityOption) {
                                    downloadLink = it.downloadLink
                                }
                            }
                        } else {
                            logInfo(
                                "Failed to find resolution download links. Defaulting to ${Quality.LOW.tag} quality."
                            )
                            qualityOption = Quality.LOW
                        }
                    }
                } else {
                    //logInfo("Using already found qualities.")
                    qualityOption = Quality.bestQuality(
                        qualityOption,
                        qualityAndDownloads.map { it.quality }
                    )
                    qualityAndDownloads.forEach {
                        if (it.quality == qualityOption) {
                            downloadLink = it.downloadLink
                        }
                    }
                }
                val series = seriesForSlug(
                    currentEpisode.seriesSlug
                )
                if (series == null) {
                    writeMessage(
                        "Failed to find series for episode: ${currentEpisode.name}. Unable to create save folder."
                    )
                }
                val downloadFolderPath = Defaults.SAVE_FOLDER.string()
                val episodeName = currentEpisode.name.fixForFiles()
                val seasonFolder = if (Defaults.SEPARATE_SEASONS.boolean())
                    Tools.findSeasonFromEpisode(episodeName) else null
                var saveFolder = File(
                    downloadFolderPath + File.separator
                            + (series?.name?.fixForFiles() ?: "NoSeries")
                            + if (seasonFolder != null) (File.separator + seasonFolder + File.separator) else ""
                )
                if (!saveFolder.exists() && !saveFolder.mkdirs()) {
                    writeMessage(
                        "Unable to create series save folder: ${saveFolder.absolutePath} " +
                                "Defaulting to $downloadFolderPath/NoSeries"
                    )
                    saveFolder = File(downloadFolderPath + File.separator + "NoSeries")
                }
                val extraQualityName = if (qualityOption != Quality.LOW)
                    " (${qualityOption.tag})" else ""
                val saveFile = File(
                    saveFolder.absolutePath + File.separator
                            + "$episodeName$extraQualityName.mp4"
                )
                mCurrentDownload = downloadForSlugAndQuality(slug, qualityOption)
                if (mCurrentDownload != null) {
                    if (currentDownload.downloadPath.isEmpty()
                        || !File(currentDownload.downloadPath).exists()
                        || currentDownload.downloadPath != saveFile.absolutePath
                    ) {
                        currentDownload.downloadPath = saveFile.absolutePath
                    }
                    Core.child.addDownload(currentDownload)
                    if (currentDownload.isComplete) {
                        writeMessage("[DB] Skipping completed video: " + currentEpisode.name)
                        currentDownload.downloading = false
                        currentDownload.queued = false
                        Core.child.updateDownloadInDatabase(currentDownload, true)
                        finishEpisode()
                        continue
                    } else {
                        currentDownload.queued = true
                        Core.child.updateDownloadProgress(currentDownload)
                    }
                }

                if (downloadLink.isEmpty()) {
                    val link = slug.slugToLink()
                    driver.navigate().to(link)
                    val wait = WebDriverWait(driver, Duration.ofSeconds(15))
                    val frameIds = listOf(
                        "anime-js-0",
                        "cizgi-js-0",
                        "cizgi-video-js-0"
                    )
                    val videoJs = "video-js_html5_api"
                    var foundVideoFrame = false
                    for (id in frameIds) {
                        if (!Core.child.isRunning) {
                            break
                        }
                        try {
                            wait.pollingEvery(Duration.ofSeconds(1))
                                .withTimeout(Duration.ofSeconds(15))
                                .until(
                                    ExpectedConditions.visibilityOfElementLocated(
                                        By.id(id)
                                    )
                                )
                            val frame = driver.findElement(
                                By.id(id)
                            )
                            driver.switchTo().frame(frame)
                            foundVideoFrame = true
                            break
                        } catch (e: Exception) {
                            logError(
                                "Failed to find flag $id. Trying next one.",
                                e
                            )
                        }
                    }
                    if (!foundVideoFrame) {
                        logError(
                            "No flag was found. IFrame not found in webpage."
                        )
                        FrogLog.writeErrorToFile(
                            "Flag Not Found:\n" + driver.pageSource,
                            "pageSource"
                        )
                        writeMessage(
                            "Failed to find video frame for ${slug.slugToLink()}. Retrying..."
                        )
                        retries++
                        continue
                    }
                    var videoLinkError: String
                    try {
                        val videoPlayer = driver.findElement(By.id(videoJs))
                        //this makes it wait so it doesn't throw an error everytime
                        wait.pollingEvery(Duration.ofSeconds(1))
                            .withTimeout(Duration.ofSeconds(15))
                            .until(ExpectedConditions.attributeToBeNotEmpty(videoPlayer, "src"))
                        downloadLink = videoPlayer.getAttribute("src")
                        videoLinkError = videoPlayer.getAttribute("innerHTML")
                    } catch (e: Exception) {
                        logError(
                            "Found frame, but failed to find $videoJs",
                            e
                        )
                        writeMessage(
                            "Failed to find video player inside frame for ${slug.slugToLink()} Retrying..."
                        )
                        retries++
                        continue
                    }
                    if (downloadLink.isEmpty()) {
                        logInfo(
                            "Found $videoJs, but the video link was empty? No javascript found?"
                        )
                        if (videoLinkError.isNotEmpty()) {
                            logInfo("Empty link source: \n${videoLinkError.trim()}")
                        }
                        writeMessage(
                            "Failed to find video link for ${slug.slugToLink()}. Retrying..."
                        )
                        retries++
                        continue
                    }
                }
                logInfo("Successfully found video link with $retries retries.")
                try {
                    if (mCurrentDownload == null) {
                        mCurrentDownload = Download()
                        currentDownload.downloadPath = saveFile.absolutePath
                        currentDownload.name = currentEpisode.name
                        currentDownload.slug = currentEpisode.slug
                        currentDownload.seriesSlug = currentEpisode.seriesSlug
                        currentDownload.resolution = qualityOption.resolution
                        currentDownload.dateAdded = System.currentTimeMillis()
                        currentDownload.fileSize = 0
                        currentDownload.queued = true
                        Core.child.addDownload(currentDownload)
                        logInfo("Created new download for ${currentEpisode.name}")
                    } else {
                        logInfo("Using existing download for ${currentEpisode.name}")
                    }
                    //driver.navigate().to(downloadLink)
                    val originalFileSize = fileSize(downloadLink)
                    if (originalFileSize <= 5000) {
                        writeMessage("Retrying... Failed to determine file size for: " + currentEpisode.name)
                        retries++
                        continue
                    }
                    if (saveFile.exists()) {
                        if (saveFile.length() >= originalFileSize) {
                            writeMessage("[IO] Skipping completed video: " + currentEpisode.name)
                            currentDownload.downloadPath = saveFile.absolutePath
                            currentDownload.fileSize = originalFileSize
                            currentDownload.downloading = false
                            currentDownload.queued = false
                            Core.child.updateDownloadInDatabase(currentDownload, true)
                            finishEpisode()
                            continue
                        }
                    } else {
                        try {
                            val created = saveFile.createNewFile()
                            if (!created) {
                                throw Exception("No error thrown.")
                            }
                        } catch (e: Exception) {
                            logError(
                                "Unable to create video file for ${currentEpisode.name}",
                                e
                            )
                            writeMessage("Failed to create new video file for ${currentEpisode.name} Retrying...")
                            retries++
                            continue
                        }
                    }
                    writeMessage("[${qualityOption.tag}] Downloading: " + currentDownload.name)
                    currentDownload.queued = false
                    currentDownload.downloading = true
                    currentDownload.fileSize = originalFileSize
                    Core.child.addDownload(currentDownload)
                    Core.child.updateDownloadInDatabase(currentDownload, true)
                    driver.navigate().back()
                    downloadVideo(downloadLink, saveFile)
                    currentDownload.downloading = false
                    //second time to ensure ui update
                    Core.child.updateDownloadInDatabase(currentDownload, true)
                    if (saveFile.exists() && saveFile.length() >= originalFileSize) {
                        Core.child.incrementDownloadsFinished()
                        writeMessage("[${qualityOption.tag}] Successfully downloaded: $episodeName")
                        finishEpisode()
                    }
                } catch (e: IOException) {
                    currentDownload.queued = true
                    currentDownload.downloading = false
                    Core.child.updateDownloadInDatabase(currentDownload, true)
                    writeMessage(
                        """
                        Failed to download $episodeName
                        Error: ${e.localizedMessage}
                        Reattempting the download...
                    """.trimIndent()
                    )
                    logError(
                        "Failed to download $episodeName",
                        e
                    )
                }
            }
        }
        killDriver()
    }

    private data class QualityAndDownload(val quality: Quality, val downloadLink: String)

    private enum class ErrorCode(val code: Int) {
        NO_FRAME(0),
        IFRAME_FORBIDDEN(1),
        FAILED_EXTRACT_RES(2),
        NO_JS(3),
        CLOUDFLARE_FUCK(4),
        SIMPLE_MODE_FAILED(5);

        companion object {
            fun errorCodeForCode(code: Int?): ErrorCode? {
                if (code == null) {
                    return null
                }
                entries.forEach {
                    if (code == it.code) {
                        return it
                    }
                }
                return null
            }
        }
    }

    private suspend fun detectAvailableResolutions(
        slug: String,
        priorityQuality: Quality
    ): Resource<List<QualityAndDownload>> = withContext(Dispatchers.IO) {
        if (driver !is JavascriptExecutor) {
            return@withContext Resource.ErrorCode(ErrorCode.NO_JS.code)
        }
        val frameIds = listOf(
            "anime-js-0",
            "cizgi-js-0",
            "cizgi-video-js-0"
        )
        val fullLink = slug.slugToLink()
        val qualities = mutableListOf<QualityAndDownload>()
        if (simpleMode) {
            logInfo("Scraping resolution links from $fullLink in simple mode.")
            logInfo("Using UserAgent: $userAgent")
            var con: HttpsURLConnection? = null
            var reader: BufferedReader? = null
            try {
                con = wcoConnection(fullLink, false)
                reader = con.inputStream.bufferedReader()
                val sb = StringBuilder()
                reader.readLines().forEach {
                    sb.append(it).append("\n")
                }
                reader.close()
                con.disconnect()
                var frameLink = ""
                val doc = Jsoup.parse(sb.toString())
                for (id in frameIds) {
                    val iframe = doc.getElementById(id)
                    if (iframe != null) {
                        frameLink = iframe.attr("src")
                        break
                    }
                }
                if (frameLink.isEmpty()) {
                    return@withContext Resource.ErrorCode(
                        ErrorCode.SIMPLE_MODE_FAILED.code
                    )
                }
                //logInfo("Found simple mode frame link: $frameLink")
                val sbFrame = StringBuilder()
                for (i in 1..5) {
                    var frameCon: HttpsURLConnection? = null
                    var frameReader: BufferedReader? = null
                    try {
                        frameCon = wcoConnection(
                            frameLink,
                            supportEncoding = false,
                            addReferer = true
                        )
                        frameReader = frameCon.inputStream.bufferedReader()
                        frameReader.readLines().forEach {
                            sbFrame.append(it).append("\n")
                        }
                        frameReader.close()
                    } catch (e: Exception) {
                        try {
                            frameCon?.disconnect()
                            frameReader?.close()
                        } catch (_: Exception) {}
                    }
                    if (sbFrame.isNotEmpty()) {
                        break
                    }
                }
                if (sbFrame.isEmpty()) {
                    logError("Failed to visit frame url: $frameLink in simple mode.")
                    return@withContext Resource.ErrorCode(
                        ErrorCode.SIMPLE_MODE_FAILED.code
                    )
                }
                val src = sbFrame.toString()
                val linkKey1 = "\$.getJSON(\""
                val linkKey2 = "\", function(response){"
                val linkIndex1 = src.indexOf(linkKey1)
                val linkIndex2 = src.indexOf(linkKey2)
                val functionLink = src.substring(
                    linkIndex1 + linkKey1.length, linkIndex2
                )
                //idk how to execute the js, so we still have to use selenium.
                driver.navigate().to(fullLink)
                val has720 = src.contains("obj720")
                val has1080 = src.contains("obj1080")
                for (quality in Quality.qualityList(has720, has1080)) {
                    try {
                        executeJs(
                            JavascriptHelper.changeUrlToVideoFunction(
                                functionLink,
                                quality
                            )
                        )
                        delay(pageChangeWaitTime)
                        if (src.contains("404 Not Found")) {
                            logInfo(
                                "Failed to find $quality quality link for $slug in simple mode."
                            )
                            continue
                        }
                        val videoLink = driver.currentUrl
                        if (videoLink.isNotEmpty()) {
                            qualities.add(
                                QualityAndDownload(quality, videoLink)
                            )
                            logInfo(
                                "Found $quality link for $slug in simple mode."
                            )
                            if (quality == priorityQuality) {
                                break
                            }
                        }
                        driver.navigate().back()
                        delay(2000)
                    } catch (e: Exception) {
                        logError(
                            "An exception was thrown when looking for resolution links in simple mode.",
                            e
                        )
                        continue
                    }
                }
                if (qualities.isEmpty()) {
                    return@withContext Resource.ErrorCode(
                        ErrorCode.SIMPLE_MODE_FAILED.code
                    )
                } else {
                    logInfo("Found qualities with simple mode!")
                    return@withContext Resource.Success(qualities)
                }
            } catch (e: Exception) {
                try {
                    con?.disconnect()
                    reader?.close()
                } catch (_: Exception) {}
                e.printStackTrace()
                return@withContext Resource.ErrorCode(
                    ErrorCode.SIMPLE_MODE_FAILED.code
                )
            }
        }
        logInfo("Scraping resolution links from $fullLink")
        driver.navigate().to(fullLink)
        val wait = WebDriverWait(driver, Duration.ofSeconds(60))
        var foundVideoFrame = false
        for (id in frameIds) {
            if (!Core.child.isRunning) {
                break
            }
            try {
                wait.pollingEvery(Duration.ofSeconds(1))
                    .withTimeout(Duration.ofSeconds(10))
                    .until(
                        ExpectedConditions.visibilityOfElementLocated(
                            By.id(id)
                        )
                    )
                val frame = driver.findElement(
                    By.id(id)
                )
                val frameLink = frame.getAttribute("src")
                logInfo("Found frame for resolution with flag: $id and link: $frameLink")
                if (!frameLink.isNullOrEmpty()) {
                    logInfo("Executing resolution javascript function.")
                    //must redirect like this or else we get forbidden
                    executeJs(JavascriptHelper.changeUrl(frameLink))
                    logInfo("Javascript executed. Waiting ${pageChangeWaitTime / 1000} seconds for page change.")
                    delay(pageChangeWaitTime)
                    if (driver.pageSource.contains("403 Forbidden")) {
                        return@withContext Resource.ErrorCode(ErrorCode.IFRAME_FORBIDDEN.code)
                    } else if (driver.pageSource.contains("Sorry, you have been blocked")) {
                        return@withContext Resource.ErrorCode(ErrorCode.CLOUDFLARE_FUCK.code)
                    } else {
                        foundVideoFrame = true
                    }
                }
                break
            } catch (e: Exception) {
                logError(
                    "Failed to find flag $id for $slug. Trying next one.",
                    e
                )
            }
        }
        if (!foundVideoFrame) {
            return@withContext Resource.ErrorCode(ErrorCode.NO_FRAME.code)
        }

        val has720 = driver.pageSource.contains("obj720")
        val has1080 = driver.pageSource.contains("obj1080")
        for (quality in Quality.qualityList(has720, has1080)) {
            try {
                val src = driver.pageSource
                val linkKey1 = "\$.getJSON(\""
                val linkKey2 = "\", function(response){"
                val linkIndex1 = src.indexOf(linkKey1)
                val linkIndex2 = src.indexOf(linkKey2)
                val functionLink = src.substring(
                    linkIndex1 + linkKey1.length, linkIndex2
                )
                executeJs(
                    JavascriptHelper.changeUrlToVideoFunction(
                        functionLink,
                        quality
                    )
                )
                delay(pageChangeWaitTime)
                if (driver.pageSource.contains("404 Not Found")) {
                    logInfo(
                        "Failed to find $quality quality link for $slug"
                    )
                    continue
                }
                val videoLink = driver.currentUrl
                if (videoLink.isNotEmpty()) {
                    qualities.add(
                        QualityAndDownload(quality, videoLink)
                    )
                    logInfo(
                        "Found $quality link for $slug"
                    )
                    if (quality == priorityQuality) {
                        break
                    }
                }
                driver.navigate().back()
                delay(2000)
            } catch (e: Exception) {
                logError(
                    "An exception was thrown when looking for resolution links.",
                    e
                )
                continue
            }
        }
        return@withContext if (qualities.isEmpty()) {
            Resource.ErrorCode(ErrorCode.FAILED_EXTRACT_RES.code)
        } else {
            Resource.Success(qualities)
        }
    }

    private fun fileSize(link: String): Long {
        val con = wcoConnection(link)
        con.requestMethod = "HEAD"
        con.useCaches = false
        return con.contentLengthLong
    }

    private fun downloadVideo(url: String, output: File) {
        var offset = 0L
        if (output.exists()) {
            offset = output.length()
        }
        val con = wcoConnection(url)
        con.setRequestProperty("Range", "bytes=$offset-")
        val completeFileSize = con.contentLength + offset
        val buffer = ByteArray(8192)
        val bis = BufferedInputStream(con.inputStream)
        val fos = FileOutputStream(output, true)
        val bos = BufferedOutputStream(fos, buffer.size)
        var count: Int
        var total = offset
        val updater = DownloadUpdater(currentDownload)
        taskScope.launch { updater.run() }
        val startTime = System.nanoTime()
        while (bis.read(buffer).also { count = it } != -1) {
            if (!Core.child.isRunning) {
                writeMessage(
                    "Stopping video download at ${Tools.bytesToString(total)}/${
                        Tools.bytesToString(
                            completeFileSize
                        )
                    } for: ${currentDownload.name}"
                )
                break
            }
            total += count.toLong()
            bos.write(buffer, 0, count)
            val elapsedTime = System.nanoTime() - startTime
            val totalTime = (elapsedTime * currentDownload.fileSize) / total
            val remainingTime = (totalTime - elapsedTime) / 1_000_000_000
            updater.remainingSeconds = remainingTime.toInt()
        }
        updater.running = false
        bos.flush()
        bos.close()
        fos.flush()
        fos.close()
        bis.close()
        con.disconnect()
    }

    private fun wcoConnection(
        url: String,
        supportEncoding: Boolean = true,
        addReferer: Boolean = false
    ): HttpsURLConnection {
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
        return con
    }

    /**
     * Movies don't have quality options and are
     * handled on a different website since they're blocked
     * by a paywall.
     */
    private suspend fun handleMovie(movie: MovieHandler.Movie) = withContext(Dispatchers.IO) {
        val downloadFolderPath = Defaults.SAVE_FOLDER.string()
        var saveFolder = File(
            downloadFolderPath + File.separator + "Movies"
        )
        if (!saveFolder.exists() && !saveFolder.mkdirs()) {
            writeMessage(
                "Failed to create movies save folder: ${saveFolder.absolutePath} " +
                        "Defaulting to $downloadFolderPath"
            )
            saveFolder = File(downloadFolderPath + File.separator)
        }
        val episodeName = movie.name.fixForFiles()
        val saveFile = File(
            saveFolder.absolutePath + File.separator
                    + "$episodeName.mp4"
        )
        mCurrentDownload = downloadForSlugAndQuality(
            movie.slug,
            Quality.LOW
        )
        writeMessage("Detected movie for ${movie.slug}. Using movie mode.")
        if (mCurrentDownload != null) {
            if (currentDownload.downloadPath.isEmpty()
                || !File(currentDownload.downloadPath).exists()
                || currentDownload.downloadPath != saveFile.absolutePath
            ) {
                currentDownload.downloadPath = saveFile.absolutePath
            }
            Core.child.addDownload(currentDownload)
            if (currentDownload.isComplete) {
                writeMessage("[DB] Skipping completed video: " + movie.name)
                currentDownload.downloading = false
                currentDownload.queued = false
                Core.child.updateDownloadInDatabase(currentDownload, true)
                finishEpisode()
                return@withContext
            } else {
                currentDownload.queued = true
                Core.child.updateDownloadProgress(currentDownload)
            }
        }
        val link = MovieHandler.wcoMoviePlaylistLink + "${movie.tag}/${movie.slug}"
        driver.navigate().to(link)
        val wait = WebDriverWait(driver, Duration.ofSeconds(15))
        val downloadLink: String
        val videoLinkError: String
        try {
            val videoPlayer = driver.findElement(By.xpath("//*[@id=\"my-video\"]/div[2]/video"))
            wait.pollingEvery(Duration.ofSeconds(1))
                .withTimeout(Duration.ofSeconds(15))
                .until(ExpectedConditions.attributeToBeNotEmpty(videoPlayer, "src"))
            downloadLink = videoPlayer.getAttribute("src")
            videoLinkError = videoPlayer.getAttribute("innerHTML")
        } catch (e: Exception) {
            writeMessage(
                """
                    Failed to find video player for movie: $link
                    Retrying...
                """.trimIndent()
            )
            retries++
            return@withContext
        }
        if (downloadLink.isEmpty()) {
            if (videoLinkError.isNotEmpty()) {
                logInfo("Empty link source: \n${videoLinkError.trim()}")
            }
            writeMessage(
                "Failed to find video link for ${movie.slug.slugToLink()}. Retrying..."
            )
            retries++
            return@withContext
        }
        logInfo("Successfully found video link with $retries retries.")
        try {
            if (mCurrentDownload == null) {
                mCurrentDownload = Download()
                currentDownload.downloadPath = saveFile.absolutePath
                currentDownload.name = currentEpisode.name
                currentDownload.slug = currentEpisode.slug
                currentDownload.seriesSlug = currentEpisode.seriesSlug
                currentDownload.resolution = Quality.LOW.resolution
                currentDownload.dateAdded = System.currentTimeMillis()
                currentDownload.fileSize = 0
                currentDownload.queued = true
                Core.child.addDownload(currentDownload)
                logInfo("Created new download for ${currentEpisode.name}")
            } else {
                logInfo("Using existing download for ${currentEpisode.name}")
            }
            driver.navigate().to(downloadLink)
            val originalFileSize = fileSize(downloadLink)
            if (originalFileSize <= 5000) {
                writeMessage("Retrying... Failed to determine file size for: " + currentEpisode.name)
                retries++
                return@withContext
            }
            if (saveFile.exists()) {
                if (saveFile.length() >= originalFileSize) {
                    writeMessage("[IO] Skipping completed video: " + currentEpisode.name)
                    currentDownload.downloadPath = saveFile.absolutePath
                    currentDownload.fileSize = originalFileSize
                    currentDownload.downloading = false
                    currentDownload.queued = false
                    Core.child.updateDownloadInDatabase(currentDownload, true)
                    finishEpisode()
                    return@withContext
                }
            } else {
                try {
                    val created = saveFile.createNewFile()
                    if (!created) {
                        throw Exception("No error thrown.")
                    }
                } catch (e: Exception) {
                    logError(
                        "Unable to create video file for ${currentEpisode.name}",
                        e
                    )
                    writeMessage("Failed to create new video file for ${currentEpisode.name} Retrying...")
                    retries++
                    return@withContext
                }
            }
            writeMessage("Downloading: " + currentDownload.name)
            currentDownload.queued = false
            currentDownload.downloading = true
            currentDownload.fileSize = originalFileSize
            Core.child.addDownload(currentDownload)
            Core.child.updateDownloadInDatabase(currentDownload, true)
            driver.navigate().back()
            downloadVideo(downloadLink, saveFile)
            currentDownload.downloading = false
            //second time to ensure ui update
            Core.child.updateDownloadInDatabase(currentDownload, true)
            if (saveFile.exists() && saveFile.length() >= originalFileSize) {
                Core.child.incrementDownloadsFinished()
                writeMessage("Successfully downloaded: $episodeName")
                finishEpisode()
            }
        } catch (e: IOException) {
            currentDownload.queued = true
            currentDownload.downloading = false
            Core.child.updateDownloadInDatabase(currentDownload, true)
            writeMessage(
                """
                   Failed to download $episodeName
                   Error: ${e.localizedMessage}
                   Reattempting the download...
                """.trimIndent()
            )
            logError(
                "Failed to download $episodeName",
                e
            )
        }
    }

    private fun finishEpisode() {
        mCurrentEpisode = null
        Core.child.decrementDownloadsInProgress()
    }

    override fun killDriver() {
        taskScope.cancel()
        super.killDriver()
    }
}