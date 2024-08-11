package nobility.downloader.utils

import kotlinx.coroutines.delay
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Download

class DownloadUpdater(
    private val download: Download
) {

    var remainingSeconds = -1
    var running = true

    suspend fun run() {
        while (running) {
            Core.child.updateDownloadProgress(download, remainingSeconds)
            delay(1000)
        }
        Core.child.updateDownloadProgress(download, remainingSeconds)
    }
}