package nobility.downloader.core.scraper

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.*
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Episode
import nobility.downloader.core.scraper.video_download.VideoDownloadHandler
import nobility.downloader.core.settings.Defaults
import nobility.downloader.utils.FrogLog
import java.util.*

/**
 * Used to handle video download tasks.
 * This will continuously run during the entire apps lifespan.
 */
class DownloadThread {

    val downloadQueue: MutableList<Episode> = Collections.synchronizedList(mutableListOf())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile
    var hasStartedDownloading = false
    @Volatile
    private var currentJobs = 0

    @Volatile
    var downloadsInProgress = mutableStateOf(0)
        private set

    @Volatile
    var downloadsInQueue = mutableStateOf(0)
        private set

    @Volatile
    var downloadsFinishedForSession = 0

    suspend fun run() = withContext(scope.coroutineContext) {
        while (isActive) {

            val maxThreads = Defaults.DOWNLOAD_THREADS.int()
            val slots = (maxThreads - currentJobs).coerceAtLeast(0)

            if (Core.child.isRunning && downloadQueue.isNotEmpty() && slots > 0) {
                repeat(downloadQueue.size.coerceAtMost(slots)) {
                    launchDownloadJob()
                }
            }
            if (hasFinished()) {
                val finished = downloadsFinishedForSession
                if (finished > 0) {
                    FrogLog.writeMessage("Gracefully finished downloading $finished video(s).")
                } else {
                    FrogLog.writeMessage("Gracefully finished. No downloads have been made.")
                }
                Core.child.stop()
                hasStartedDownloading = false
            }
            delay(500)
        }
    }

    private fun launchDownloadJob(): Job? {
        val nextDownload = nextDownload
        if (nextDownload == null) {
            return null
        }
        return scope.launch(Dispatchers.IO) {
            currentJobs++
            val downloader = VideoDownloadHandler(nextDownload)
            try {
                downloader.run()
            } catch (e: Exception) {
                FrogLog.logError(
                    "Failed to finish download.",
                    e
                )
            } finally {
                downloader.killDriver()
                currentJobs--
            }
        }
    }

    private fun hasFinished(): Boolean {
        if (!hasStartedDownloading) {
            return false
        }
        val noJobs = currentJobs <= 0
        val nothingToDownload = downloadQueue.isEmpty()
        return noJobs && nothingToDownload
    }

    fun addToQueue(vararg downloads: Episode): Int {
        return addToQueue(*downloads)
    }

    fun addToQueue(downloads: List<Episode>): Int {
        var added = 0
        for (dl in downloads) {
            if (!isInQueue(dl)) {
                downloadQueue.add(dl)
                added++
            }
        }
        synchronizeDownloadsInQueue()
        return added
    }

    @get:Synchronized
    private val nextDownload: Episode?
        get() {
            if (downloadQueue.isEmpty()) {
                return null
            }
            val link = downloadQueue.first()
            downloadQueue.removeAt(0)
            synchronizeDownloadsInQueue()
            return link
        }

    private fun isInQueue(episode: Episode): Boolean {
        return downloadQueue.any { it.matches(episode) }
    }

    fun clear() {
        downloadQueue.clear()
    }

    fun stop() {
        scope.cancel()
    }

    fun synchronizeDownloadsInQueue() {
        downloadsInQueue.value = downloadQueue.size
    }

    @Synchronized
    fun incrementDownloadsFinished() {
        downloadsFinishedForSession++
    }

    @Synchronized
    fun incrementDownloadsInProgress() {
        downloadsInProgress.value++
    }

    @Synchronized
    fun decrementDownloadsInProgress() {
        if (downloadsInProgress.value > 0) {
            downloadsInProgress.value--
        }
    }

}