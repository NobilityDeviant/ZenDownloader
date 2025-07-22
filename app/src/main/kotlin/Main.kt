
import androidx.compose.runtime.key
import androidx.compose.ui.window.application
import nobility.downloader.core.Core
import nobility.downloader.ui.windows.AssetUpdateWindow
import nobility.downloader.ui.windows.MainWindow
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.KeyEvents


fun main() {
    @Suppress("KotlinConstantConditions")
    if (AppInfo.UPDATE_ASSETS_ON_LAUNCH) {
        val assetUpdateWindow = AssetUpdateWindow()
        assetUpdateWindow.open {
            //initialize the core singleton before anything besides the updater.
            //this is the root of the app.
            Core.initialize()
            /**
             * The main window is needed to keep the application running.
             */
            ApplicationState.newWindow(
                AppInfo.TITLE,
                KeyEvents.shared.loadKeyEvents(),
                maximized = true,
                onClose = {
                    Core.child.shutdown(false)
                    false
                }
            ) {
                MainWindow(this)
            }
            true
        }
    } else {
        Core.initialize()
        ApplicationState.newWindow(
            AppInfo.TITLE,
            KeyEvents.shared.loadKeyEvents(),
            maximized = true,
            onClose = {
                Core.child.shutdown(false)
                false
            }
        ) {
            MainWindow(this)
        }
    }

    application {
        for (window in ApplicationState.shared.windows) {
            key(window) {
                window.content()
            }
        }
    }
}