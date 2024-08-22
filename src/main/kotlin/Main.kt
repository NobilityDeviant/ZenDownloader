
import androidx.compose.runtime.key
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.application
import nobility.downloader.core.Core
import nobility.downloader.ui.views.AssetUpdateView
import nobility.downloader.ui.windows.mainWindow
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.AppInfo
import nobility.downloader.utils.loadKeyEvents

fun main() {
    ApplicationState.newWindow(
        "Asset Updater",
        size = DpSize(450.dp, 250.dp),
        transparent = true,
        undecorated = true,
        onClose = {
            //initialize the core singleton before anything besides the updater.
            //this is the root of the app.
            Core.initialize()
            /**
             * The main window is needed to keep the application running.
             */
            ApplicationState.newWindow(
                AppInfo.TITLE,
                loadKeyEvents(),
                maximized = true,
                onClose = {
                    Core.child.shutdown(false)
                    false
                }
            ) {
                mainWindow(this)
            }
            true
        }
    ) {
        val assetUpdateView = AssetUpdateView()
        assetUpdateView.assetUpdaterUi(this)
    }

    application {
        for (window in ApplicationState.shared.windows) {
            key(window) {
                window.content()
            }
        }
    }
}