package nobility.downloader.core.scraper

import kotlinx.coroutines.*
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.BoxHelper.Companion.downloadForSlugAndQuality
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.BoxHelper.Companion.wcoSeriesForSlug
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
import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.*
import java.net.URI
import java.net.URL
import java.time.Duration
import javax.net.ssl.HttpsURLConnection

class VideoDownloader(
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

    suspend fun run() = withContext(Dispatchers.IO) {
        while (Core.child.isRunning) {
            if (retries >= Constants.maxRetries) {
                if (mCurrentEpisode != null) {
                    writeMessage("Reached max retries of ${Constants.maxRetries} for ${currentEpisode.name}. Skipping download...")
                }
                finishEpisode()
                resRetries = 0
                retries = 0
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
            }
            val slug = currentEpisode.slug
            if (slug.isEmpty()) {
                writeMessage("Skipping video: (${currentEpisode.name}) with no slug.")
                finishEpisode()
                continue
            }
            if (currentEpisode.isMovie || Core.child.movieHandler.movieForSlug(slug) != null) {
                handleMovie()
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
                val series = wcoSeriesForSlug(currentEpisode.seriesSlug)
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
                    driver.get(link)
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
                        //this makes it wait, so it doesn't throw an error everytime
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
                    driver.get(downloadLink)
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
        CLOUDFLARE_FUCK(4);

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
    ): Resource<List<QualityAndDownload>> {
        if (driver !is JavascriptExecutor) {
            return Resource.ErrorCode(ErrorCode.NO_JS.code)
        }
        val fullLink = slug.slugToLink()
        logInfo("Scraping resolution links from $fullLink")
        val qualities = mutableListOf<QualityAndDownload>()
        driver.get(fullLink)
        val wait = WebDriverWait(driver, Duration.ofSeconds(60))
        val frameIds = listOf(
            "anime-js-0",
            "cizgi-js-0",
            "cizgi-video-js-0"
        )
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
                    executeJs(JavascriptHelper.advancedChangeUrl(frameLink))
                    logInfo("Javascript executed. Waiting ${pageChangeWaitTime / 1000} seconds for page change.")
                    delay(pageChangeWaitTime)
                    if (driver.pageSource.contains("403 Forbidden")) {
                        return Resource.ErrorCode(ErrorCode.IFRAME_FORBIDDEN.code)
                    } else if (driver.pageSource.contains("Sorry, you have been blocked")) {
                        return Resource.ErrorCode(ErrorCode.CLOUDFLARE_FUCK.code)
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
            return Resource.ErrorCode(ErrorCode.NO_FRAME.code)
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
                val functionLink = driver.pageSource.substring(
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
        return if (qualities.isEmpty()) {
            Resource.ErrorCode(ErrorCode.FAILED_EXTRACT_RES.code)
        } else {
            Resource.Success(qualities)
        }
    }

    private fun fileSize(link: String): Long {
        val con = wcoConnection(URI.create(link).toURL())
        con.requestMethod = "HEAD"
        con.useCaches = false
        return con.contentLengthLong
    }

    private fun downloadVideo(url: String, output: File) {
        var offset = 0L
        if (output.exists()) {
            offset = output.length()
        }
        val con = wcoConnection(URI(url).toURL())
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

    private fun wcoConnection(url: URL): HttpsURLConnection {
        val con = url.openConnection() as HttpsURLConnection
        con.addRequestProperty(
            "Accept",
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
        )
        con.addRequestProperty("Accept-Encoding", "gzip, deflate, br")
        con.addRequestProperty("Accept-Language", "en-US,en;q=0.9")
        con.addRequestProperty("Connection", "keep-alive")
        con.addRequestProperty("Sec-Fetch-Dest", "document")
        con.addRequestProperty("Sec-Fetch-Mode", "navigate")
        con.addRequestProperty("Sec-Fetch-Site", "cross-site")
        con.addRequestProperty("Sec-Fetch-User", "?1")
        con.addRequestProperty("Upgrade-Insecure-Requests", "1")
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
    private suspend fun handleMovie() = withContext(Dispatchers.IO) {
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
        val episodeName = currentEpisode.name.fixForFiles()
        val saveFile = File(
            saveFolder.absolutePath + File.separator
                    + "$episodeName.mp4"
        )
        val slug = currentEpisode.slug
        mCurrentDownload = downloadForSlugAndQuality(
            slug,
            Quality.LOW
        )
        writeMessage("Detected movie for $slug. Using movie mode.")
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
                return@withContext
            } else {
                currentDownload.queued = true
                Core.child.updateDownloadProgress(currentDownload)
            }
        }
        val link = MovieHandler.wcoMoviePlaylistLink + slug
        driver.get(link)
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
                "Failed to find video link for ${slug.slugToLink()}. Retrying..."
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
            driver.get(downloadLink)
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
        super.killDriver()
        taskScope.cancel()
    }
}