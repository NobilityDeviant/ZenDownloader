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
    var fileSize: Long = 0,
    var resolution: Int = 0
) : Episode() {

    @Transient
    var downloading = false

    @Transient
    var paused = false

    @Transient
    var queued = false

    @Transient
    var progress = mutableStateOf("0%")

    @Transient
    var remainingDownloadSeconds = mutableStateOf(0)

    fun update(download: Download, updateProperties: Boolean) {
        downloadPath = download.downloadPath
        dateAdded = download.dateAdded
        fileSize = download.fileSize
        queued = download.queued
        downloading = download.downloading
        resolution = download.resolution
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
    }

    fun updateProgress() {
        if (queued) {
            setProgressValue("Queued")
            return
        } else if (paused) {
            setProgressValue("Paused")
            return
        }
        val video = downloadFile()
        if (video != null && video.exists()) {
            val ratio = video.length() / fileSize.toDouble()
            setProgressValue(Tools.percentFormat.format(ratio))
        } else {
            setProgressValue("File Not Found")
        }
    }

    private fun setProgressValue(value: String) {
        if (progress.value != value) {
            progress.value = value
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
