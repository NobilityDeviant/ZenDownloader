package nobility.downloader.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import nobility.downloader.Page
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.BoxHelper.Companion.update
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
        var darkMode = mutableStateOf(false)
        var consolePoppedOut by mutableStateOf(false)

        val wcoUrl: String
            get() {
                return "https://" +
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
            initialized = true
            System.setOut(PrintStream(console))
            System.setErr(PrintStream(errorConsole))
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
            openUpdate(true)
        }

        fun openSettings() {
            settings.updateValues()
            currentPage = Page.SETTINGS
        }

        fun openWco() {
            val databaseWindow = DatabaseWindow()
            databaseWindow.open()
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