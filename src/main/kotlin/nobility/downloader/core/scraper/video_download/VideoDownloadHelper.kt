package nobility.downloader.core.scraper.video_download

import kotlinx.coroutines.*
import nobility.downloader.core.BoxHelper.Companion.downloadForNameAndQuality
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Download
import nobility.downloader.core.scraper.data.ParsedQuality
import nobility.downloader.core.scraper.data.QualityAndDownload
import nobility.downloader.core.scraper.video_download.Functions.downloadVideo
import nobility.downloader.core.scraper.video_download.Functions.fileSize
import nobility.downloader.core.scraper.video_download.Functions.httpRequestConfig
import nobility.downloader.core.scraper.video_download.Functions.m3u8Download
import nobility.downloader.core.scraper.video_download.Functions.readUrlLines
import nobility.downloader.core.scraper.video_download.m3u8_downloader.M3u8Downloads
import nobility.downloader.core.scraper.video_download.m3u8_downloader.core.M3u8Download
import nobility.downloader.core.scraper.video_download.m3u8_downloader.core.M3u8DownloadListener
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.VideoUtil
import nobility.downloader.core.settings.Defaults
import nobility.downloader.core.settings.Quality
import nobility.downloader.utils.*
import org.jsoup.Jsoup
import org.openqa.selenium.JavascriptExecutor
import java.io.File

/**
 * Just used to make the video handler smaller and easier to manage.
 */
class VideoDownloadHelper(
    val temporaryQuality: Quality?
) {

    val data = VideoDownloadData(temporaryQuality)

    suspend fun parseQualities(
        slug: String
    ): Resource<ParsedQuality> {
        var qualityOption = temporaryQuality ?: Quality.qualityForTag(
            Defaults.QUALITY.string()
        )
        var downloadLink = ""
        var separateAudioLink = ""
        if (data.qualityAndDownloads.isEmpty()) {
            if (data.resRetries < Defaults.QUALITY_RETRIES.int()) {
                val result = detectAvailableQualities(slug, qualityOption)
                if (result.errorCode != -1) {
                    val errorCode = ErrorCode.errorCodeForCode(result.errorCode)
                    if (errorCode == ErrorCode.NO_FRAME) {
                        data.resRetries++
                        data.logError(
                            "Failed to find frame for quality check. Retrying..."
                        )
                        return Resource.Error()
                    } else if (errorCode == ErrorCode.CLOUDFLARE_FUCK) {
                        data.logError(
                            """
                               Cloudflare has blocked our request.
                               Unfortunately that means we can't proceed, but well continue anyways...
                            """.trimIndent()
                        )
                        return Resource.Error()
                    } else if (errorCode == ErrorCode.IFRAME_FORBIDDEN || errorCode == ErrorCode.EMPTY_FRAME) {
                        data.resRetries = 3
                        data.logError(
                            "Failed to find video frame for: $slug" +
                                    "\nPlease report this in github issues with the video you are trying to download."
                        )
                        return Resource.Error()
                    } else if (errorCode == ErrorCode.FAILED_EXTRACT_RES) {
                        data.resRetries = 3
                        data.logError(
                            "Failed to extract quality links. Returned an empty list."
                        )
                        return Resource.Error()
                    } else if (errorCode == ErrorCode.NO_JS) {
                        data.resRetries = 3
                        data.writeMessage("This browser doesn't support JavascriptExecutor.")
                    } else if (errorCode == ErrorCode.M3U8_LINK_FAILED) {
                        data.retries++
                        data.logError(
                            "",
                            errorMessage = result.message
                        )
                        return Resource.Error()
                    } else if (errorCode == ErrorCode.FAILED_PAGE_READ) {
                        data.retries++
                        data.logError(
                            "Failed to read webpage."
                        )
                        return Resource.Error()
                    }
                }
                if (result.data != null) {
                    data.qualityAndDownloads.addAll(result.data)
                    val firstQualities = result.data.filter { !it.secondFrame }
                    qualityOption = Quality.bestQuality(
                        qualityOption,
                        firstQualities.map { it.quality }
                    )
                    firstQualities.forEach {
                        if (it.quality == qualityOption) {
                            downloadLink = it.downloadLink
                            separateAudioLink = it.separateAudioLink
                        }
                    }
                } else {
                    data.logInfo(
                        "Failed to find quality download links. Defaulting to ${Quality.LOW.tag} quality."
                    )
                    qualityOption = Quality.LOW
                }
            }
        } else {
            val firstQualities = data.qualityAndDownloads.filter { !it.secondFrame }
            qualityOption = Quality.bestQuality(
                qualityOption,
                firstQualities.map { it.quality }
            )
            firstQualities.forEach {
                if (it.quality == qualityOption) {
                    downloadLink = it.downloadLink
                    separateAudioLink = it.separateAudioLink
                }
            }
        }
        if (downloadLink.isEmpty()) {
            data.retries++
            data.logInfo("The download link is empty. Retrying...")
            return Resource.Error()
        }
        return Resource.Success(
            ParsedQuality(
                qualityOption,
                downloadLink,
                separateAudioLink
            )
        )
    }

    suspend fun detectAvailableQualities(
        slug: String,
        priorityQuality: Quality
    ): Resource<List<QualityAndDownload>> = withContext(Dispatchers.IO) {
        if (data.driver !is JavascriptExecutor) {
            return@withContext Resource.ErrorCode(ErrorCode.NO_JS.code)
        }
        val frameIds = listOf(
            "anime-js-0",
            "cizgi-js-0",
            "anime-js-1"
        )
        val secondFrameId = "cizgi-js-1"
        var m3u8Mode = false
        val fullLink = slug.slugToLink()
        val qualityAndDownloads = mutableListOf<QualityAndDownload>()

        data.logInfo("Scraping quality links from $fullLink")
        data.logInfo("Using UserAgent: ${data.userAgent}")

        try {
            val source = readUrlLines(fullLink, data, false)
            if (source.isFailed) {
                return@withContext Resource.ErrorCode(
                    source.message,
                    ErrorCode.FAILED_PAGE_READ.code
                )
            }
            var frameLink = ""
            var secondFrameLink = ""
            val doc = Jsoup.parse(source.data.toString())
            for (id in frameIds) {
                val iframe = doc.getElementById(id)
                if (iframe != null) {
                    frameLink = iframe.attr("src")
                    if (id == "anime-js-1") {
                        m3u8Mode = true
                    }
                    data.logInfo("Found frame with id: $id")
                    break
                }
            }
            if (!m3u8Mode) {
                val iframe = doc.getElementById(secondFrameId)
                if (iframe != null) {
                    secondFrameLink = iframe.attr("src")
                    data.logInfo("Found second frame with id: $secondFrameId")
                }
            }
            if (frameLink.isEmpty()) {
                return@withContext Resource.ErrorCode(
                    ErrorCode.EMPTY_FRAME.code
                )
            }
            var sbFrame = StringBuilder()
            @Suppress("UNUSED")
            for (i in 1..5) {
                val frameSource = readUrlLines(frameLink, data)
                if (frameSource.data != null) {
                    sbFrame = frameSource.data
                }
                if (sbFrame.isNotEmpty()) {
                    break
                }
            }
            var sbFrame2 = StringBuilder()
            if (secondFrameLink.isNotEmpty()) {
                @Suppress("UNUSED")
                for (i in 1..5) {
                    val frameSource = readUrlLines(secondFrameLink, data)
                    if (frameSource.data != null) {
                        sbFrame2 = frameSource.data
                    }
                    if (sbFrame2.isNotEmpty()) {
                        break
                    }
                }
            }
            if (sbFrame.isEmpty()) {
                return@withContext Resource.ErrorCode(
                    "Failed to read frame source.",
                    ErrorCode.EMPTY_FRAME.code
                )
            }
            if (m3u8Mode) {
                var hslLink = ""
                var domain = ""
                val frameString = sbFrame.toString()
                val frameDoc = Jsoup.parse(frameString)
                val source = frameDoc.getElementsByTag("source").firstOrNull()
                if (source != null) {
                    hslLink = source.attr("src")
                    domain = hslLink.substringBeforeLast("/")
                } else {
                    val linkKey = "\"src\": \"ht"
                    for (line in sbFrame.lines()) {
                        if (line.contains(linkKey)) {
                            hslLink = line.substringAfter("\"src\": \"")
                                .substringBeforeLast("\"")
                            domain = hslLink.substringBeforeLast("/")
                            break
                        }
                    }
                }
                if (hslLink.isEmpty() || domain.isEmpty()) {
                    return@withContext Resource.ErrorCode(
                        "Failed to find m3u8 link in the source.",
                        ErrorCode.M3U8_LINK_FAILED.code
                    )
                }
                val hslSource = readUrlLines(hslLink, data)
                if (hslSource.data != null) {
                    val hslLines = hslSource.data.lines()
                    hslLines.forEachIndexed { index, s ->
                        if (s.startsWith("#EXT-X-STREAM-INF")) {
                            Quality.qualityList().forEach { quality ->
                                if (s.contains("x${quality.resolution}")) {
                                    var separateAudio = false
                                    hslLines.forEach { t ->
                                        if (t.startsWith("#EXT-X-MEDIA:TYPE=AUDIO")) {
                                            if (t.contains("LANGUAGE=\"eng\"")) {
                                                val last = t.split(",").last()
                                                val audioUrl =
                                                    domain + "/" + last.substringAfter("\"").substringBeforeLast("\"")
                                                //FrogLog.logInfo("Found separate english audio for m3u8.")
                                                separateAudio = true
                                                qualityAndDownloads.add(
                                                    QualityAndDownload(
                                                        quality,
                                                        domain + "/" + hslLines[index + 1],
                                                        separateAudioLink = audioUrl
                                                    )
                                                )
                                                return@forEach
                                            }
                                        }
                                    }
                                    if (!separateAudio) {
                                        qualityAndDownloads.add(
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
                }
                if (qualityAndDownloads.isEmpty()) {
                    return@withContext Resource.ErrorCode(
                        "Failed to find m3u8 qualities.",
                        ErrorCode.M3U8_LINK_FAILED.code
                    )
                } else {
                    data.logInfo("Successfully found m3u8 qualities.")
                    return@withContext Resource.Success(qualityAndDownloads)
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
            data.undriver.go(fullLink)
            val has720 = src.contains("obj720")
            val has1080 = src.contains("obj1080")
            var qualityIndex = 0
            var qualityRetry = mutableMapOf<String, Int>()
            val qualityList = Quality.qualityList(has720, has1080)
            while (qualityIndex < qualityList.size) {
                val quality = qualityList[qualityIndex]
                try {
                    data.base.executeJs(
                        JavascriptHelper.changeUrlToVideoFunction(
                            functionLink,
                            quality
                        )
                    )
                    delay(data.pageChangeWaitTime)
                    //a new way to check if the javascript script threw an error.
                    //JavascriptHelper will change the url to blank.org on a jquery error.
                    //idk how to show the error, but we can at least do this
                    if (data.driver.currentUrl == "https://blank.org") {
                        val retry = qualityRetry[quality.tag]
                        if (retry != null) {
                            if (retry > 3) {
                                data.logError(
                                    "Failed to find ${quality.tag} quality after 3 retries. | Skipping quality"
                                )
                                qualityIndex++
                                continue
                            }
                            qualityRetry.put(quality.tag, retry + 1)
                            data.logError("The script has failed for quality: ${quality.tag} | Retrying... ($retry)")
                        } else {
                            qualityRetry.put(quality.tag, 1)
                            data.logError("The script has failed for quality: ${quality.tag} | Retrying... (1)")
                        }
                        continue
                    }
                    if (src.contains("404 Not Found") || src.contains("404 - Page not Found")) {
                        data.logError(
                            "(404) Failed to find $quality quality. | Skipping quality"
                        )
                        qualityIndex++
                        continue
                    }
                    val videoLink = data.driver.url()
                    if (videoLink.isNotEmpty()) {
                        qualityAndDownloads.add(
                            QualityAndDownload(quality, videoLink)
                        )
                        data.logInfo(
                            "Found $quality quality."
                        )
                        if (quality == priorityQuality) {
                            break
                        }
                    }
                    data.driver.navigate().back()
                    qualityIndex++
                } catch (e: Exception) {
                    val retry = qualityRetry[quality.tag]
                    if (retry != null) {
                        if (retry > 3) {
                            data.logError(
                                "An exception was thrown when looking for quality links after 3 retries. | Skipping quality",
                                e
                            )
                            qualityIndex++
                            continue
                        }
                        qualityRetry.put(quality.tag, retry + 1)
                        data.logError(
                            "An exception was thrown when looking for quality links. | Retrying... ($retry)",
                            e.localizedMessage
                        )
                    } else {
                        qualityRetry.put(quality.tag, 1)
                        data.logError(
                            "An exception was thrown when looking for quality links. | Retrying... (1)",
                            e.localizedMessage
                        )
                    }
                    continue
                }
            }
            /*for (quality in Quality.qualityList(has720, has1080)) {
                try {
                    data.base.executeJs(
                        JavascriptHelper.changeUrlToVideoFunction(
                            functionLink,
                            quality
                        )
                    )
                    delay(data.pageChangeWaitTime)
                    if (src.contains("404 Not Found") || src.contains("404 - Page not Found")) {
                        data.logInfo(
                            "(404) Failed to find $quality quality link for $slug"
                        )
                        continue
                    }
                    val videoLink = data.driver.currentUrl
                    if (videoLink.isNotEmpty()) {
                        qualityAndDownloads.add(
                            QualityAndDownload(quality, videoLink)
                        )
                        data.logInfo(
                            "Found $quality quality."
                        )
                        if (quality == priorityQuality) {
                            break
                        }
                    }
                    data.driver.navigate().back()
                } catch (e: Exception) {
                    data.logError(
                        "An exception was thrown when looking for quality links.",
                        e
                    )
                    continue
                }
            }*/
            if (sbFrame2.isNotEmpty()) {
                val src2 = sbFrame2.toString()
                val secondLinkIndex1 = src2.indexOf(linkKey1)
                val secondLinkIndex2 = src2.indexOf(linkKey2)
                val secondFunctionLink = src2.substring(
                    secondLinkIndex1 + linkKey1.length, secondLinkIndex2
                )
                data.driver.navigate().to(fullLink)
                val secondHas720 = src2.contains("obj720")
                val secondHas1080 = src2.contains("obj1080")
                //timeout just in case it hangs for too long.
                //withTimeout((Defaults.TIMEOUT.int() * 1000).toLong() * 3) {
                for (quality in Quality.qualityList(secondHas720, secondHas1080)) {
                    try {
                        data.base.executeJs(
                            JavascriptHelper.changeUrlToVideoFunction(
                                secondFunctionLink,
                                quality
                            )
                        )
                        delay(data.pageChangeWaitTime)
                        if (src.contains("404 Not Found") || src.contains("404 - Page not Found")) {
                            data.logInfo(
                                "(2nd) (404) Failed to find $quality quality link for $slug"
                            )
                            continue
                        }
                        val videoLink = data.driver.url()
                        if (videoLink.isNotEmpty()) {
                            qualityAndDownloads.add(
                                QualityAndDownload(quality, videoLink, true)
                            )
                            data.logInfo(
                                "(2nd) Found $quality link for $slug"
                            )
                            if (quality == priorityQuality) {
                                break
                            }
                        }
                        data.driver.navigate().back()
                        //delay(2000)
                    } catch (e: Exception) {
                        data.logError(
                            "(2nd) An exception was thrown when looking for quality links.",
                            e
                        )
                        continue
                    }
                }
            }
            //}
            if (qualityAndDownloads.isEmpty()) {
                return@withContext Resource.Error(
                    "Failed to find qualities."
                )
            } else {
                data.logInfo("Successfully found qualities.")
                return@withContext Resource.Success(qualityAndDownloads)
            }
        } catch (e: Exception) {
            return@withContext Resource.Error(e)
        }
    }

    suspend fun handleM3U8(
        parsedQuality: ParsedQuality,
        saveFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        //we have to listen to the exception this way.
        //throwing an exception in listener doesn't affect this scope.
        var exx: Exception? = null
        val job = launch(Dispatchers.Default) {
            while (isActive) {
                if (exx != null) {
                    cancel("", exx)
                }
            }
        }
        val m3u8Path = System.getProperty("user.home") + "/.m3u8_files/${saveFile.nameWithoutExtension}/"
        if (saveFile.exists()) {
            if (saveFile.length() >= 5000) {
                data.writeMessage("(IO) Skipping completed video.")
                data.currentDownload.downloadPath = saveFile.absolutePath
                data.currentDownload.fileSize = saveFile.length()
                data.currentDownload.manualProgress = true
                data.currentDownload.downloading = false
                data.currentDownload.queued = false
                data.currentDownload.update()
                data.finishEpisode()
                return@withContext false
            }
        }
        val hasSeparateAudio = parsedQuality.separateAudioLink.isNotEmpty()
        val downloads = mutableListOf<M3u8Download>()
        val videoListener = M3u8DownloadListener(
            downloadStarted = {
                data.writeMessage("Starting m3u8 video download with ${parsedQuality.quality.tag} quality.")
                data.currentDownload.queued = false
                data.currentDownload.downloading = true
                data.currentDownload.manualProgress = true
                data.currentDownload.update()
            },
            downloadFinished = { download, success ->
                if (!success) {
                    exx = Exception("Failed to download m3u8 file. Incomplete ts files.")
                    return@M3u8DownloadListener
                }
                data.logInfo("Finished m3u8 video download.")
                if (!hasSeparateAudio) {
                    data.currentDownload.downloading = false
                    data.currentDownload.update()
                }
            },
            downloadProgress = { progress, seconds ->
                Core.child.m3u8UpdateDownloadProgress(
                    data.currentDownload,
                    progress,
                    true,
                    seconds
                )
            },
            downloadSizeUpdated = {
                data.currentDownload.fileSize = it
                data.currentDownload.update()
            },
            onMergeStarted = {
                Core.child.m3u8UpdateDownloadProgress(
                    data.currentDownload,
                    "Merging video ts files",
                    true,
                    0
                )
            },
            onMergeFinished = { download, ex ->
                if (ex == null) {
                    Core.child.m3u8UpdateDownloadProgress(
                        data.currentDownload,
                        "Finalizing video",
                        true,
                        0
                    )
                    data.currentDownload.fileSize = saveFile.length()
                    data.currentDownload.update()
                    if (saveFile.exists() && saveFile.length() >= 5000) {
                        if (!hasSeparateAudio) {
                            data.currentDownload.downloading = false
                            data.currentDownload.update()
                            Core.child.incrementDownloadsFinished()
                            data.writeMessage("Successfully downloaded with ${parsedQuality.quality.tag} quality.")
                            data.finishEpisode()
                            if (job.isActive) {
                                job.cancel()
                            }
                            File(m3u8Path).deleteRecursively()
                        }
                    } else {
                        exx = Exception("Video merge failed to save to disk.")
                    }
                } else {
                    exx = ex
                }
            }
        )
        downloads.add(
            m3u8Download(
                parsedQuality.downloadLink,
                saveFile,
                videoListener
            )
        )
        val audioSaveFile = File(
            saveFile.parent,
            saveFile.nameWithoutExtension + ".m4a"
        )
        if (hasSeparateAudio) {
            val audioListener = M3u8DownloadListener(
                downloadStarted = {
                    if (exx != null) {
                        return@M3u8DownloadListener
                    }
                    data.logInfo("Downloading separate audio for video.")
                },
                downloadFinished = { download, success ->
                    if (exx != null) {
                        return@M3u8DownloadListener
                    }
                    if (!success) {
                        exx = Exception("Failed to download m3u8 audio.")
                        return@M3u8DownloadListener
                    }
                    data.logInfo("Finished downloading audio file.")
                },
                downloadProgress = { progress, seconds ->
                    Core.child.m3u8UpdateDownloadProgress(
                        data.currentDownload,
                        progress,
                        false,
                        seconds
                    )
                },
                onMergeStarted = {
                    if (exx != null) {
                        return@M3u8DownloadListener
                    }
                    Core.child.m3u8UpdateDownloadProgress(
                        data.currentDownload,
                        "Merging audio ts files",
                        false,
                        0
                    )
                },
                onMergeFinished = { download, ex ->
                    if (exx != null) {
                        return@M3u8DownloadListener
                    }
                    Core.child.m3u8UpdateDownloadProgress(
                        data.currentDownload,
                        "Finalizing audio",
                        false,
                        0
                    )
                    if (ex != null) {
                        exx = Exception("Failed to merge video and audio.")
                        audioSaveFile.delete()
                    }
                }
            )
            downloads.add(
                m3u8Download(
                    parsedQuality.separateAudioLink,
                    audioSaveFile,
                    audioListener
                )
            )
        }
        val newFile = File(
            m3u8Path,
            saveFile.name
        )
        try {
            val stopped = M3u8Downloads.download(
                httpRequestConfig(data.userAgent),
                downloads
            )
            if (hasSeparateAudio && exx == null) {
                if (stopped) {
                    throw Exception("Stopped download manually.")
                }
                newFile.parentFile.mkdirs()
                data.logInfo("Finished merging audio file. Attempting to merge video & audio...")
                val success = VideoUtil.mergeVideoAndAudio(
                    saveFile,
                    audioSaveFile,
                    newFile
                )
                Core.child.finalizeM3u8DownloadProgress(
                    data.currentDownload,
                    newFile.length(),
                    success
                )
                if (success) {
                    data.writeMessage("Successfully merged video and audio.")
                    newFile.copyTo(
                        saveFile,
                        true
                    )
                    audioSaveFile.delete()
                    newFile.parentFile.deleteRecursively()
                    Core.child.incrementDownloadsFinished()
                    data.writeMessage("Successfully downloaded with ${parsedQuality.quality.tag} quality.")
                    data.finishEpisode()
                    if (job.isActive) {
                        job.cancel()
                    }
                } else {
                    newFile.delete()
                    saveFile.delete()
                    audioSaveFile.delete()
                    throw Exception("Failed to merge video and audio.")
                }
            }
        } catch (e: Exception) {
            newFile.delete()
            audioSaveFile.delete()
            data.retries++
            data.logError(
                "Failed to download m3u8 video. Retrying...",
                e
            )
            return@withContext false
        }
        return@withContext true
    }

    suspend fun handleSecondVideo() = withContext(Dispatchers.IO) {
        val qualities = data.qualityAndDownloads.filter {
            it.secondFrame
        }.toMutableList()
        if (qualities.isEmpty()) {
            return@withContext
        }
        data.writeMessage("First video download is complete. Now downloading the second video.")
        var retries = 0
        var downloadLink = ""
        var qualityOption = temporaryQuality ?: Quality.qualityForTag(
            Defaults.QUALITY.string()
        )
        val episodeName = data.currentEpisode.name.fixForFiles() + "-01"
        val saveFile = data.generateEpisodeSaveFile(qualityOption, "-01")
        var currentDownload = downloadForNameAndQuality(
            episodeName,
            qualityOption
        )
        while (Core.child.isRunning) {
            try {
                if (retries > Defaults.VIDEO_RETRIES.int()) {
                    saveFile.delete()
                    data.writeMessage("Reached max retries of ${Defaults.VIDEO_RETRIES.int()}. Skipping 2nd video download.")
                    break
                }
                qualities.forEach {
                    if (it.quality == qualityOption) {
                        downloadLink = it.downloadLink
                    }
                }
                if (downloadLink.isNotEmpty()) {
                    var fileSizeRetries = Defaults.FILE_SIZE_RETRIES.int()
                    var originalFileSize = 0L
                    var headMode = true
                    data.logInfo("(2nd) Checking video file size with $fileSizeRetries retries.")
                    for (i in 0..fileSizeRetries) {
                        originalFileSize = fileSize(
                            downloadLink,
                            data.userAgent,
                            headMode
                        )
                        if (originalFileSize <= Constants.minFileSize) {
                            headMode = headMode.not()
                            if (i == fileSizeRetries / 2) {
                                data.logError(
                                    "(2nd) Failed to find video file size. Current retries: $i"
                                )
                            }
                            continue
                        } else {
                            break
                        }
                    }
                    if (originalFileSize <= Constants.minFileSize) {
                        if (qualities.isNotEmpty()) {
                            data.logError(
                                "(2nd) Failed to find video file size after $fileSizeRetries retries. | Using another quality."
                            )
                            qualities.remove(
                                qualities.first {
                                    it.downloadLink == downloadLink
                                }
                            )
                            retries++
                            continue
                        } else {
                            data.logError(
                                "(2nd) Failed to find video file size. There are no more qualities to check. | Skipping episode"
                            )
                            saveFile.delete()
                            break
                        }
                    }
                    if (currentDownload != null) {
                        if (currentDownload.downloadPath.isEmpty()
                            || !File(currentDownload.downloadPath).exists()
                            || currentDownload.downloadPath != saveFile.absolutePath
                        ) {
                            currentDownload.downloadPath = saveFile.absolutePath
                        }
                        Core.child.addDownload(currentDownload)
                        if (currentDownload.isComplete) {
                            data.writeMessage("(DB) (2nd) Skipping completed video.")
                            currentDownload.downloading = false
                            currentDownload.queued = false
                            currentDownload.update()
                            break
                        } else {
                            currentDownload.queued = true
                            currentDownload.update()
                        }
                    }
                    data.logInfo("(2nd) Successfully found video link with $retries retries.")
                    if (currentDownload == null) {
                        currentDownload = Download()
                        currentDownload.downloadPath = saveFile.absolutePath
                        currentDownload.name = episodeName
                        currentDownload.slug = data.currentEpisode.slug
                        currentDownload.seriesSlug = data.currentEpisode.seriesSlug
                        currentDownload.resolution = qualityOption.resolution
                        currentDownload.dateAdded = System.currentTimeMillis()
                        currentDownload.fileSize = 0
                        currentDownload.queued = true
                        Core.child.addDownload(currentDownload)
                        data.logInfo("(2nd) Created new download.")
                    } else {
                        data.logInfo("(2nd) Using existing download.")
                    }
                    if (saveFile.exists()) {
                        if (saveFile.length() >= originalFileSize) {
                            data.writeMessage("(IO) (2nd) Skipping completed video.")
                            currentDownload.downloadPath = saveFile.absolutePath
                            currentDownload.fileSize = originalFileSize
                            currentDownload.downloading = false
                            currentDownload.queued = false
                            currentDownload.update()
                            break
                        }
                    } else {
                        var fileRetries = 0
                        var fileError: Exception? = null
                        for (i in 0..3) {
                            try {
                                val created = saveFile.createNewFile()
                                if (!created) {
                                    throw Exception("No error thrown. $i")
                                }
                            } catch (e: Exception) {
                                fileError = e
                                fileRetries++
                                continue
                            }
                        }
                        if (!saveFile.exists()) {
                            data.logError(
                                "(2nd) Failed to create video file after 3 retries. | Skipping episode",
                                fileError,
                                true
                            )
                            saveFile.delete()
                            break
                        }
                    }
                    data.writeMessage("(2nd) Starting download with ${qualityOption.tag} quality.")
                    currentDownload.queued = false
                    currentDownload.downloading = true
                    currentDownload.fileSize = originalFileSize
                    Core.child.addDownload(currentDownload)
                    currentDownload.update()
                    downloadVideo(
                        downloadLink,
                        saveFile,
                        data,
                        currentDownload
                    )
                    originalFileSize = saveFile.length()
                    currentDownload.downloading = false
                    //second time to ensure ui update
                    currentDownload.update()
                    if (saveFile.exists() && saveFile.length() >= originalFileSize) {
                        Core.child.incrementDownloadsFinished()
                        data.writeMessage("(2nd) Successfully downloaded with ${qualityOption.tag} quality.")
                        break
                    }
                } else {
                    data.writeMessage("(2nd) Download link is empty. There's no more qualities to check. | Skipping episode")
                    saveFile.delete()
                    break
                }
            } catch (e: Exception) {
                if (currentDownload != null) {
                    currentDownload.queued = true
                    currentDownload.downloading = false
                    currentDownload.update()
                }
                data.logError(
                    "(2nd) Failed to download video. Retrying...",
                    e
                )
                retries++
            }
        }
    }

}