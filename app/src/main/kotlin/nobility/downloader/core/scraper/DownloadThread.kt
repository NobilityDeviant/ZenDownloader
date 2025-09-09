package nobility.downloader.core.scraper

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.*
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Episode
import nobility.downloader.core.scraper.data.DownloadQueue
import nobility.downloader.core.scraper.data.M3U8Data
import nobility.downloader.core.scraper.video_download.VideoDownloadHandler
import nobility.downloader.core.settings.Defaults
import nobility.downloader.utils.FrogLog
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Used to handle video download tasks.
 * This will continuously run during the entire apps' lifespan.
 */
class DownloadThread {

    val downloadQueue: MutableList<DownloadQueue> = Collections
        .synchronizedList(mutableStateListOf())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var currentJobs = AtomicInteger(0)
    private var stopJob: Job? = null

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
            val headless = Defaults.HEADLESS_MODE.boolean()
            val maxThreads = if (!headless) 1 else Defaults.DOWNLOAD_THREADS.int()
            val availableSlots = (maxThreads - currentJobs.get()).coerceAtLeast(0)
            if (Core.child.isRunning && availableSlots > 0) {
                var launched = 0
                while (launched < availableSlots) {
                    launchDownloadJob()
                    launched++
                }
            }
            delay(500)
        }
    }

    suspend fun launchStopJob() = withContext(scope.coroutineContext) {
        stopJob?.cancel()
        delay(5000)
        stopJob = launch {
            while (isActive) {
                if (hasFinished()) {
                    val finished = downloadsFinishedForSession
                    if (finished > 0) {
                        FrogLog.message("Gracefully finished downloading $finished video(s).")
                    } else {
                        FrogLog.message("Gracefully finished. No downloads have been made.")
                    }
                    Core.child.stop()
                    break
                }
                delay(2500)
            }
        }
    }

    private fun launchDownloadJob() {
        val nextDownload = nextDownload
        if (nextDownload == null) {
            return
        }
        currentJobs.incrementAndGet()
        scope.launch(Dispatchers.IO) {
            val downloader = VideoDownloadHandler(nextDownload)
            try {
                downloader.run()
            } catch (e: Exception) {
                FrogLog.error(
                    "Failed to finish download.",
                    e
                )
            } finally {
                downloader.killDriver()
                currentJobs.decrementAndGet()
            }
        }
    }

    private fun hasFinished(): Boolean {
        val noJobs = currentJobs.get() <= 0
        val nothingToDownload = isQueueEmpty
        return noJobs && nothingToDownload
    }

    fun addToQueue(
        episode: Episode,
        m3U8Data: M3U8Data
    ): Boolean {
        if (!isInQueue(episode)) {
            downloadQueue.add(
                DownloadQueue(
                    episode,
                    m3U8Data
                )
            )
            synchronizeDownloadsInQueue()
            return true
        }
        return false
    }

    fun addToQueue(vararg downloads: Episode): Int {
        return addToQueue(listOf(*downloads))
    }

    fun addToQueue(downloads: List<Episode>): Int {
        var added = 0
        for (dl in downloads) {
            if (!isInQueue(dl)) {
                downloadQueue.add(
                    DownloadQueue(dl)
                )
                added++
            }
        }
        synchronizeDownloadsInQueue()
        return added
    }

    fun removeFromQueue(
        queue: DownloadQueue
    ) {
        downloadQueue.removeIf {
            it.episode.matches(queue.episode)
        }
        synchronizeDownloadsInQueue()
    }

    @get:Synchronized
    private val nextDownload: DownloadQueue?
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
        return downloadQueue.any {
            it.episode.matches(episode)
        }
    }

    fun clear() {
        downloadQueue.clear()
        synchronizeDownloadsInQueue()
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

    val isQueueEmpty get() = downloadQueue.isEmpty()

}