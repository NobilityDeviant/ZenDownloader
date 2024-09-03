package nobility.downloader.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.BoxHelper.Companion.update
import nobility.downloader.core.entities.Download
import nobility.downloader.core.entities.Episode
import nobility.downloader.core.scraper.DownloadHandler
import nobility.downloader.core.scraper.MovieHandler
import nobility.downloader.core.settings.Defaults
import nobility.downloader.core.updates.UrlUpdater
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.ui.components.dialog.DialogHelper.showError
import nobility.downloader.utils.*
import org.openqa.selenium.WebDriver
import java.io.File
import java.net.URI
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger
import javax.net.ssl.HttpsURLConnection
import kotlin.system.exitProcess


class CoreChild {

    val taskScope = CoroutineScope(Dispatchers.Default)
    var isRunning = false
        private set
    var isUpdating by mutableStateOf(false)
    private var shutdownExecuted = false
    val runningDrivers: MutableList<WebDriver> = Collections.synchronizedList(mutableListOf())
    val currentEpisodes: MutableList<Episode> = Collections.synchronizedList(mutableListOf())
    val downloadList = mutableStateListOf<Download>()
    lateinit var movieHandler: MovieHandler
    @Volatile
    var downloadsFinishedForSession = 0
        private set
    @Volatile
    var downloadsInProgress = mutableStateOf(0)
        private set

    fun init() {
        if (!AppInfo.DEBUG_MODE) {
            System.setProperty("webdriver.chrome.silentOutput", "true")
            Logger.getLogger("org.openqa.selenium").level = Level.OFF
        }
        BoxHelper.shared.downloadBox.all.forEach {
            addDownload(it)
        }
        movieHandler = MovieHandler()
        taskScope.launch(Dispatchers.IO) {
            launch {
                UrlUpdater.updateWcoUrl()
            }
            movieHandler.loadMovies()
            //ok THIS is actually working.
            //cloudflare isn't blocking it
            //it shows the iframe too!
            //we can use this to easily access now? and fallback to chrome as a backup??!!!!!!
            val url = URI("https://www.wcofun.net/wistoria-wand-and-sword-episode-6-english-dubbed").toURL()
            val con = url.openConnection() as HttpsURLConnection
            con.addRequestProperty(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
            )
            con.addRequestProperty("Accept-Language", "en-US,en;q=0.9")
            con.addRequestProperty("Connection", "keep-alive")
            con.addRequestProperty("Referer", "https://www.wcofun.net/")
            con.addRequestProperty("Sec-Fetch-Dest", "document")
            con.addRequestProperty("Sec-Fetch-Mode", "navigate")
            con.addRequestProperty("Sec-Fetch-Site", "cross-site")
            con.addRequestProperty("Sec-Fetch-User", "?1")
            con.addRequestProperty("Upgrade-Insecure-Requests", "1")
            //the same user agent as the driver is needed.
            con.addRequestProperty("User-Agent", UserAgents.random)
            con.connectTimeout = Defaults.TIMEOUT.int() * 1000
            con.readTimeout = Defaults.TIMEOUT.int() * 1000
            con.inputStream.bufferedReader().readLines().forEach {
                println(it)
            }
        }
    }

    fun start() {
        if (!canStart()) {
            return
        }
        softStart()
        val url = Core.currentUrl
        Defaults.LAST_DOWNLOAD.update(url)
        taskScope.launch {
            val downloadHandler = DownloadHandler()
            val updateResult = downloadHandler.extractDataFromUrl(url)
            if (updateResult.isSuccess) {
                Defaults.LAST_DOWNLOAD.update("")
                downloadHandler.launch()
            } else {
                downloadHandler.kill()
                withContext(Dispatchers.Main) {
                    stop()
                    val error = updateResult.message!!
                    if (error.contains("unknown error: cannot find")
                        || error.contains("Unable to find driver executable")
                        || error.contains("unable to find binary")
                    ) {
                        showError(
                            """
                               Failed to read episodes from $url"
                               Error: Failed to find Chrome on your PC.
                               Make sure Chrome is installed. If this problem persists, there might be permission issues with your folders.
                            """.trimIndent()
                        )
                    } else {
                        showError(
                            "Failed to read episodes from $url", error
                        )
                    }
                }
            }
        }
    }

    fun softStart() {
        currentEpisodes.clear()
        Core.startButtonEnabled.value = false
        Core.stopButtonEnabled.value = true
        isRunning = true
        downloadsFinishedForSession = 0
        synchronizeDownloadsInProgress()
    }

    fun stop() {
        if (!isRunning) {
            return
        }
        Core.startButtonEnabled.value = true
        Core.stopButtonEnabled.value = false
        isRunning = false
        currentEpisodes.clear()
        synchronizeDownloadsInProgress()
    }

    fun shutdown(force: Boolean = false) {
        if (force && !shutdownExecuted) {
            shutdownExecuted = true
            stop()
            runningDrivers.forEach {
                it.close()
                it.quit()
            }
            exitProcess(-1)
        }
        if (isRunning) {
            DialogHelper.showConfirm(
                """
                    You are currently downloading videos.
                    Shutting down will stop all downloads and possibly corrupt any incomplete video.
                    It's advised to press the Stop button beforehand.
                    Do you wish to continue?
                """.trimIndent(),
                "Shutdown"
            ) {
                shutdownExecuted = true
                stop()
                runningDrivers.forEach {
                    it.close()
                    it.quit()
                }
                exitProcess(0)
            }
        } else {
            shutdownExecuted = true
            runningDrivers.forEach {
                it.close()
                it.quit()
            }
            exitProcess(0)
        }
    }

    private fun canStart(): Boolean {
        if (isUpdating) {
            showError("You can't download videos while the app is updating.")
            return false
        }
        if (isRunning) {
            showError("Failed to start the downloader because it's already running.")
            return false
        }
        val downloadFolder = File(Defaults.SAVE_FOLDER.string())
        if (!downloadFolder.exists()) {
            showError(
                """
                    The download folder in your settings doesn't exist. 
                    You must set it up before downloading videos.
                """.trimIndent()
            )
            Core.openSettings()
            return false
        }
        try {
            if (!downloadFolder.canWrite()) {
                showError(
                    """
                       The download folder in your settings doesn't allow write permissions.
                       If this is a USB or SD Card then disable write protection.
                       You can try selecting a folder in the user or home folder. Those are usually not restricted.
                    """.trimIndent()
                )
                Core.openSettings()
                return false
            }
        } catch (e: Exception) {
            showError("Failed to check for write permissions.", e)
            return false
        }
        if (!Defaults.BYPASS_DISK_SPACE.boolean()) {
            val root = downloadFolder.toPath().root
            val usableSpace = root.toFile().usableSpace
            if (usableSpace != 0L) {
                if (Tools.bytesToMB(usableSpace) < Constants.minSpaceNeeded) {
                    showError(
                        """
                            The download folder in your settings requires at least ${Constants.minSpaceNeeded}MB of free space.
                            Most videos average around ${Constants.averageVideoSize}MB so the requirement is just to be safe.
                            If you are having issues with this, open the settings and enable Bypass Disk Space Check.
                        """.trimIndent()
                    )
                    Core.openSettings()
                    return false
                }
            } else {
                val freeSpace = root.toFile().freeSpace
                if (freeSpace != 0L) {
                    if (Tools.bytesToMB(freeSpace) < Constants.minSpaceNeeded) {
                        showError(
                            """
                                The download folder in your settings requires at least ${Constants.minSpaceNeeded}MB of free space.
                                Most videos average around ${Constants.averageVideoSize}MB so the requirement is just to be safe.
                                If you are having issues with this, open the settings and enable Bypass Disk Space Check.
                            """.trimIndent()
                        )
                        Core.openSettings()
                        return false
                    }
                } else {
                    FrogLog.writeMessage("[WARNING] Failed to check for free space. Make sure you have enough space to download videos. (150MB+)")
                }
            }
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
                        
                """.trimIndent(),
                size = DpSize(300.dp, 400.dp)
            )
            return false
        }
        try {
            URI(url).toURL()
        } catch (_: Exception) {
            showError("This is not a valid URL.")
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
        return true
    }

    private fun synchronizeDownloadsInProgress() {
        downloadsInProgress.value = currentEpisodes.size
    }

    @Synchronized
    fun incrementDownloadsFinished() {
        downloadsFinishedForSession++
    }

    @Synchronized
    fun decrementDownloadsInProgress() {
        downloadsInProgress.value--
    }

    private fun isEpisodeInQueue(episode: Episode): Boolean {
        for (e in currentEpisodes) {
            if (e.matches(episode)) {
                return true
            }
        }
        return false
    }

    fun addEpisodeToQueue(episode: Episode): Boolean {
        if (!isEpisodeInQueue(episode)) {
            currentEpisodes.add(episode)
            synchronizeDownloadsInProgress()
            return true
        }
        return false
    }

    fun addEpisodesToQueue(episodesToAdd: List<Episode>): Int {
        var added = 0
        for (episode in episodesToAdd) {
            if (!isEpisodeInQueue(episode)) {
                currentEpisodes.add(episode)
                added++
            }
        }
        synchronizeDownloadsInProgress()
        return added
    }

    @get:Synchronized
    val nextEpisode: Episode?
        get() {
            if (currentEpisodes.isEmpty()) {
                return null
            }
            val link = currentEpisodes.first()
            currentEpisodes.removeAt(0)
            synchronizeDownloadsInProgress()
            return link
        }

    fun updateDownloadInDatabase(download: Download, updateProperties: Boolean) {
        BoxHelper.shared.downloadBox.put(download)
        val index = indexForDownload(download)
        if (index != -1) {
            downloadList[index].update(download, updateProperties)
        }
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
        }
    }

    fun removeDownload(download: Download) {
        downloadList.remove(download)
        BoxHelper.shared.downloadBox.remove(download)
    }

    fun updateDownloadProgress(
        download: Download,
        remainingSeconds: Int = -1
    ) {
        val index = indexForDownload(download)
        if (index != -1) {
            downloadList[index].updateProgress()
            if (remainingSeconds > -1) {
                downloadList[index].remainingDownloadSeconds.value = remainingSeconds
            }
        }
    }

    fun refreshDownloadsProgress() {
        downloadList.forEach {
            it.updateProgress()
        }
    }
}