package nobility.downloader.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.bonigarcia.wdm.WebDriverManager
import kotlinx.coroutines.*
import nobility.downloader.Page
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.driver.undetected_chrome.ChromeDriverBuilder
import nobility.downloader.core.driver.undetected_chrome.UndetectedChromeDriver
import nobility.downloader.core.entities.Download
import nobility.downloader.core.scraper.DownloadHandler
import nobility.downloader.core.scraper.DownloadThread
import nobility.downloader.core.scraper.MovieHandler
import nobility.downloader.core.settings.Defaults
import nobility.downloader.core.updates.UrlUpdater
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.ui.components.dialog.DialogHelper.showError
import nobility.downloader.utils.AppInfo
import nobility.downloader.utils.Constants
import nobility.downloader.utils.FrogLog
import nobility.downloader.utils.update
import org.openqa.selenium.WebDriver
import java.io.File
import java.net.URI
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.system.exitProcess


class CoreChild {

    val downloadThread = DownloadThread()
    @Volatile
    var isRunning = false
        private set
    var isUpdating by mutableStateOf(false)
    var shutdownExecuted by mutableStateOf(false)
    var shutdownProgressIndex by mutableStateOf(0)
    var shutdownProgressTotal by mutableStateOf(0)
    val runningDrivers: MutableMap<String, WebDriver> = Collections.synchronizedMap(mutableMapOf())
    val downloadList: MutableList<Download> = Collections.synchronizedList(mutableStateListOf())
    lateinit var movieHandler: MovieHandler
    var forceStopped = false

    fun init() {
        BoxHelper.shared.downloadBox.all.forEach {
            addDownload(it)
        }
        movieHandler = MovieHandler()
        Core.taskScope.launch(Dispatchers.IO) {
            launch {
                UrlUpdater.updateWcoUrl()
            }
            movieHandler.loadMovies()
            val logger = Logger.getLogger("org.openqa.selenium.remote.http.WebSocket")
            logger.setLevel(Level.SEVERE)

            WebDriverManager.chromedriver().setup()
            launch {
                downloadThread.run()
            }
        }
    }

    /**
     * New rule. start can only be used by the user.
     * I have replaced every other use of it with a better solution.
     */
    fun start() {
        if (!canStart()) {
            return
        }
        softStart()
        val url = Core.currentUrl
        Core.taskScope.launch {
            val result = DownloadHandler.run(url)
            if (result.isFailed) {
                withContext(Dispatchers.Main) {
                    stop()
                    showError(
                        "Failed to find series from: $url",
                        result.message
                    )
                }
            } else {
                launchStopJob()
            }
        }
    }

    fun launchStopJob() {
        Core.taskScope.launch {
            downloadThread.launchStopJob()
        }
    }

    fun softStart() {
        forceStopped = false
        downloadThread.clear()
        Core.startButtonEnabled = false
        Core.stopButtonEnabled = true
        isRunning = true
        downloadThread.downloadsFinishedForSession = 0
        downloadThread.synchronizeDownloadsInQueue()
    }

    fun stop() {
        if (!isRunning) {
            return
        }
        Core.startButtonEnabled = true
        Core.stopButtonEnabled = false
        isRunning = false
        downloadThread.clear()
        downloadThread.downloadsInProgress.value = 0
        downloadThread.synchronizeDownloadsInQueue()
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun shutdown(force: Boolean = false) {
        if (force) {
            downloadThread.stop()
            Core.taskScope.cancel()
            shutdownExecuted = true
            stop()
            GlobalScope.launch { killAllDrivers() }
            return
        }
        if (isRunning) {
            DialogHelper.showConfirm(
                """
                    You are currently downloading videos.
                    Shutting down will stop all downloads and possibly corrupt any incomplete video.
                    It's advised to press the Stop button and wait for it to fully stop beforehand.
                    Do you wish to continue?
                """.trimIndent(),
                "Shutdown"
            ) {
                downloadThread.stop()
                Core.taskScope.cancel()
                shutdownExecuted = true
                stop()
                GlobalScope.launch { killAllDrivers() }
            }
        } else {
            downloadThread.stop()
            Core.taskScope.cancel()
            shutdownExecuted = true
            GlobalScope.launch { killAllDrivers() }
        }
    }

    private val maxRetries = 2
    private val killTimeout = 7000L

    private suspend fun killAllDrivers(): Nothing = withContext(Dispatchers.Default) {
        val tempFolderDir = AppInfo.databasePath + "udc_temp" +
                File.separator +
                "udc_${System.currentTimeMillis()}" +
                File.separator

        val tempFolder = File(tempFolderDir)

        val drivers = synchronized(runningDrivers) { runningDrivers.toMap() }

        if (drivers.isEmpty()) {
            tempFolder.deleteRecursively()
            exitProcess(0)
        }

        shutdownProgressTotal = drivers.size
        var index = 0

        for ((key, driver) in drivers) {
            val success = attemptKillDriver(key, driver)

            if (!success) {
                FrogLog.error("All attempts failed to kill driver ($key)")
            }

            runningDrivers.remove(key)
            shutdownProgressIndex = ++index
        }

        FrogLog.info("Driver shutdown complete. exiting...")
        tempFolder.deleteRecursively()
        exitProcess(0)
    }

    private suspend fun attemptKillDriver(key: String, driver: WebDriver): Boolean {
        repeat(maxRetries) { attempt ->
            FrogLog.info("Killing driver [$key] — attempt ${attempt + 1}/$maxRetries")
            try {
                val result = withTimeoutOrNull(killTimeout) {
                    if (driver is UndetectedChromeDriver) {
                        driver.kill()
                    } else {
                        driver.quit()
                    }
                    true
                }

                if (result == true) {
                    FrogLog.info("Successfully killed driver [$key]")
                    return true
                } else {
                    FrogLog.info(
                        "Attempt ${attempt + 1} timed out for driver [$key]"
                    )
                }
            } catch (e: Exception) {
                FrogLog.error(
                    "Failed to kill driver ($key) on attempt ${attempt + 1}",
                    e
                )
            }
        }
        return false
    }



    private fun canStart(): Boolean {
        if (isUpdating) {
            showError("You can't download videos while the app is updating.")
            return false
        }
        val downloadFolder = File(Defaults.SAVE_FOLDER.string())
        if (!downloadFolder.exists() && !downloadFolder.mkdirs()) {
            showError(
                "Your download folder doesn't exist and wasn't able to be created.",
                "Be sure to set it inside the settings before downloading videos."
            )
            Core.changePage(Page.SETTINGS)
            return false
        }
        try {
            if (!downloadFolder.canWrite()) {
                showError(
                    """
                       The download folder in your settings doesn't allow write permissions.
                       If this is a USB or SD Card, you have to disable write protection.
                       You can also try selecting a folder in the user or home folder or, running the app as admin/superuser permissions.
                    """.trimIndent()
                )
                Core.changePage(Page.SETTINGS)
                return false
            }
        } catch (e: Exception) {
            showError("Failed to check for write permissions.", e)
            return false
        }
        val url = Core.currentUrl
        if (url.isEmpty()) {
            showError(
                """
                    You must input a series or episode link.
                        
                    [Examples]
                        
                    Series: 
                        
                    ${Core.exampleSeries}
                        
                    Episode: 
                        
                    ${Core.exampleEpisode}
                    
                    You can also input a keyword to open & search inside the database window.
                        
                """.trimIndent(),
                size = DpSize(400.dp, 400.dp)
            )
            return false
        }
        if (isRunning) {
            Core.openWco(url)
            return false
        }
        try {
            URI(url).toURL()
        } catch (_: Exception) {
            Core.openWco(url)
            return false
        }
        val threads = Defaults.DOWNLOAD_THREADS.int()
        if (threads < Constants.minThreads) {
            showError("Your download threads must be at least ${Constants.minThreads}.")
            return false
        }
        if (threads > Constants.maxThreads) {
            showError("Your download threads can't be higher than ${Constants.maxThreads}.")
            return false
        }
        if (!ChromeDriverBuilder.isChromeInstalled()) {
            showError(
                """
                    Chrome isn't installed. Install it and restart the app.
                    For some help visit: https://github.com/NobilityDeviant/ZenDownloader/#download--install
                """.trimIndent()
            )
            return false
        }
        return true
    }

    private fun indexForDownload(download: Download): Int {
        for ((index, d) in downloadList.withIndex()) {
            if (d.matches(download)) {
                return index
            }
        }
        return -1
    }

    @Synchronized
    fun addDownload(download: Download) {
        val index = indexForDownload(download)
        if (index == -1) {
            download.updateProgress()
            downloadList.add(download)
        } else {
            //push it to the top of the list
            download.dateAdded = System.currentTimeMillis()
            download.update()
        }
    }

    fun removeDownload(download: Download) {
        downloadList.remove(download)
        BoxHelper.shared.downloadBox.remove(download)
    }

    fun finalizeM3u8DownloadProgress(
        download: Download,
        finalFileSize: Long,
        success: Boolean
    ) {
        val index = indexForDownload(download)
        if (index != -1) {
            downloadList[index].updateVideoSeconds(0)
            downloadList[index].updateAudioSeconds(0)
            if (success) {
                downloadList[index].updateProgress("", true)
            } else {
                downloadList[index].updateProgress("Failed to merge", true)
            }
            downloadList[index].updateProgress("", false)
            downloadList[index].downloading = false
            downloadList[index].manualProgress = true
            if (success) {
                downloadList[index].fileSize = finalFileSize
                downloadList[index].updateProgress()
            }
        }
    }

    fun m3u8UpdateDownloadProgress(
        download: Download,
        progress: String,
        isVideo: Boolean,
        remainingSeconds: Int = -1,
    ) {
        val index = indexForDownload(download)
        if (index != -1) {
            if (remainingSeconds > -1) {
                if (isVideo) {
                    downloadList[index].updateVideoSeconds(remainingSeconds)
                } else {
                    downloadList[index].updateAudioSeconds(remainingSeconds)
                }
            }
            if (isVideo) {
                downloadList[index].updateProgress(
                    progress,
                    true
                )
            } else {
                downloadList[index].updateProgress(
                    progress,
                    false
                )
            }
        }
    }

    fun updateDownloadInDatabase(
        download: Download,
        updateProperties: Boolean = true
    ) {
        val index = indexForDownload(download)
        if (index != -1) {
            downloadList[index].updateWithDownload(download, updateProperties)
        } else {
            BoxHelper.shared.downloadBox.put(download)
        }
    }

    fun updateDownloadProgress(
        download: Download,
        remainingSeconds: Int = -1,
        downloadSpeed: Long = -1
    ) {
        val index = indexForDownload(download)
        if (index != -1) {
            if (remainingSeconds > -1) {
                downloadList[index].updateVideoSeconds(remainingSeconds)
            }
            if (downloadSpeed > -1) {
                downloadList[index].updateDownloadSpeed(downloadSpeed)
            }
            downloadList[index].updateProgress()
        }
    }

    fun refreshDownloadsProgress() {
        downloadList.forEach {
            it.updateProgress()
        }
    }
}