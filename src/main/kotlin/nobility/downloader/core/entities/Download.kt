package nobility.downloader.core.entities

import androidx.compose.runtime.mutableStateOf
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Transient
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.settings.Quality
import nobility.downloader.core.settings.Quality.Companion.qualityForResolution
import nobility.downloader.utils.Tools
import java.io.File

@Entity
data class Download(
    var downloadPath: String = "",
    var dateAdded: Long = 0,
    var resolution: Int = 0
) : Episode() {

    var manualProgress = false

    @Transient
    var downloading = false
    @Transient
    var paused = false
    @Transient
    var queued = false
    @Transient
    var videoProgress = mutableStateOf("0%")
    @Transient
    var audioProgress = mutableStateOf("")
    @Transient
    var remainingVideoDownloadSeconds = mutableStateOf(0)
    @Transient
    var remainingAudioDownloadSeconds = mutableStateOf(0)

    fun update(download: Download, updateProperties: Boolean) {
        downloadPath = download.downloadPath
        dateAdded = download.dateAdded
        fileSize = download.fileSize
        queued = download.queued
        downloading = download.downloading
        resolution = download.resolution
        manualProgress = download.manualProgress
        remainingVideoDownloadSeconds = download.remainingVideoDownloadSeconds
        remainingAudioDownloadSeconds = download.remainingAudioDownloadSeconds
        if (updateProperties) {
            updateProgress()
        }
        BoxHelper.shared.downloadBox.put(this)
    }

    fun matches(download: Download): Boolean {
        return download.id > 0
                && download.id == id
                || download.slug == slug
                && resolution == download.resolution
                && name == download.name
    }

    fun updateProgress() {
        if (queued) {
            setVideoProgressValue("Queued")
            return
        } else if (paused) {
            setVideoProgressValue("Paused")
            return
        }
        if (manualProgress && downloading) {
            return
        }
        val video = downloadFile()
        if (video != null && video.exists()) {
            if (manualProgress) {
                setVideoProgressValue("100%")
            } else {
                val ratio = video.length() / fileSize.toDouble()
                setVideoProgressValue(Tools.percentFormat.format(ratio))
            }
        } else {
            setVideoProgressValue("File Not Found")
        }
    }

    fun updateVideoSeconds(seconds: Int) {
        remainingVideoDownloadSeconds.value = seconds
    }

    fun updateAudioSeconds(seconds: Int) {
        remainingAudioDownloadSeconds.value = seconds
    }

    fun setVideoProgressValue(
        value: String
    ) {
        if (videoProgress.value != value) {
            videoProgress.value = value
        }
    }

    fun updateProgress(value: String, video: Boolean) {
        if (video) {
            videoProgress.value = value
        } else {
            audioProgress.value = value
        }
    }

    fun downloadFile(): File? {
        if (downloadPath.isNotEmpty()) {
            val file = File(downloadPath)
            if (file.exists()) {
                return file
            }
        }
        return null
    }

    val isComplete: Boolean
        get() {
            if (fileSize <= 5000) {
                return false
            }
            val file = downloadFile()
            return if (file != null) {
                if (file.length() <= 0) {
                    false
                } else {
                    file.length() >= fileSize
                }
            } else false
        }

    fun nameAndResolution(): String {
        val quality = qualityForResolution(resolution)
        val extra = if (quality == Quality.LOW) "" else " (" + quality.tag + ")"
        return name + extra
    }
}
