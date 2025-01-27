package nobility.downloader.core.scraper.video_download

import kotlinx.coroutines.*
import nobility.downloader.core.BoxHelper.Companion.downloadForNameAndQuality
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Download
import nobility.downloader.core.scraper.data.ParsedQuality
import nobility.downloader.core.scraper.data.QualityAndDownload
import nobility.downloader.core.scraper.video_download.Functions.downloadVideo
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
            if (data.resRetries < Constants.maxResRetries) {
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
                        data.m3u8Retries++
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
            //timeout just in case it hangs for too long.
            //withTimeout((Defaults.TIMEOUT.int() * 1000).toLong() * 3) {
            for (quality in Quality.qualityList(has720, has1080)) {
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
                    //data.undriver.blank()
                    //delay(2000)
                } catch (e: Exception) {
                    data.logError(
                        "An exception was thrown when looking for quality links.",
                        e
                    )
                    continue
                }
            }
            //}
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
                        val videoLink = data.driver.currentUrl
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
                Core.child.updateDownloadInDatabase(
                    data.currentDownload
                )
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
                Core.child.updateDownloadInDatabase(
                    data.currentDownload,
                    true
                )
            },
            downloadFinished = { download, success ->
                if (!success) {
                    exx = Exception("Failed to download m3u8 file. Incomplete ts files.")
                    return@M3u8DownloadListener
                }
                data.logInfo("Finished m3u8 video download.")
                if (!hasSeparateAudio) {
                    data.currentDownload.downloading = false
                    Core.child.updateDownloadInDatabase(
                        data.currentDownload,
                        true
                    )
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
                Core.child.updateDownloadInDatabase(
                    data.currentDownload,
                    true
                )
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
                    Core.child.updateDownloadInDatabase(
                        data.currentDownload,
                        true
                    )
                    if (saveFile.exists() && saveFile.length() >= 5000) {
                        if (!hasSeparateAudio) {
                            data.currentDownload.downloading = false
                            Core.child.updateDownloadInDatabase(
                                data.currentDownload,
                                true
                            )
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
                if (retries > Constants.maxRetries) {
                    saveFile.delete()
                    data.writeMessage("Reached max retries of ${Constants.maxRetries}. Skipping 2nd video download.")
                    break
                }
                if (qualities.isNotEmpty()) {
                    qualities.forEach {
                        if (it.quality == qualityOption) {
                            downloadLink = it.downloadLink
                        }
                    }
                }
                if (downloadLink.isNotEmpty()) {
                    var originalFileSize = Functions.fileSize(downloadLink, data.userAgent)
                    if (originalFileSize <= 5000) {
                        if (retries < 2) {
                            data.writeMessage("(2nd) Failed to determine file size. Retrying...")
                            retries++
                            continue
                        } else if (retries in 2..4) {
                            data.writeMessage("(2nd) Failed to determine file size. Retrying with a different quality...")
                            qualities.remove(
                                qualities.first { it.downloadLink == downloadLink }
                            )
                            retries++
                            continue
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
                            Core.child.updateDownloadInDatabase(currentDownload, true)
                            break
                        } else {
                            currentDownload.queued = true
                            Core.child.updateDownloadInDatabase(currentDownload, true)
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
                        //todo might have to move this for actual success
                        Core.child.addDownload(currentDownload)
                        data.logInfo("(2nd) Created new download.")
                    } else {
                        data.logInfo("(2nd) Using existing download.")
                    }
                    if (saveFile.exists()) {
                        if (originalFileSize > 0 && saveFile.length() >= originalFileSize) {
                            data.writeMessage("(IO) (2nd) Skipping completed video.")
                            currentDownload.downloadPath = saveFile.absolutePath
                            currentDownload.fileSize = originalFileSize
                            currentDownload.downloading = false
                            currentDownload.queued = false
                            Core.child.updateDownloadInDatabase(
                                currentDownload,
                                true
                            )
                            break
                        }
                    } else {
                        try {
                            val created = saveFile.createNewFile()
                            if (!created) {
                                throw Exception("No error thrown.")
                            }
                        } catch (e: Exception) {
                            data.logError(
                                "(2nd) Failed to create second video file. Retrying...",
                                e,
                                true
                            )
                            retries++
                            continue
                        }
                    }
                    data.writeMessage("(2nd) Starting download with ${qualityOption.tag} quality.")
                    currentDownload.queued = false
                    currentDownload.downloading = true
                    currentDownload.fileSize = originalFileSize
                    Core.child.addDownload(currentDownload)
                    Core.child.updateDownloadInDatabase(currentDownload, true)
                    downloadVideo(
                        downloadLink,
                        saveFile,
                        data,
                        currentDownload
                    )
                    originalFileSize = saveFile.length()
                    currentDownload.downloading = false
                    //second time to ensure ui update
                    Core.child.updateDownloadInDatabase(currentDownload, true)
                    if (saveFile.exists() && saveFile.length() >= originalFileSize) {
                        Core.child.incrementDownloadsFinished()
                        data.writeMessage("(2nd) Successfully downloaded with ${qualityOption.tag} quality.")
                        break
                    }
                } else {
                    data.writeMessage("(2nd) Download link is empty. Retrying...")
                    retries++
                }
            } catch (e: Exception) {
                if (currentDownload != null) {
                    currentDownload.queued = true
                    currentDownload.downloading = false
                    Core.child.updateDownloadInDatabase(
                        currentDownload,
                        true
                    )
                }
                data.logError(
                    "(2nd) Failed to download video. Retrying...",
                    e,
                    true
                )
                retries++
            }
        }
    }

}