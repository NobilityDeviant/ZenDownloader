package nobility.downloader.core.scraper.video_download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.BoxHelper.Companion.downloadForSlugAndQuality
import nobility.downloader.core.BoxHelper.Companion.seriesForSlug
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.Core
import nobility.downloader.core.driver.BasicDriverBase
import nobility.downloader.core.entities.Download
import nobility.downloader.core.entities.Episode
import nobility.downloader.core.scraper.data.QualityAndDownload
import nobility.downloader.core.settings.Defaults
import nobility.downloader.core.settings.Quality
import nobility.downloader.utils.Constants
import nobility.downloader.utils.FrogLog
import nobility.downloader.utils.Tools
import nobility.downloader.utils.fixForFiles
import java.io.File

/**
 * This class is used to store the current data of a video downloader thread.
 * We use it in a separate class to be able to pass the data to any other function.
 */
class VideoDownloadData(
    val temporaryQuality: Quality?
) {

    val base = BasicDriverBase()
    val driver get() = base.driver
    val userAgent get() = base.userAgent
    val taskScope = CoroutineScope(Dispatchers.Default)
    val pageChangeWaitTime = 5_000L //in milliseconds
    var mCurrentEpisode: Episode? = null
    var mCurrentDownload: Download? = null
    val currentEpisode get() = mCurrentEpisode!!
    val currentDownload get() = mCurrentDownload!!
    var retries = 0
    var resRetries = 0
    var m3u8Retries = 0
    val qualityAndDownloads = mutableListOf<QualityAndDownload>()

    fun createDownload(
        slug: String,
        qualityOption: Quality,
        saveFile: File
    ): Boolean {
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
                finishEpisode()
                return false
            } else {
                currentDownload.queued = true
                Core.child.updateDownloadProgress(currentDownload)
            }
        }
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
        return true
    }

    fun generateSaveFile(
        updatedQuality: Quality,
        extraName: String = ""
    ): File {
        if (mCurrentEpisode == null) {
            throw Exception("Failed to generate save folder. The current episode is null.")
        }
        val series = seriesForSlug(currentEpisode.seriesSlug)
        val downloadFolderPath = Defaults.SAVE_FOLDER.string()
        val episodeName = currentEpisode.name.fixForFiles() + extraName
        val seasonFolder = if (Defaults.SEPARATE_SEASONS.boolean())
            Tools.findSeasonFromEpisode(episodeName) else null
        var saveFolder = File(
            downloadFolderPath + File.separator
                    + (series?.name?.fixForFiles() ?: "NoSeries")
                    + if (seasonFolder != null) (File.separator + seasonFolder + File.separator) else ""
        )
        if (!saveFolder.exists() && !saveFolder.mkdirs()) {
            logError(
                "Failed to create series save folder: ${saveFolder.absolutePath} " +
                        "Defaulting to $downloadFolderPath/NoSeries"
            )
            saveFolder = File(downloadFolderPath + File.separator + "NoSeries")
        }
        val extraQualityName = if (updatedQuality != Quality.LOW)
            " (${updatedQuality.tag})" else ""
        val saveFile = File(
            saveFolder.absolutePath + File.separator
                    + "$episodeName$extraQualityName.mp4"
        )
        return saveFile
    }

    fun quickCheckVideoExists(slug: String): Boolean {
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
                return true
            }
        }
        return false
    }

    fun getNewEpisode(): Boolean {
        if (mCurrentEpisode == null) {
            qualityAndDownloads.clear()
            mCurrentEpisode = Core.child.nextEpisode
            if (mCurrentEpisode == null) {
                return false
            }
            resRetries = 0
            retries = 0
            m3u8Retries = 0
            Core.child.incrementDownloadsInProgress()
        }
        return true
    }

    fun reachedMax(): Boolean {
        if (retries >= Constants.maxRetries) {
            if (mCurrentEpisode != null) {
                writeMessage("Reached max retries of ${Constants.maxRetries}.")
            }
            finishEpisode()
            resRetries = 0
            retries = 0
            m3u8Retries = 0
            return true
        }
        return false
    }

    fun reachedMaxM3U8(): Boolean {
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
            return true
        }
        return false
    }

    fun finishEpisode() {
        if (mCurrentDownload != null) {
            currentDownload.queued = false
            currentDownload.downloading = false
            Core.child.updateDownloadInDatabase(
                currentDownload
            )
            mCurrentDownload = null
        }
        if (mCurrentEpisode != null) {
            mCurrentEpisode = null
            Core.child.decrementDownloadsInProgress()
        }
    }

    fun writeMessage(s: String) {
        FrogLog.writeMessage("$tag $s")
    }

    fun logInfo(s: String) {
        FrogLog.logInfo(
            "$tag $s"
        )
    }

    fun logError(
        message: String? = null,
        e: Throwable?,
        important: Boolean = false
    ) {
        FrogLog.logError(
            "$tag $message",
            e,
            important
        )
    }

    fun logError(
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

    private val tag get() = if (mCurrentEpisode != null) "[S] [${currentEpisode.name}]" else "[S] [No Episode]"
}