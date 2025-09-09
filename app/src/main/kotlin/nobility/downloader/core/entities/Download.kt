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
    var downloadProgress = mutableStateOf("0%")

    @Transient
    var downloadSeconds = mutableStateOf(0)
        private set

    @Transient
    var downloadSpeed = mutableStateOf("")
        private set

    fun updateWithDownload(
        download: Download,
        updateProperties: Boolean
    ) {
        if (downloadPath != download.downloadPath) {
            downloadPath = download.downloadPath
        }
        if (dateAdded != download.dateAdded) {
            dateAdded = download.dateAdded
        }
        if (fileSize != download.fileSize) {
            fileSize = download.fileSize
        }
        if (queued != download.queued) {
            queued = download.queued
        }
        if (downloading != download.downloading) {
            downloading = download.downloading
        }
        if (resolution != download.resolution) {
            resolution = download.resolution
        }
        if (manualProgress != download.manualProgress) {
            manualProgress = download.manualProgress
        }
        if (downloadProgress.value != download.downloadProgress.value) {
            downloadProgress.value = download.downloadProgress.value
        }
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
            setProgress("Queued")
            return
        } else if (paused) {
            setProgress("Paused")
            return
        }
        if (manualProgress && downloading) {
            return
        }
        val video = downloadFile()
        if (video != null && video.exists()) {
            if (manualProgress) {
                setProgress("100%")
            } else {
                val ratio = video.length() / fileSize.toDouble()
                setProgress(Tools.percentFormat.format(ratio))
            }
        } else {
            setProgress("File Not Found")
        }
    }

    fun setSeconds(seconds: Int) {
        downloadSeconds.value = seconds
    }

    fun setDownloadSpeed(bps: Long) {
        downloadSpeed.value = " (${Tools.bytesToString(bps)}ps)"
    }

    fun setProgress(value: String) {
        downloadProgress.value = value
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
