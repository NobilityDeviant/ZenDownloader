package nobility.downloader.core.scraper.video_download.m3u8_downloader

import kotlinx.coroutines.*
import nobility.downloader.core.Core
import nobility.downloader.core.scraper.video_download.m3u8_downloader.core.M3u8Download
import nobility.downloader.core.scraper.video_download.m3u8_downloader.core.M3u8Downloader
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.config.HttpRequestManagerConfig
import nobility.downloader.utils.FrogLog
import kotlin.coroutines.cancellation.CancellationException

object M3u8Downloads {

    suspend fun download(
        managerConfig: HttpRequestManagerConfig,
        download: M3u8Download
    ): Boolean = withContext(Dispatchers.IO) {

        val downloader = M3u8Downloader(managerConfig)
        var stopped = false

        val watcher = launch {
            while (Core.child.isRunning) {
                delay(100)
            }

            if (isActive) {
                stopped = true
                try {
                    downloader.shutdown()
                    FrogLog.info("Shutdown M3u8Downloader gracefully.")
                } catch (e: Exception) {
                    FrogLog.error("Failed to shutdown M3u8Downloader.", e)
                }
            }

            cancel()
        }

        try {
            downloader.run(download)
        } catch (_: CancellationException) {
        } finally {
            watcher.cancel()
            downloader.shutdown()
        }

        return@withContext stopped
    }

}
