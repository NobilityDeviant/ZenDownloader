package nobility.downloader.core.scraper.video_download

import kotlinx.coroutines.*
import nobility.downloader.core.BoxHelper.Companion.downloadForNameAndQuality
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Download
import nobility.downloader.core.entities.Episode
import nobility.downloader.core.scraper.data.DownloadData
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
    private val episode: Episode,
    val temporaryQuality: Quality?
) {

    val data = VideoDownloadData(
        episode,
        temporaryQuality
    )

    suspend fun parseQualities(
        slug: String
    ): Resource<DownloadData> {
        var qualityOption = temporaryQuality ?: Quality.qualityForTag(
            Defaults.QUALITY.string()
        )
        var preferredDownload: DownloadData? = null
        if (data.downloadDatas.isEmpty()) {
            if (data.resRetries < Defaults.QUALITY_RETRIES.int()) {
                val result = detectAvailableQualities(slug)
                if (result.isFailed) {
                    if (result.errorCode != -1) {
                        val errorCode = ErrorCode.errorCodeForCode(result.errorCode)
                        when (errorCode) {
                            ErrorCode.NO_FRAME -> {
                                data.resRetries++
                                return Resource.Error(
                                    "Failed to find frame for quality check. Retrying...",
                                    result.message
                                )
                            }
                            ErrorCode.CLOUDFLARE_FUCK -> {
                                return Resource.Error(
                                    """
                                                   Cloudflare has blocked our request.
                                                   Unfortunately that means we can't proceed, but well continue anyways...
                                                """.trimIndent(),
                                    result.message
                                )
                            }
                            ErrorCode.IFRAME_FORBIDDEN, ErrorCode.EMPTY_FRAME -> {
                                data.resRetries = 3
                                return Resource.Error(
                                    "Failed to find video frame for: $slug" +
                                            "\nPlease report this in github issues with the video you are trying to download.",
                                    result.message
                                )
                            }
                            ErrorCode.FAILED_EXTRACT_RES -> {
                                data.resRetries = 3
                                return Resource.Error(
                                    "Failed to extract quality links. Returned an empty list.",
                                    result.message
                                )
                            }
                            ErrorCode.NO_JS -> {
                                data.resRetries = 3
                                return Resource.Error(
                                    "This browser doesn't support JavascriptExecutor.",
                                    result.message
                                )
                            }
                            ErrorCode.M3U8_LINK_FAILED -> {
                                data.retries++
                                return Resource.Error(
                                    "m3u8 link has failed.",
                                    result.message
                                )
                            }
                            ErrorCode.FAILED_PAGE_READ -> {
                                data.retries++
                                return Resource.Error(
                                    "Failed to read webpage.",
                                    result.message
                                )
                            }
                            ErrorCode.FFMPEG_NOT_INSTALLED -> {
                                //no retry needed, we finish the episode
                                return Resource.Error(
                                    "Critical error | Skipping episode",
                                    result.message
                                )
                            }
                            else -> {
                                data.retries++
                                return Resource.Error(
                                    "Error without ErrorCode.",
                                    result.message
                                )
                            }
                        }
                    } else {
                        data.retries++
                        return Resource.Error(
                            "Error without ErrorCode.",
                            result.message
                        )
                    }
                }
                if (result.data != null) {
                    data.downloadDatas.clear()
                    data.downloadDatas.addAll(result.data)
                    val firstQualities = result.data.filter { !it.secondFrame }
                    qualityOption = Quality.bestQuality(
                        qualityOption,
                        firstQualities.map { it.quality }
                    )
                    firstQualities.forEach {
                        if (it.quality == qualityOption) {
                            preferredDownload = it
                        }
                    }
                }/* else {
                    data.logInfo(
                        "Failed to find quality download links. Defaulting to ${Quality.LOW.tag} quality."
                    )
                    qualityOption = Quality.LOW
                }*/
            }
        } else {
            data.logDebug("Using existing qualities. Size: ${data.downloadDatas.size}")
            val firstQualities = data.downloadDatas.filter { !it.secondFrame }
            qualityOption = Quality.bestQuality(
                qualityOption,
                firstQualities.map { it.quality }
            )
            firstQualities.forEach {
                if (it.quality == qualityOption) {
                    preferredDownload = it
                }
            }
        }
        if (preferredDownload == null || preferredDownload.downloadLink.isEmpty()) {
            data.retries++
            return Resource.Error(
                //"Failed to find the preferredDownload. | Retrying..."
            )
        }
        return Resource.Success(preferredDownload)
    }

    suspend fun detectAvailableQualities(
        slug: String
    ): Resource<List<DownloadData>> = withContext(Dispatchers.IO) {
        if (data.driver !is JavascriptExecutor) {
            return@withContext Resource.ErrorCode(ErrorCode.NO_JS.code)
        }
        val frameIds = listOf(
            "anime-js-0",
            "anime-js-1",
            "cizgi-js-0"
        )
        val secondFrameId = "cizgi-js-1"
        var m3u8Mode = false
        val fullLink = slug.slugToLink()
        val downloadDatas = mutableListOf<DownloadData>()

        data.logDebug("Scraping quality links from $fullLink")
        data.logDebug("Using UserAgent: ${data.userAgent}")

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
            var sbFrameMessage: String? = ""
            @Suppress("UNUSED")
            for (i in 1..5) {
                val frameSource = readUrlLines(frameLink, data)
                if (frameSource.data != null) {
                    sbFrame = frameSource.data
                } else {
                    sbFrameMessage = frameSource.message
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
                //ok this happens sometimes with
                //selenium 403 error. konosuba.
                //i think we need to use the js redirect.
                return@withContext Resource.Error(
                    "Failed to read frame source.",
                    sbFrameMessage
                )
            }
            if (m3u8Mode) {
                if (VideoUtil.FfmpegPathHolder.ffmpegPath.isEmpty()) {
                    return@withContext Resource.ErrorCode(
                        "Found an m3u8 but ffmpeg isn't installed. Visit: ${AppInfo.FFMPEG_GUIDE_URL}",
                        ErrorCode.FFMPEG_NOT_INSTALLED.code
                    )
                }
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
                    val linkKey = "getRedirectedUrl(\""
                    val endLinkKey = "\").then(data => {"
                    for (line in sbFrame.lines()) {
                        if (line.contains(linkKey)) {
                            hslLink = line.substringAfter(linkKey).substringBefore(endLinkKey)
                            domain = hslLink.substringBeforeLast("/")
                        }
                    }
                }
                if (hslLink.isEmpty() || domain.isEmpty()) {
                    FrogLog.writeErrorToTxt(
                        "m3u8_link_source",
                        frameString,
                        "Link: $hslLink | UserAgent: " + data.userAgent
                    )
                    return@withContext Resource.ErrorCode(
                        "Failed to find m3u8 link in the source.",
                        ErrorCode.M3U8_LINK_FAILED.code
                    )
                }
                FrogLog.debug("Trying to read content from: $hslLink")
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
                                            if (t.contains("LANGUAGE=\"eng\"") || t.contains("LANGUAGE=\"eng_2\"")) {
                                                val split = t.split(",")
                                                var audioSplit = ""
                                                split.forEach { splitLine ->
                                                    if (splitLine.contains("URI=\"")) {
                                                        audioSplit = splitLine.substringAfter("URI=\"").substringBeforeLast("\"")
                                                    }
                                                }
                                                val audioUrl = "$domain/$audioSplit"
                                                FrogLog.info("Found separate english audio for m3u8. t: $t audio url: $audioUrl quality: $quality")
                                                separateAudio = true
                                                downloadDatas.add(
                                                    DownloadData(
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
                                        downloadDatas.add(
                                            DownloadData(
                                                quality,
                                                domain + "/" + hslLines[index + 1]
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    return@withContext Resource.Error(
                        "Failed to read m3u8 quality source code.",
                        hslSource.message
                    )
                }
                if (downloadDatas.isEmpty()) {
                    FrogLog.writeErrorToTxt(
                        "m3u8_qualities_source",
                        hslSource.data.toString(),
                        "Link: $hslLink | UserAgent: " + data.userAgent
                    )
                    return@withContext Resource.ErrorCode(
                        "Failed to find m3u8 qualities.",
                        ErrorCode.M3U8_LINK_FAILED.code
                    )
                } else {
                    data.logInfo("Successfully found m3u8 qualities.")
                    return@withContext Resource.Success(downloadDatas)
                }
            }
            val src = sbFrame.toString()
            val linkKey1 = "$.getJSON(\""
            val linkKey2 = "\", function(response){"
            if (!src.contains(linkKey1)) {
                if (src.contains("<title>403 Forbidden</title>")) {
                    return@withContext Resource.Error(
                        "Webpage returned a 403 Forbidden error. UserAgent: ${data.userAgent}"
                    )
                }
                FrogLog.writeErrorToTxt(
                    "Missing linkKey1",
                    src,
                    "User Agent: ${data.userAgent}"
                )
                return@withContext Resource.Error(
                    "Source code doesn't contain linkKey1. UserAgent: ${data.userAgent}"
                )
            }
            if (!src.contains(linkKey2)) {
                if (src.contains("<title>403 Forbidden</title>")) {
                    return@withContext Resource.Error(
                        "Webpage returned a 403 Forbidden error. UserAgent: ${data.userAgent}"
                    )
                }
                FrogLog.writeErrorToTxt(
                    "Missing linkKey2",
                    src,
                    "User Agent: ${data.userAgent}"
                )
                return@withContext Resource.Error(
                    "Source code doesn't contain linkKey2. UserAgent: ${data.userAgent}"
                )
            }
            val linkIndex1 = src.indexOf(linkKey1)
            val linkIndex2 = src.indexOf(linkKey2)
            val functionLink = src.substring(
                linkIndex1 + linkKey1.length, linkIndex2
            )
            //IDK how to execute the js, so we still have to use selenium.
            data.undriver.go(fullLink)
            data.logDebug("Checking qualities with a timeout of ${Defaults.TIMEOUT.int()} seconds")
            var qualityRetry = 0
            while (qualityRetry <= Defaults.QUALITY_RETRIES.int()) {
                try {
                    data.base.executeJs(
                        JavascriptHelper.changeUrlToVideoFunction(
                            functionLink
                        )
                    )
                    delay(data.pageChangeWaitTime)
                    val scriptError = data.base.findLinkError()
                    if (scriptError.isNotEmpty()) {
                        qualityRetry++
                        if (qualityRetry == Defaults.QUALITY_RETRIES.int() / 2) {
                            data.logError(
                                "The script has failed to find qualities. | Retrying... ($qualityRetry)",
                                scriptError
                            )
                        }
                        data.base.clearLogs()
                        continue
                    }
                    if (src.contains("404 Not Found") || src.contains("404 - Page not Found")) {
                        data.logError(
                            "(404) Failed to find qualities. | Retrying..."
                        )
                        qualityRetry++
                        data.base.clearLogs()
                        continue
                    }
                    val links = data.base.findLinks()
                    if (links.isNotEmpty()) {
                        links.forEach { link ->
                            val dlData = DownloadData(link.quality, link.url)
                            if (!downloadDatas.contains(dlData)) {
                                downloadDatas.add(dlData)
                                data.logInfo(
                                    "Found ${link.quality} quality."
                                )
                            }
                        }
                        break
                    }
                    delay(2000)
                } catch (e: Exception) {
                    qualityRetry++
                    if (qualityRetry == Defaults.QUALITY_RETRIES.int() / 2) {
                        data.logError(
                            "An exception was thrown when looking for quality links. | Retrying... ($qualityRetry)",
                            e.localizedMessage
                        )
                    }
                    continue
                }
            }
            data.base.clearLogs()
            qualityRetry = 0
            if (sbFrame2.isNotEmpty()) {
                val src2 = sbFrame2.toString()
                val secondLinkIndex1 = src2.indexOf(linkKey1)
                val secondLinkIndex2 = src2.indexOf(linkKey2)
                val secondFunctionLink = src2.substring(
                    secondLinkIndex1 + linkKey1.length, secondLinkIndex2
                )
                data.driver.navigate().to(fullLink)
                while (qualityRetry <= Defaults.QUALITY_RETRIES.int()) {
                    try {
                        data.base.executeJs(
                            JavascriptHelper.changeUrlToVideoFunction(
                                secondFunctionLink
                            )
                        )
                        delay(data.pageChangeWaitTime)
                        val scriptError = data.base.findLinkError()
                        if (scriptError.isNotEmpty()) {
                            qualityRetry++
                            if (qualityRetry == Defaults.QUALITY_RETRIES.int() / 2) {
                                data.logError(
                                    "(2nd) The script has failed to find qualities. | Retrying... ($qualityRetry)",
                                    scriptError
                                )
                            }
                            data.base.clearLogs()
                            continue
                        }
                        if (src.contains("404 Not Found") || src.contains("404 - Page not Found")) {
                            data.logError(
                                "(2nd) (404) Failed to find qualities. | Retrying..."
                            )
                            qualityRetry++
                            data.base.clearLogs()
                            continue
                        }
                        val links = data.base.findLinks()
                        if (links.isNotEmpty()) {
                            links.forEach { link ->
                                val dlData = DownloadData(
                                    link.quality,
                                    link.url,
                                    true
                                )
                                if (!downloadDatas.contains(dlData)) {
                                    downloadDatas.add(dlData)
                                    data.logInfo(
                                        "(2nd) Found ${link.quality} quality."
                                    )
                                }
                            }
                            break
                        }
                        delay(2000)
                    } catch (e: Exception) {
                        qualityRetry++
                        if (qualityRetry == Defaults.QUALITY_RETRIES.int() / 2) {
                            data.logError(
                                "(2nd) An exception was thrown when looking for quality links. | Retrying... ($qualityRetry)",
                                e.localizedMessage
                            )
                        }
                        continue
                    }
                }
            }
            if (downloadDatas.isEmpty()) {
                return@withContext Resource.Error(
                    "Failed to find quality links after ${Defaults.QUALITY_RETRIES.int()} retries.",
                )
            } else {
                data.logInfo("Successfully found qualities. Size: ${downloadDatas.size}")
                return@withContext Resource.Success(downloadDatas)
            }
        } catch (e: Exception) {
            return@withContext Resource.Error(e)
        }
    }

    suspend fun handleM3U8(
        downloadData: DownloadData,
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
                if (job.isActive) {
                    job.cancel()
                }
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
        val hasSeparateAudio = downloadData.separateAudioLink.isNotEmpty()
        val videoListener = M3u8DownloadListener(
            downloadStarted = {
                data.writeMessage("Starting m3u8 video download with ${downloadData.quality.tag} quality.")
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
                            Core.child.downloadThread.incrementDownloadsFinished()
                            data.writeMessage("Successfully downloaded with ${downloadData.quality.tag} quality.")
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
        val m3u8VideoDownload = m3u8Download(
            downloadData.downloadLink,
            saveFile,
            videoListener
        )
        var m3u8AudioDownload: M3u8Download? = null
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
                        exx = Exception("Failed to download m3u8 audio file.")
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
                        exx = Exception("Failed to merge audio file. Error: ${ex.localizedMessage}")
                        audioSaveFile.delete()
                    }
                }
            )
            m3u8AudioDownload = m3u8Download(
                downloadData.separateAudioLink,
                audioSaveFile,
                audioListener
            )
        }
        val mergedVideoAndAudioFile = File(
            m3u8Path,
            saveFile.name
        )
        try {
            val stopped = M3u8Downloads.download(
                httpRequestConfig(data.userAgent),
                m3u8VideoDownload
            )
            if (hasSeparateAudio && exx == null) {
                if (stopped) {
                    throw Exception("Stopped download manually.")
                }
                if (m3u8AudioDownload == null) {
                    throw Exception("Failed to create seperate audio download.")
                }
                val stoppedAudio = M3u8Downloads.download(
                    httpRequestConfig(data.userAgent),
                    m3u8AudioDownload
                )
                if (stoppedAudio) {
                    throw Exception("Stopped audio download manually.")
                }
                if (audioSaveFile.exists()) {
                    mergedVideoAndAudioFile.parentFile.mkdirs()
                    data.logInfo("Finished merging audio ts files. Attempting to merge video & audio...")
                    val success = VideoUtil.mergeVideoAndAudio(
                        saveFile,
                        audioSaveFile,
                        mergedVideoAndAudioFile
                    )
                    Core.child.finalizeM3u8DownloadProgress(
                        data.currentDownload,
                        mergedVideoAndAudioFile.length(),
                        success
                    )
                    if (success) {
                        data.writeMessage("Successfully merged video and audio.")
                        mergedVideoAndAudioFile.copyTo(
                            saveFile,
                            true
                        )
                        audioSaveFile.delete()
                        mergedVideoAndAudioFile.parentFile.deleteRecursively()
                        Core.child.downloadThread.incrementDownloadsFinished()
                        data.writeMessage("Successfully downloaded with ${downloadData.quality.tag} quality.")
                        data.finishEpisode()
                    } else {
                        mergedVideoAndAudioFile.delete()
                        saveFile.delete()
                        audioSaveFile.delete()
                        throw Exception("Failed to merge video and audio.")
                    }
                } else {
                    throw (Exception("The audio file doesn't exist."))
                }
            } else if (exx != null) {
                throw (exx)
            }
        } catch (e: Exception) {
            mergedVideoAndAudioFile.delete()
            audioSaveFile.delete()
            data.retries++
            data.logError(
                "Failed to download m3u8 video. | Retrying...",
                e
            )
            return@withContext false
        } finally {
            if (job.isActive) {
                job.cancel()
            }
        }
        return@withContext true
    }

    suspend fun handleSecondVideo() = withContext(Dispatchers.IO) {
        val qualities = data.downloadDatas.filter {
            it.secondFrame
        }.toMutableList()
        if (qualities.isEmpty()) {
            return@withContext
        }
        data.writeMessage("First video download is complete. Now downloading the second video.")
        var retries = 0
        var preferredDownload: DownloadData? = null
        val qualityOption = temporaryQuality ?: Quality.qualityForTag(
            Defaults.QUALITY.string()
        )
        val episodeName = episode.name.fixForFiles() + "-01"
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
                        preferredDownload = it
                    }
                }
                if (preferredDownload != null && preferredDownload.downloadLink.isNotEmpty()) {
                    val fileSizeRetries = Defaults.FILE_SIZE_RETRIES.int()
                    var originalFileSize = 0L
                    var headMode = true
                    data.logDebug("(2nd) Checking video file size with $fileSizeRetries max retries.")
                    for (i in 0..fileSizeRetries) {
                        originalFileSize = fileSize(
                            preferredDownload.downloadLink,
                            data.userAgent,
                            headMode
                        )
                        if (originalFileSize <= Constants.minFileSize) {
                            headMode = headMode.not()
                            if (i == fileSizeRetries / 2) {
                                data.logError(
                                    "(2nd) Failed to find video file size. Current retries: $i"
                                )
                                data.writeMessage(
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
                            qualities.remove(preferredDownload)
                            retries++
                            if (qualities.isEmpty()) {
                                data.logError(
                                    "(2nd) Failed to find video file size. There are no more qualities to check. | Skipping episode"
                                )
                                saveFile.delete()
                                break
                            }
                            continue
                        } else {
                            data.logError(
                                "(2nd) Failed to find video file size. There are no more qualities to check. | Skipping episode"
                            )
                            saveFile.delete()
                            break
                        }
                    }
                    data.logDebug("(2nd) Successfully found video file size of: ${Tools.bytesToString(originalFileSize)}")
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
                        currentDownload.slug = episode.slug
                        currentDownload.seriesSlug = episode.seriesSlug
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
                        preferredDownload.downloadLink,
                        saveFile,
                        data,
                        currentDownload
                    )
                    originalFileSize = saveFile.length()
                    currentDownload.downloading = false
                    //second time to ensure ui update
                    currentDownload.update()
                    if (saveFile.exists() && saveFile.length() >= originalFileSize) {
                        Core.child.downloadThread.incrementDownloadsFinished()
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