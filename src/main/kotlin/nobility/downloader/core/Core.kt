package nobility.downloader.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import nobility.downloader.Page
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.BoxHelper.Companion.update
import nobility.downloader.core.entities.Series
import nobility.downloader.core.scraper.data.ToDownload
import nobility.downloader.core.settings.Defaults
import nobility.downloader.ui.components.Console
import nobility.downloader.ui.views.DownloadsView
import nobility.downloader.ui.views.SettingsView
import nobility.downloader.ui.windows.DownloadConfirmWindow
import nobility.downloader.ui.windows.HistoryWindow
import nobility.downloader.ui.windows.RecentWindow
import nobility.downloader.ui.windows.UpdateWindow
import nobility.downloader.ui.windows.database.DatabaseWindow
import nobility.downloader.utils.AppInfo
import nobility.downloader.utils.FrogLog
import nobility.downloader.utils.fileExists
import java.io.PrintStream

/**
 * A class used to represent the global singleton for the application.
 * Any class can access this class without needing to pass it as a parameter(like the old model class).
 */
class Core {

    companion object {

        private var initialized = false
        val child = CoreChild()
        val console = Console()
        val errorConsole = Console(true)
        lateinit var settings: SettingsView
        lateinit var downloads: DownloadsView
        var currentPage by mutableStateOf(Page.HOME)
        var currentUrl by mutableStateOf("")
        var currentUrlHint by mutableStateOf("")
        var startButtonEnabled = mutableStateOf(true)
        var stopButtonEnabled = mutableStateOf(false)
        var currentUrlFocused = false
        var darkMode = mutableStateOf(false)
        var consolePoppedOut by mutableStateOf(false)
        val randomSeries = mutableStateListOf<Series>()
        val randomSeries2 = mutableStateListOf<Series>()

        val wcoUrl: String
            get() {
                return "https://" +
                        Defaults.WCO_DOMAIN.string() +
                        "." +
                        Defaults.WCO_EXTENSION.string() +
                        "/"
            }

        val wcoUrlWww: String
            get() {
                return "https://www." +
                        Defaults.WCO_DOMAIN.string() +
                        "." +
                        Defaults.WCO_EXTENSION.string() +
                        "/"
            }

        val exampleEpisode: String get() = "$wcoUrl${AppInfo.EXAMPLE_EPISODE}"
        val exampleSeries: String get() = "$wcoUrl${AppInfo.EXAMPLE_SERIES}"

        fun initialize() {
            if (initialized) {
                return
            }
            @Suppress("KotlinConstantConditions")
            if (!AppInfo.DEBUG_MODE) {
                System.setOut(PrintStream(console))
                System.setErr(PrintStream(errorConsole))
            }
            if (!Defaults.FIRST_LAUNCH.boolean()) {
                FrogLog.writeMessage("Welcome to ${AppInfo.TITLE}!")
                Defaults.FIRST_LAUNCH.update(true)
            } else {
                FrogLog.writeMessage("Welcome Back!")
            }
            BoxHelper.init()
            child.init()
            downloads = DownloadsView()
            settings = SettingsView()
            currentUrl = Defaults.LAST_DOWNLOAD.string()
            currentUrlHint = exampleSeries
            darkMode.value = Defaults.DARK_MODE.boolean()
            try {
                var tries = 0
                val series = BoxHelper.allSeriesNoMovies
                if (series.size >= 30) {
                    val randoms = mutableListOf<Series>()
                    while (randoms.size < 31) {
                        val random = series.random()
                        if (!randoms.contains(random) && random.imagePath.fileExists()) {
                            randoms.add(random)
                        }
                        if (tries >= 150) {
                            break
                        }
                        tries++
                    }
                    randomSeries.addAll(randoms.subList(0, 15))
                    randomSeries2.addAll(randoms.subList(15, 30))
                }
            } catch (_: Exception) {
            }
            initialized = true
            openUpdate(true)
        }

        fun openSettings() {
            settings.updateValues()
            currentPage = Page.SETTINGS
        }

        fun openWco(
            initialSearch: String = ""
        ) {
            val databaseWindow = DatabaseWindow()
            databaseWindow.open(initialSearch)
        }

        fun openUpdate(
            justCheck: Boolean = false
        ) {
            UpdateWindow(justCheck)
        }

        fun openRecents() {
            val recentWindow = RecentWindow()
            recentWindow.open()
        }

        fun openHistory() {
            val historyWindow = HistoryWindow()
            historyWindow.open()
        }

        fun openDownloadConfirm(
            toDownload: ToDownload
        ) {
            if (toDownload.series == null) {
                FrogLog.writeMessage(
                    "Failed to open download confirm window. Series isn't valid."
                )
                return
            }
            val downloadConfirm = DownloadConfirmWindow(toDownload)
            downloadConfirm.open()
        }

    }

}