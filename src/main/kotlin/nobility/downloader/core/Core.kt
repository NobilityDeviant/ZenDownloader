package nobility.downloader.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import nobility.downloader.Page
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.BoxHelper.Companion.update
import nobility.downloader.core.entities.Series
import nobility.downloader.core.scraper.data.ToDownload
import nobility.downloader.core.settings.Defaults
import nobility.downloader.ui.components.Console
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.ui.views.*
import nobility.downloader.ui.windows.DownloadConfirmWindow
import nobility.downloader.ui.windows.ImageUpdaterWindow
import nobility.downloader.ui.windows.UpdateWindow
import nobility.downloader.ui.windows.database.DatabaseWindow
import nobility.downloader.utils.AppInfo
import nobility.downloader.utils.FrogLog
import nobility.downloader.utils.fileExists
import java.io.PrintStream

/**
 * A class used to represent the global singleton for the application.
 * Any class can access this class without needing to pass it as a parameter.
 */
class Core {

    companion object {

        private var initialized = false
        val child = CoreChild()
        val console = Console()
        val errorConsole = Console(true)

        lateinit var settingsView: SettingsView
        lateinit var downloaderView: DownloaderView
        lateinit var downloadsView: DownloadsView
        lateinit var historyView: HistoryView
        lateinit var recentView: RecentView
        lateinit var errorConsoleView: ErrorConsoleView

        var currentPage by mutableStateOf(Page.DOWNLOADER)
            private set
        var lastPage = Page.DOWNLOADER
        var currentUrl by mutableStateOf("")
        var currentUrlHint by mutableStateOf("")
        var startButtonEnabled by mutableStateOf(true)
        var stopButtonEnabled by mutableStateOf(false)
        var currentUrlFocused = false
        var darkMode = mutableStateOf(false)
        val randomSeries = mutableStateListOf<Series>()
        val randomSeries2 = mutableStateListOf<Series>()
        var errorPrintStream: PrintStream? = null
        val exampleEpisode: String get() = "$wcoUrl${AppInfo.EXAMPLE_EPISODE}"
        val exampleSeries: String get() = "$wcoUrl${AppInfo.EXAMPLE_SERIES}"

        fun initialize() {
            if (initialized) {
                return
            }
            @Suppress("KotlinConstantConditions")
            if (!AppInfo.DEBUG_MODE) {
                System.setOut(PrintStream(console))
                if (AppInfo.USE_CUSTOM_ERROR_PS) {
                    errorPrintStream = PrintStream(errorConsole)
                } else {
                    System.setErr(PrintStream(errorConsole))
                }
            }
            if (!Defaults.FIRST_LAUNCH.boolean()) {
                FrogLog.writeMessage("Welcome to ${AppInfo.TITLE}!")
                Defaults.FIRST_LAUNCH.update(true)
            } else {
                FrogLog.writeMessage("Welcome Back!")
            }
            BoxHelper.init()
            child.init()
            downloaderView = DownloaderView()
            downloadsView = DownloadsView()
            settingsView = SettingsView()
            historyView = HistoryView()
            recentView = RecentView()
            errorConsoleView = ErrorConsoleView()
            currentUrl = Defaults.LAST_DOWNLOAD.string()
            currentUrlHint = exampleSeries
            darkMode.value = Defaults.DARK_MODE.boolean()
            if (Defaults.ENABLE_RANDOM_SERIES.boolean()) {
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
            }
            initialized = true
            openUpdate(true)
        }

        fun changePage(page: Page) {
            if (currentPage == Page.SETTINGS) {
                if (settingsView.settingsChanged()) {
                    DialogHelper.showConfirm(
                        "You have unsaved settings. Would you like to save them?",
                        "Save Settings",
                        size = DpSize(300.dp, 200.dp),
                        onConfirmTitle = "Save",
                        onDeny = {
                            changePageFunction(page)
                        }
                    ) {
                        if (settingsView.saveSettings()) {
                            changePageFunction(page)
                        }
                    }
                } else {
                    changePageFunction(page)
                }
            } else {
                changePageFunction(page)
            }
        }

        private fun changePageFunction(page: Page) {
            if (currentPage == page) {
                return
            }

            //before close
            when (currentPage) {
                Page.DOWNLOADER -> {
                    downloaderView.onClose()
                }
                Page.DOWNLOADS -> {
                    downloadsView.onClose()
                }
                Page.SETTINGS -> {
                    settingsView.onClose()
                }
                Page.HISTORY -> {
                    historyView.onClose()
                }
                Page.RECENT -> {
                    recentView.onClose()
                }
                Page.ERROR_CONSOLE -> {
                    errorConsoleView.onClose()
                }
            }
            //before open
            when (page) {
                Page.SETTINGS -> {
                    settingsView.updateValues()
                }
                Page.ERROR_CONSOLE -> {
                    errorConsole.unreadErrors = false
                }
                else -> {}
            }
            currentPage = page
            if (page != Page.SETTINGS) {
                lastPage = page
            }
            //after open
            when (page) {
                Page.ERROR_CONSOLE -> {
                    errorConsole.unreadErrors = false
                }
                else -> {}
            }
        }

        fun reloadRandomSeries() {
            if (Defaults.ENABLE_RANDOM_SERIES.boolean()) {
                randomSeries.clear()
                randomSeries2.clear()
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
            } else {
                randomSeries.clear()
                randomSeries2.clear()
            }
        }

        fun openWco(
            initialSearch: String = ""
        ) {
            val databaseWindow = DatabaseWindow()
            databaseWindow.open(initialSearch)
        }

        fun openImageUpdater() {
            val imageUpdaterWindow = ImageUpdaterWindow()
            imageUpdaterWindow.open()
        }

        fun openUpdate(
            justCheck: Boolean = false
        ) {
            UpdateWindow(justCheck)
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
    }

}