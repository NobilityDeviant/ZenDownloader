package nobility.downloader.core.scraper.video_download

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
import nobility.downloader.core.scraper.MovieHandler
import nobility.downloader.core.scraper.video_download.m3u8_downloader.M3u8Downloads
import nobility.downloader.core.scraper.video_download.m3u8_downloader.core.M3u8Download
import nobility.downloader.core.scraper.video_download.m3u8_downloader.core.M3u8DownloadListener
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.config.HttpRequestManagerConfig
import nobility.downloader.core.settings.Defaults
import nobility.downloader.core.settings.Quality
import nobility.downloader.utils.*
import org.jsoup.Jsoup
import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.*
import java.net.URI
import java.time.Duration
import javax.net.ssl.HttpsURLConnection

/**
 * SimpleVideoDownloader is a video downloader that tries to skip the use
 * of selenium as much as possible.
 * Unlike the regular VideoDownloader, this one is a lot faster.
 * If this fails, the run function will return a boolean and if false,
 * it starts the normal VideoDownloader.
 */
class SimpleVideoDownloader(
    private val temporaryQuality: Quality? = null
) : DriverBase() {

    private var mCurrentEpisode: Episode? = null
    private var mCurrentDownload: Download? = null
    private val currentEpisode get() = mCurrentEpisode!!
    private val currentDownload get() = mCurrentDownload!!
    private var retries = 0
    private var resRetries = 0
    private var simpleRetries = 0
    private var m3u8Retries = 0
    private val pageChangeWaitTime = 5_000L //in milliseconds
    private val qualityAndDownloads = mutableListOf<QualityAndDownload>()
    private val taskScope = CoroutineScope(Dispatchers.Default)

    suspend fun run() = withContext(Dispatchers.IO) {
        while (Core.child.isRunning) {
            if (retries >= Constants.maxRetries) {
                if (mCurrentEpisode != null) {
                    writeMessage("Reached max retries of ${Constants.maxRetries}. Switching to normal mode.")
                    //todo
                }
                finishEpisode()
                resRetries = 0
                retries = 0
                m3u8Retries = 0
                if (simpleRetries < Constants.maxSimpleRetries) {
                    simpleRetries = 0
                }
                continue
            }
            if (simpleRetries >= Constants.maxSimpleRetries) {
                simpleRetries = 0
                if (mCurrentEpisode != null) {
                    writeMessage("Reached max simple mode retries. Switching to normal mode.")
                    //todo
                }
                continue
            }
            if (m3u8Retries >= Constants.maxM3U8Retries) {
                if (mCurrentEpisode != null) {
                    writeMessage(
                        "Reached max m3u8 retries of ${Constants.maxM3U8Retries}. Skipping download..."
                    )
                }
                finishEpisode()
                resRetries = 0
                retries = 0
                m3u8Retries = 0
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
                m3u8Retries = 0
                simpleRetries = 0
            }
            val slug = currentEpisode.slug
            if (slug.isEmpty()) {
                writeMessage("Skipping video with no slug.")
                finishEpisode()
                continue
            }
            if (temporaryQuality != null) {
                val tempDownload = downloadForSlugAndQuality(slug, temporaryQuality)
                if (tempDownload != null && tempDownload.isComplete) {
                    writeMessage("(DB) Skipping completed video.")
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
                                    "Failed to find frame for quality check. Retrying..."
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
                                    "Failed to extract quality links. Returned an empty list."
                                )
                            } else if (errorCode == ErrorCode.NO_JS) {
                                resRetries = 3
                                writeMessage("This browser doesn't support JavascriptExecutor.")
                            } else if (errorCode == ErrorCode.SIMPLE_MODE_FAILED) {
                                simpleRetries++
                                logError(
                                    "Failed in simple mode. Retrying with ${simpleRetries}/${Constants.maxSimpleRetries} left.",
                                    result.message
                                )
                                continue
                            } else if (errorCode == ErrorCode.M3U8_LINK_FAILED) {
                                m3u8Retries++
                                logError(
                                    "Failed to find m3u8 link."
                                )
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
                if (downloadLink.isEmpty()) {
                    retries++
                    logInfo("The download link is empty. Retrying...")
                    continue
                }
                val series = seriesForSlug(
                    currentEpisode.seriesSlug
                )
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
                        writeMessage("(DB) Skipping completed video.")
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
                        logInfo("Created new download.")
                    } else {
                        logInfo("Using existing download.")
                    }
                    if (downloadLink.endsWith(".m3u8")) {
                        if (saveFile.exists()) {
                            if (saveFile.length() >= 5000) {
                                writeMessage("(IO) Skipping completed video.")
                                currentDownload.downloadPath = saveFile.absolutePath
                                currentDownload.fileSize = saveFile.length()
                                currentDownload.manualProgress = true
                                currentDownload.downloading = false
                                currentDownload.queued = false
                                Core.child.updateDownloadInDatabase(
                                    currentDownload,
                                    true
                                )
                                finishEpisode()
                                continue
                            }
                        }
                        //writeMessage("m3u8Mode is starting with: $downloadLink")
                        val m3u8Download = M3u8Download.builder()
                            .setUri(URI(downloadLink))
                            .setFileName(saveFile.name)
                            .setWorkHome(System.getProperty("user.home") + "/.m3u8_files/$episodeName/")
                            .setTargetFiletDir(saveFile.parent)
                            .deleteTsOnComplete()
                            .forceCacheAssignmentBasedOnFileName()
                            .setRetryCount(3)
                            .addHttpHeader("Accept", "*/*")
                            .addHttpHeader("Cache-Control", "no-cache")
                            //.addHttpHeader("User-Agent", userAgent)
                            .addListener(object: M3u8DownloadListener {
                                override fun downloadStarted(m3u8Download: M3u8Download) {
                                    writeMessage("Starting m3u8 download with ${qualityOption.tag} quality.")
                                    currentDownload.queued = false
                                    currentDownload.downloading = true
                                    currentDownload.manualProgress = true
                                    Core.child.updateDownloadInDatabase(
                                        currentDownload,
                                        true
                                    )
                                }

                                override fun downloadProgress(
                                    downloadPercentage: String,
                                    remainingSeconds: Int,
                                    remainingTsCount: Int,
                                    totalTsCount: Int
                                ) {
                                    currentDownload.setProgressValue(
                                        downloadPercentage +
                                                " ts: $remainingTsCount/$totalTsCount"
                                    )
                                    Core.child.updateDownloadProgress(
                                        currentDownload,
                                        remainingSeconds
                                    )
                                }

                                override fun downloadSizeUpdated(fileSize: Long) {
                                    currentDownload.fileSize = fileSize
                                    Core.child.updateDownloadInDatabase(
                                        currentDownload,
                                        true
                                    )
                                }

                                override fun downloadFinished(
                                    m3u8Download: M3u8Download,
                                    complete: Boolean
                                ) {
                                    if (!complete) {
                                        throw Exception("Failed to download m3u8 file.")
                                    }
                                    logInfo("Finished m3u8 download.")
                                    currentDownload.downloading = false
                                    Core.child.updateDownloadInDatabase(
                                        currentDownload,
                                        true
                                    )
                                }

                                override fun onMergeStarted(m3u8Download: M3u8Download) {
                                    currentDownload.merging = true
                                    Core.child.updateDownloadInDatabase(
                                        currentDownload,
                                        true
                                    )
                                }

                                override fun onMergeFinished(m3u8Download: M3u8Download) {
                                    currentDownload.merging = false
                                    currentDownload.fileSize = saveFile.length()
                                    Core.child.updateDownloadInDatabase(
                                        currentDownload,
                                        true
                                    )
                                    if (saveFile.exists() && saveFile.length() >= 5000) {
                                        Core.child.incrementDownloadsFinished()
                                        writeMessage("Successfully downloaded with ${qualityOption.tag} quality.")
                                        finishEpisode()
                                    }
                                }

                            }).build()

                        try {
                            M3u8Downloads.download(
                                HttpRequestManagerConfig.custom()
                                    .userAgent(userAgent)
                                    .setTimeoutMillis((Defaults.TIMEOUT.int() * 1000))
                                    .defaultMaxRetries(10)
                                    .build(),
                                m3u8Download
                            )
                        } catch (e: Exception) {
                            retries++
                            logError(
                                "Failed to download m3u8 video. Retrying...",
                                e
                            )
                            continue
                        }
                    } else {
                        val originalFileSize = fileSize(downloadLink)
                        if (originalFileSize <= 5000) {
                            writeMessage("Failed to determine file size. Retrying...")
                            retries++
                            continue
                        }
                        if (saveFile.exists()) {
                            if (saveFile.length() >= originalFileSize) {
                                writeMessage("(IO) Skipping completed video.")
                                currentDownload.downloadPath = saveFile.absolutePath
                                currentDownload.fileSize = originalFileSize
                                currentDownload.downloading = false
                                currentDownload.queued = false
                                Core.child.updateDownloadInDatabase(
                                    currentDownload,
                                    true
                                )
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
                                    "Failed to create video file. Retrying...",
                                    e,
                                    true
                                )
                                retries++
                                continue
                            }
                        }
                        writeMessage("Starting download with ${qualityOption.tag} quality.")
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
                            writeMessage("Successfully downloaded with ${qualityOption.tag} quality.")
                            finishEpisode()
                        }
                    }
                } catch (e: IOException) {
                    currentDownload.queued = true
                    currentDownload.downloading = false
                    Core.child.updateDownloadInDatabase(
                        currentDownload,
                        true
                    )
                    logError(
                        "Failed to download. Retrying...",
                        e,
                        true
                    )
                }
            }
        }
        killDriver()
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
            "cizgi-video-js-0",
            "anime-js-1"
        )
        var m3u8Mode = false
        val fullLink = slug.slugToLink()
        val qualities = mutableListOf<QualityAndDownload>()

        logInfo("Scraping quality links from $fullLink")
        logInfo("Using UserAgent: $userAgent")
        try {
            val source = readUrlLines(fullLink, false)
            if (source.isFailed) {
                return@withContext Resource.ErrorCode(
                    source.message,
                    ErrorCode.SIMPLE_MODE_FAILED.code
                )
            }
            var frameLink = ""
            val doc = Jsoup.parse(source.data.toString())
            for (id in frameIds) {
                val iframe = doc.getElementById(id)
                if (iframe != null) {
                    frameLink = iframe.attr("src")
                    if (id == "anime-js-1") {
                        m3u8Mode = true
                    }
                    break
                }
            }
            if (frameLink.isEmpty()) {
                return@withContext Resource.ErrorCode(
                    ErrorCode.SIMPLE_MODE_FAILED.code
                )
            }
            var sbFrame = StringBuilder()
            for (i in 1..5) {
                val frameSource = readUrlLines(frameLink)
                if (frameSource.data != null) {
                    sbFrame = frameSource.data
                }
                if (sbFrame.isNotEmpty()) {
                    break
                }
            }
            if (sbFrame.isEmpty()) {
                return@withContext Resource.ErrorCode(
                    "Failed to read frame source.",
                    ErrorCode.SIMPLE_MODE_FAILED.code
                )
            }
            if (m3u8Mode) {
                var hslLink = ""
                var domain = ""
                logInfo("Detected m3u8 frame.")
                val linkKey = "\"src\": \"ht"
                for (line in sbFrame.lines()) {
                    if (line.contains(linkKey)) {
                        hslLink = line.substringAfter("\"src\": \"")
                            .substringBeforeLast("\"")
                        domain = hslLink.substringBeforeLast("/")
                        /*if (hslLink.isNotEmpty() && domain.isNotEmpty()) {
                            logInfo(
                                "Found m3u8 link: $hslLink" +
                                        "\nWith the domain: $domain"
                            )
                        }*/
                        break
                    }
                }
                if (hslLink.isEmpty() || domain.isEmpty()) {
                    return@withContext Resource.ErrorCode(
                        "Failed to find m3u8 link in the source.",
                        ErrorCode.M3U8_LINK_FAILED.code
                    )
                }
                val hslSource = readUrlLines(hslLink)
                if (hslSource.data != null) {
                    val hslLines = hslSource.data.lines()
                    hslLines.forEachIndexed { index, s ->
                        if (s.startsWith("#EXT-X-STREAM-INF")) {
                            Quality.qualityList().forEach { quality ->
                                if (s.contains("x${quality.resolution}")) {
                                    qualities.add(
                                        QualityAndDownload(
                                            quality,
                                            domain + "/" + hslLines[index + 1]
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                if (qualities.isEmpty()) {
                    return@withContext Resource.ErrorCode(
                        "Failed to find m3u8 qualities.",
                        ErrorCode.M3U8_LINK_FAILED.code
                    )
                } else {
                    logInfo("Successfully found m3u8 qualities.")
                    return@withContext Resource.Success(qualities)
                }
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
                        "An exception was thrown when looking for quality links.",
                        e
                    )
                    continue
                }
            }
            if (qualities.isEmpty()) {
                return@withContext Resource.ErrorCode(
                    "Failed to find qualities.",
                    ErrorCode.SIMPLE_MODE_FAILED.code
                )
            } else {
                logInfo("Successfully found qualities.")
                return@withContext Resource.Success(qualities)
            }
        } catch (e: Exception) {
            return@withContext Resource.ErrorCode(
                e,
                ErrorCode.SIMPLE_MODE_FAILED.code
            )
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
                logInfo("Created new download.")
            } else {
                logInfo("Using existing download.")
            }
            driver.navigate().to(downloadLink)
            val originalFileSize = fileSize(downloadLink)
            if (originalFileSize <= 5000) {
                writeMessage("Retrying. Failed to determine file size.")
                retries++
                return@withContext
            }
            if (saveFile.exists()) {
                if (saveFile.length() >= originalFileSize) {
                    writeMessage("[IO Skipping completed video.")
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

    private suspend fun readUrlLines(
        url: String,
        addReferer: Boolean = true
    ): Resource<StringBuilder> = withContext(Dispatchers.IO) {
        var con: HttpsURLConnection? = null
        var reader: BufferedReader? = null
        val sb = StringBuilder()
        try {
            con = wcoConnection(url, false, addReferer)
            reader = con.inputStream.bufferedReader()
            reader.readLines().forEach {
                sb.append(it).append("\n")
            }
            return@withContext Resource.Success(sb)
        } catch (e: Exception) {
            return@withContext Resource.Error(e)
        } finally {
            try {
                con?.disconnect()
                reader?.close()
            } catch (_: Exception) {
            }
        }
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

    private val tag get() = if (mCurrentEpisode != null) "[S] [${currentEpisode.name}]" else "[S] [No Episode]"

    private fun writeMessage(s: String) {
        FrogLog.writeMessage("$tag $s")
    }

    private fun logInfo(s: String) {
        FrogLog.logInfo(
            "$tag $s"
        )
    }

    private fun logError(
        message: String? = null,
        e: Throwable,
        important: Boolean = false
    ) {
        FrogLog.logError(
            "$tag $message",
            e,
            important
        )
    }

    private fun logError(
        message: String,
        errorMessage: String? = null,
        important: Boolean = false
    ) {
        FrogLog.logError(
            "$tag $message",
            errorMessage,
            important
        )
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