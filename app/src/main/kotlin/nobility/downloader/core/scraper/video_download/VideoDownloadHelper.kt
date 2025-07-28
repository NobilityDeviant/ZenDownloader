package nobility.downloader.core.scraper.video_download

import AppInfo
import Resource
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

    val videoDownloadData = VideoDownloadData(
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
        if (videoDownloadData.downloadDatas.isEmpty()) {
            if (videoDownloadData.resRetries < Defaults.QUALITY_RETRIES.int()) {
                val result = detectAvailableQualities(slug)
                val data = result.data
                if (result.isFailed) {
                    if (result.errorCode != -1) {
                        val errorCode = ErrorCode.errorCodeForCode(result.errorCode)
                        when (errorCode) {
                            ErrorCode.NO_FRAME -> {
                                videoDownloadData.resRetries++
                                return Resource.Error(
                                    "Failed to find frame for quality check. Retrying...",
                                    result.message
                                )
                            }

                            ErrorCode.M3U8_SECOND_EMPTY_FRAME -> {
                                videoDownloadData.m3u8SecondVideoCheck = false
                                videoDownloadData.m3u8Retries = 0
                                videoDownloadData.retries++
                                return Resource.Error(
                                    "Failed to find 2nd m3u8 frame. | Defaulting to regular M3U8 check.",
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
                                videoDownloadData.resRetries = 3
                                return Resource.Error(
                                    "Failed to find video frame for: $slug" +
                                            "\nPlease report this in github issues with the video you are trying to download.",
                                    result.message
                                )
                            }

                            ErrorCode.FAILED_EXTRACT_RES -> {
                                videoDownloadData.resRetries = 3
                                return Resource.Error(
                                    "Failed to extract quality links. Returned an empty list.",
                                    result.message
                                )
                            }

                            ErrorCode.NO_JS -> {
                                videoDownloadData.resRetries = 3
                                return Resource.Error(
                                    "This browser doesn't support JavascriptExecutor.",
                                    result.message
                                )
                            }

                            ErrorCode.M3U8_LINK_FAILED -> {
                                videoDownloadData.retries++
                                return Resource.Error(
                                    "m3u8 link has failed.",
                                    result.message
                                )
                            }

                            ErrorCode.FAILED_PAGE_READ -> {
                                videoDownloadData.retries++
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
                                videoDownloadData.retries++
                                return Resource.Error(
                                    "Error without ErrorCode.",
                                    result.message
                                )
                            }
                        }
                    } else {
                        videoDownloadData.retries++
                        return Resource.Error(
                            "Error without ErrorCode.",
                            result.message
                        )
                    }
                }
                if (data != null) {
                    videoDownloadData.downloadDatas.clear()
                    videoDownloadData.downloadDatas.addAll(data)
                    val firstQualities = data.filter { !it.secondFrame }
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
            }
        } else {
            videoDownloadData.debug("Using existing qualities. Size: ${videoDownloadData.downloadDatas.size}")
            val firstQualities = videoDownloadData.downloadDatas.filter { !it.secondFrame }
            qualityOption = Quality.bestQuality(
                qualityOption,
                firstQualities.map { it.quality }
            )
            videoDownloadData.debug(
                "Found best quality: ${qualityOption.resolution} From: ${firstQualities.map { it.quality }.joinToString("")}"
            )
            firstQualities.forEach {
                if (it.quality == qualityOption) {
                    preferredDownload = it
                }
            }
        }
        if (preferredDownload == null || preferredDownload.downloadLink.isEmpty()) {
            videoDownloadData.debug("Failed to find download link for quality: ${qualityOption.resolution}")
            videoDownloadData.retries++
            return Resource.Error()
        }
        return Resource.Success(preferredDownload)
    }

    suspend fun detectAvailableQualities(
        slug: String
    ): Resource<List<DownloadData>> = withContext(Dispatchers.IO) {
        if (videoDownloadData.driver !is JavascriptExecutor) {
            return@withContext Resource.ErrorCode(ErrorCode.NO_JS.code)
        }
        val m3u8Check = videoDownloadData.m3u8SecondVideoCheck
        val frameIds = listOf(
            "anime-js-0",
            "anime-js-1",
            "cizgi-js-0"
        )
        val secondFrameIds = listOf(
            "cizgi-js-0",
            "cizgi-js-1"
        )
        var m3u8Mode = false
        val fullLink = slug.slugToLink()
        val downloadDatas = mutableListOf<DownloadData>()

        videoDownloadData.debug("Scraping quality links from $fullLink")
        videoDownloadData.debug("Using UserAgent: ${videoDownloadData.userAgent}")
        if (m3u8Check) {
            videoDownloadData.debug("Using m3u8 2nd video check.")
        }

        try {
            val source = readUrlLines(fullLink, videoDownloadData, false)
            if (source.isFailed) {
                return@withContext Resource.ErrorCode(
                    source.message,
                    ErrorCode.FAILED_PAGE_READ.code
                )
            }
            var frameLink = ""
            var secondFrameLink = ""
            val doc = Jsoup.parse(source.data.toString())
            if (m3u8Check) {
                for (id in secondFrameIds) {
                    val iframe = doc.getElementById(
                        id
                    )
                    if (iframe != null) {
                        frameLink = iframe.attr("src")
                        videoDownloadData.info("Found m3u8 2nd frame with id: $id")
                        break
                    }
                }
            } else {
                for (id in frameIds) {
                    val iframe = doc.getElementById(
                        id
                    )
                    if (iframe != null) {
                        frameLink = iframe.attr("src")
                        if (id == "anime-js-1") {
                            m3u8Mode = true
                        }
                        videoDownloadData.info("Found frame with id: $id")
                        break
                    }
                }
            }

            if (!m3u8Check) {
                val iframe = doc.getElementById(secondFrameIds[1])
                if (iframe != null) {
                    secondFrameLink = iframe.attr("src")
                    videoDownloadData.info("Found 2nd frame with id: ${secondFrameIds[1]}")
                }
            }

            if (frameLink.isEmpty()) {
                if (m3u8Check) {
                    return@withContext Resource.ErrorCode(
                        ErrorCode.M3U8_SECOND_EMPTY_FRAME.code
                    )
                }
                return@withContext Resource.ErrorCode(
                    ErrorCode.EMPTY_FRAME.code
                )
            }
            var sbFrame = StringBuilder()
            var sbFrameMessage: String? = ""

            @Suppress("UNUSED")
            for (i in 1..5) {
                val frameSource = readUrlLines(frameLink, videoDownloadData)
                val frameSourceData = frameSource.data
                if (frameSourceData != null) {
                    sbFrame = frameSourceData
                    if (m3u8Check) {
                        //just in case?
                        m3u8Mode = false
                    }
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
                    val frameSource = readUrlLines(secondFrameLink, videoDownloadData)
                    val frameSourceData = frameSource.data
                    if (frameSourceData != null) {
                        sbFrame2 = frameSourceData
                    }
                    if (sbFrame2.isNotEmpty()) {
                        break
                    }
                }
            }

            if (sbFrame.isEmpty()) {
                if (m3u8Check) {
                    videoDownloadData.m3u8SecondVideoCheck = false
                    videoDownloadData.m3u8Retries = 0
                    return@withContext Resource.Error(
                        "Failed to read m3u8 2nd video frame source. Defaulting to regular M3U8 check.",
                        sbFrameMessage
                    )
                }
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
                        "Link: $hslLink | UserAgent: " + videoDownloadData.userAgent
                    )
                    return@withContext Resource.ErrorCode(
                        "Failed to find m3u8 link in the source.",
                        ErrorCode.M3U8_LINK_FAILED.code
                    )
                }
                FrogLog.debug("Trying to read content from: $hslLink")

                val hslSource = readUrlLines(hslLink, videoDownloadData)
                val hslSourceData = hslSource.data

                if (hslSourceData != null) {

                    //File("./${videoDownloadData.episode.name.fixForFiles()}_debug_.m3u8")
                      //  .writeText(hslSourceData.toString())

                    val hslLines = hslSourceData.lines()
                    val audioMap = mutableMapOf<String, String>()

                    hslLines.forEach { line ->
                        if (line.startsWith("#EXT-X-MEDIA:TYPE=AUDIO") && line.contains("LANGUAGE=\"eng")) {
                            val groupId = Regex("""GROUP-ID="([^"]+)"""").find(line)?.groupValues?.get(1)
                            val uri = Regex("""URI="([^"]+)"""").find(line)?.groupValues?.get(1)

                            if (groupId != null && uri != null) {
                                audioMap[groupId] = uri
                            }
                        }
                    }

                    hslLines.forEachIndexed { index, line ->
                        if (line.startsWith("#EXT-X-STREAM-INF")) {
                            Quality.qualityList().forEach { quality ->
                                if (line.contains("x${quality.resolution}")) {

                                    val videoUri = hslLines.getOrNull(index + 1)?.trim()

                                    if (videoUri != null) {

                                        val audioGroupId = Regex("""AUDIO="([^"]+)"""")
                                            .find(line)?.groupValues?.get(1)
                                        val separateAudioUrl = audioMap[audioGroupId]

                                        val downloadData = DownloadData(
                                            quality,
                                            "$domain/$videoUri",
                                            separateAudioLink = if (separateAudioUrl != null)
                                                "$domain/$separateAudioUrl" else ""
                                        )

                                        videoDownloadData.info(
                                            """
                                                Added m3u8 stream:
                                                quality=${quality.resolution}
                                                domain=$domain
                                                video=$videoUri
                                                audio=$separateAudioUrl
                                            """.trimIndent()
                                        )

                                        downloadDatas.add(downloadData)
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
                        "Link: $hslLink | UserAgent: " + videoDownloadData.userAgent
                    )
                    return@withContext Resource.ErrorCode(
                        "Failed to find m3u8 qualities.",
                        ErrorCode.M3U8_LINK_FAILED.code
                    )
                } else {
                    videoDownloadData.info("Successfully found m3u8 qualities.")
                    return@withContext Resource.Success(downloadDatas)
                }
            }
            val src = sbFrame.toString()
            val linkKey1 = "$.getJSON(\""
            val linkKey2 = "\", function(response){"
            if (!src.contains(linkKey1)) {
                if (src.contains("<title>403 Forbidden</title>")) {
                    return@withContext Resource.Error(
                        "Webpage returned a 403 Forbidden error. UserAgent: ${videoDownloadData.userAgent}"
                    )
                }
                FrogLog.writeErrorToTxt(
                    "Missing linkKey1",
                    src,
                    "User Agent: ${videoDownloadData.userAgent}"
                )
                return@withContext Resource.Error(
                    "Source code doesn't contain linkKey1. UserAgent: ${videoDownloadData.userAgent}"
                )
            }
            if (!src.contains(linkKey2)) {
                if (src.contains("<title>403 Forbidden</title>")) {
                    return@withContext Resource.Error(
                        "Webpage returned a 403 Forbidden error. UserAgent: ${videoDownloadData.userAgent}"
                    )
                }
                FrogLog.writeErrorToTxt(
                    "Missing linkKey2",
                    src,
                    "User Agent: ${videoDownloadData.userAgent}"
                )
                return@withContext Resource.Error(
                    "Source code doesn't contain linkKey2. UserAgent: ${videoDownloadData.userAgent}"
                )
            }
            val linkIndex1 = src.indexOf(linkKey1)
            val linkIndex2 = src.indexOf(linkKey2)
            val functionLink = src.substring(
                linkIndex1 + linkKey1.length, linkIndex2
            )
            //IDK how to execute the js, so we still have to use selenium.
            videoDownloadData.undriver.go(fullLink)
            if (m3u8Check) {
                videoDownloadData.debug("Found 2nd video for m3u8 check.")
            }
            videoDownloadData.debug("Checking qualities with a timeout of ${Defaults.TIMEOUT.int()} seconds")
            var qualityRetry = 0
            while (qualityRetry <= Defaults.QUALITY_RETRIES.int()) {
                try {
                    videoDownloadData.base.executeJs(
                        JavascriptHelper.changeUrlToVideoFunction(
                            functionLink
                        )
                    )
                    delay(videoDownloadData.pageChangeWaitTime)
                    val scriptError = videoDownloadData.base.findLinkError()
                    if (scriptError.isNotEmpty()) {
                        qualityRetry++
                        if (qualityRetry == Defaults.QUALITY_RETRIES.int() / 2) {
                            videoDownloadData.error(
                                "The script has failed to find qualities. | Retrying ($qualityRetry)",
                                scriptError
                            )
                        }
                        videoDownloadData.base.clearLogs()
                        continue
                    }
                    if (src.contains("404 Not Found") || src.contains("404 - Page not Found")) {
                        videoDownloadData.error(
                            "(404) Failed to find qualities. | Retrying"
                        )
                        qualityRetry++
                        videoDownloadData.base.clearLogs()
                        continue
                    }
                    val links = videoDownloadData.base.findLinks()
                    if (links.isNotEmpty()) {
                        links.forEach { link ->
                            val dlData = DownloadData(link.quality, link.url)
                            if (!downloadDatas.contains(dlData)) {
                                downloadDatas.add(dlData)
                                videoDownloadData.info(
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
                        videoDownloadData.error(
                            "An exception was thrown when looking for quality links. | Retrying... ($qualityRetry)",
                            e.localizedMessage
                        )
                    }
                    continue
                }
            }
            videoDownloadData.base.clearLogs()
            qualityRetry = 0
            if (sbFrame2.isNotEmpty() && !m3u8Check) {
                val src2 = sbFrame2.toString()
                val secondLinkIndex1 = src2.indexOf(linkKey1)
                val secondLinkIndex2 = src2.indexOf(linkKey2)
                val secondFunctionLink = src2.substring(
                    secondLinkIndex1 + linkKey1.length, secondLinkIndex2
                )
                videoDownloadData.driver.navigate().to(fullLink)
                while (qualityRetry <= Defaults.QUALITY_RETRIES.int()) {
                    try {
                        videoDownloadData.base.executeJs(
                            JavascriptHelper.changeUrlToVideoFunction(
                                secondFunctionLink
                            )
                        )
                        delay(videoDownloadData.pageChangeWaitTime)
                        val scriptError = videoDownloadData.base.findLinkError()
                        if (scriptError.isNotEmpty()) {
                            qualityRetry++
                            if (qualityRetry == Defaults.QUALITY_RETRIES.int() / 2) {
                                videoDownloadData.error(
                                    "(2nd) The script has failed to find qualities. | Retrying ($qualityRetry)",
                                    scriptError
                                )
                            }
                            videoDownloadData.base.clearLogs()
                            continue
                        }
                        if (src.contains("404 Not Found") || src.contains("404 - Page not Found")) {
                            videoDownloadData.error(
                                "(2nd) (404) Failed to find qualities. | Retrying..."
                            )
                            qualityRetry++
                            videoDownloadData.base.clearLogs()
                            continue
                        }
                        val links = videoDownloadData.base.findLinks()
                        if (links.isNotEmpty()) {
                            links.forEach { link ->
                                val dlData = DownloadData(
                                    link.quality,
                                    link.url,
                                    true
                                )
                                if (!downloadDatas.contains(dlData)) {
                                    downloadDatas.add(dlData)
                                    videoDownloadData.info(
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
                            videoDownloadData.error(
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
                videoDownloadData.info("Successfully found qualities. Size: ${downloadDatas.size}")
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
        val errorJob = launch(Dispatchers.Default) {
            while (isActive) {
                if (exx != null) {
                    cancel("", exx)
                }
            }
        }
        val m3u8Path = System.getProperty("user.home") + "/.m3u8_files/${saveFile.nameWithoutExtension}/"
        if (saveFile.exists()) {
            if (saveFile.length() >= Constants.minFileSize) {
                if (errorJob.isActive) {
                    errorJob.cancel()
                }
                videoDownloadData.message("(IO) Skipping completed video.")
                videoDownloadData.currentDownload.downloadPath = saveFile.absolutePath
                videoDownloadData.currentDownload.fileSize = saveFile.length()
                videoDownloadData.currentDownload.manualProgress = true
                videoDownloadData.currentDownload.downloading = false
                videoDownloadData.currentDownload.queued = false
                videoDownloadData.currentDownload.update()
                videoDownloadData.finishEpisode()
                return@withContext false
            }
        }
        val hasSeparateAudio = downloadData.separateAudioLink.isNotEmpty()
        val videoListener = M3u8DownloadListener(
            downloadStarted = {
                videoDownloadData.message("Starting m3u8 video download with ${downloadData.quality.tag} quality.")
                videoDownloadData.currentDownload.queued = false
                videoDownloadData.currentDownload.downloading = true
                videoDownloadData.currentDownload.manualProgress = true
                videoDownloadData.currentDownload.update()
            },
            downloadFinished = { download, success ->
                if (!success) {
                    exx = Exception("Failed to download m3u8 file. Incomplete ts files.")
                    return@M3u8DownloadListener
                }
                videoDownloadData.info("Finished m3u8 video download.")
                if (!hasSeparateAudio) {
                    videoDownloadData.currentDownload.downloading = false
                    videoDownloadData.currentDownload.update()
                }
            },
            downloadProgress = { progress, seconds ->
                Core.child.m3u8UpdateDownloadProgress(
                    videoDownloadData.currentDownload,
                    progress,
                    true,
                    seconds
                )
            },
            downloadSizeUpdated = {
                videoDownloadData.currentDownload.fileSize = it
                videoDownloadData.currentDownload.update()
            },
            onMergeStarted = {
                Core.child.m3u8UpdateDownloadProgress(
                    videoDownloadData.currentDownload,
                    "Merging video ts files",
                    true,
                    0
                )
            },
            onMergeFinished = { download, ex ->
                if (ex == null) {
                    Core.child.m3u8UpdateDownloadProgress(
                        videoDownloadData.currentDownload,
                        "Finalizing video",
                        true,
                        0
                    )
                    videoDownloadData.currentDownload.fileSize = saveFile.length()
                    videoDownloadData.currentDownload.update()
                    if (saveFile.exists() && saveFile.length() >= Constants.minFileSize) {
                        if (!hasSeparateAudio) {
                            videoDownloadData.currentDownload.downloading = false
                            videoDownloadData.currentDownload.update()
                            Core.child.downloadThread.incrementDownloadsFinished()
                            videoDownloadData.message("Successfully downloaded with ${downloadData.quality.tag} quality.")
                            videoDownloadData.finishEpisode()
                            if (errorJob.isActive) {
                                errorJob.cancel()
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
                    videoDownloadData.info("Downloading separate audio for video.")
                },
                downloadFinished = { download, success ->
                    if (exx != null) {
                        return@M3u8DownloadListener
                    }
                    if (!success) {
                        exx = Exception("Failed to download m3u8 audio file.")
                        return@M3u8DownloadListener
                    }
                    videoDownloadData.info("Finished downloading audio file.")
                },
                downloadProgress = { progress, seconds ->
                    Core.child.m3u8UpdateDownloadProgress(
                        videoDownloadData.currentDownload,
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
                        videoDownloadData.currentDownload,
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
                        videoDownloadData.currentDownload,
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
                httpRequestConfig(videoDownloadData.userAgent),
                m3u8VideoDownload
            )
            if (hasSeparateAudio && exx == null) {
                if (stopped) {
                    throw Exception("Stopped download manually.")
                }
                if (m3u8AudioDownload == null) {
                    throw Exception("Failed to create separate audio download.")
                }
                if (!saveFile.exists() || saveFile.length() < Constants.minFileSize) {
                    throw Exception("Failed to download separate audio. The video file doesn't exist.")
                }
                val stoppedAudio = M3u8Downloads.download(
                    httpRequestConfig(videoDownloadData.userAgent),
                    m3u8AudioDownload
                )
                if (stoppedAudio) {
                    throw Exception("Stopped audio download manually.")
                }
                if (audioSaveFile.exists()) {
                    mergedVideoAndAudioFile.parentFile.mkdirs()
                    videoDownloadData.info("Finished merging audio ts files. Attempting to merge video & audio...")
                    val success = VideoUtil.mergeVideoAndAudio(
                        saveFile,
                        audioSaveFile,
                        mergedVideoAndAudioFile
                    )
                    Core.child.finalizeM3u8DownloadProgress(
                        videoDownloadData.currentDownload,
                        mergedVideoAndAudioFile.length(),
                        success
                    )
                    if (success) {
                        videoDownloadData.message("Successfully merged video and audio.")
                        mergedVideoAndAudioFile.copyTo(
                            saveFile,
                            true
                        )
                        audioSaveFile.delete()
                        mergedVideoAndAudioFile.parentFile.deleteRecursively()
                        Core.child.downloadThread.incrementDownloadsFinished()
                        videoDownloadData.message(
                            "Successfully downloaded with ${downloadData.quality.tag} quality."
                        )
                        videoDownloadData.finishEpisode()
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
            saveFile.delete()
            mergedVideoAndAudioFile.delete()
            audioSaveFile.delete()

            videoDownloadData.m3u8Retries++

            if (videoDownloadData.m3u8Retries >= Defaults.M3u8_RETRIES.int()) {
                if (videoDownloadData.mCurrentDownload != null) {
                    Core.child.removeDownload(videoDownloadData.currentDownload)
                    videoDownloadData.mCurrentDownload = null
                }
                videoDownloadData.downloadDatas.remove(downloadData)
                if (videoDownloadData.downloadDatas.isNotEmpty()) {
                    videoDownloadData.error(
                        "Failed to download m3u8 video. | Retrying with different quality.",
                        e
                    )
                } else {
                    videoDownloadData.m3u8SecondVideoCheck = true
                    videoDownloadData.error(
                        "Failed to download m3u8 video. | Trying 2nd video.",
                        e
                    )
                }
            } else {
                videoDownloadData.error(
                    "Failed to download m3u8 video. | Retrying",
                    e
                )
            }
            return@withContext false
        } finally {
            if (errorJob.isActive) {
                errorJob.cancel()
            }
        }
        return@withContext true
    }

    suspend fun handleSecondVideo() = withContext(Dispatchers.IO) {
        val qualities = videoDownloadData.downloadDatas.filter {
            it.secondFrame
        }.toMutableList()
        if (qualities.isEmpty()) {
            return@withContext
        }
        videoDownloadData.message("First video download is complete. Now downloading the second video.")
        var retries = 0
        var preferredDownload: DownloadData? = null
        val qualityOption = temporaryQuality ?: Quality.qualityForTag(
            Defaults.QUALITY.string()
        )
        val episodeName = episode.name.fixForFiles() + "-01"
        val saveFile = videoDownloadData.generateEpisodeSaveFile(qualityOption, "-01")
        var currentDownload = downloadForNameAndQuality(
            episodeName,
            qualityOption
        )
        while (Core.child.isRunning) {
            try {
                if (retries > Defaults.VIDEO_RETRIES.int()) {
                    saveFile.delete()
                    videoDownloadData.message("Reached max retries of ${Defaults.VIDEO_RETRIES.int()}. Skipping 2nd video download.")
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
                    videoDownloadData.debug("(2nd) Checking video file size with $fileSizeRetries max retries.")
                    for (i in 0..fileSizeRetries) {
                        originalFileSize = fileSize(
                            preferredDownload.downloadLink,
                            videoDownloadData.userAgent,
                            headMode
                        )
                        if (originalFileSize <= Constants.minFileSize) {
                            headMode = headMode.not()
                            if (i == fileSizeRetries / 2) {
                                videoDownloadData.error(
                                    "(2nd) Failed to find video file size. Current retries: $i"
                                )
                                videoDownloadData.message(
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
                            videoDownloadData.error(
                                "(2nd) Failed to find video file size after $fileSizeRetries retries. | Using another quality."
                            )
                            qualities.remove(preferredDownload)
                            retries++
                            if (qualities.isEmpty()) {
                                videoDownloadData.error(
                                    "(2nd) Failed to find video file size. There are no more qualities to check. | Skipping episode"
                                )
                                saveFile.delete()
                                break
                            }
                            continue
                        } else {
                            videoDownloadData.error(
                                "(2nd) Failed to find video file size. There are no more qualities to check. | Skipping episode"
                            )
                            saveFile.delete()
                            break
                        }
                    }
                    videoDownloadData.debug(
                        "(2nd) Successfully found video file size of: ${
                            Tools.bytesToString(
                                originalFileSize
                            )
                        }"
                    )
                    if (currentDownload != null) {
                        if (currentDownload.downloadPath.isEmpty()
                            || !File(currentDownload.downloadPath).exists()
                            || currentDownload.downloadPath != saveFile.absolutePath
                        ) {
                            currentDownload.downloadPath = saveFile.absolutePath
                        }
                        Core.child.addDownload(currentDownload)
                        if (currentDownload.isComplete) {
                            videoDownloadData.message("(DB) (2nd) Skipping completed video.")
                            currentDownload.downloading = false
                            currentDownload.queued = false
                            currentDownload.update()
                            break
                        } else {
                            currentDownload.queued = true
                            currentDownload.update()
                        }
                    }
                    videoDownloadData.info("(2nd) Successfully found video link with $retries retries.")
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
                        videoDownloadData.info("(2nd) Created new download.")
                    } else {
                        videoDownloadData.info("(2nd) Using existing download.")
                    }
                    if (saveFile.exists()) {
                        if (saveFile.length() >= originalFileSize) {
                            videoDownloadData.message("(IO) (2nd) Skipping completed video.")
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
                            videoDownloadData.error(
                                "(2nd) Failed to create video file after 3 retries. | Skipping episode",
                                fileError,
                                true
                            )
                            saveFile.delete()
                            break
                        }
                    }
                    videoDownloadData.message("(2nd) Starting download with ${qualityOption.tag} quality.")
                    currentDownload.queued = false
                    currentDownload.downloading = true
                    currentDownload.fileSize = originalFileSize
                    Core.child.addDownload(currentDownload)
                    currentDownload.update()
                    downloadVideo(
                        preferredDownload.downloadLink,
                        saveFile,
                        videoDownloadData,
                        currentDownload
                    )
                    originalFileSize = saveFile.length()
                    currentDownload.downloading = false
                    //second time to ensure ui update
                    currentDownload.update()
                    if (saveFile.exists() && saveFile.length() >= originalFileSize) {
                        Core.child.downloadThread.incrementDownloadsFinished()
                        videoDownloadData.message("(2nd) Successfully downloaded with ${qualityOption.tag} quality.")
                        break
                    }
                } else {
                    videoDownloadData.message("(2nd) Download link is empty. There's no more qualities to check. | Skipping episode")
                    saveFile.delete()
                    break
                }
            } catch (e: Exception) {
                if (currentDownload != null) {
                    currentDownload.queued = true
                    currentDownload.downloading = false
                    currentDownload.update()
                }
                videoDownloadData.error(
                    "(2nd) Failed to download video. Retrying...",
                    e
                )
                retries++
            }
        }
    }

}