
import androidx.compose.runtime.key
import androidx.compose.ui.window.application
import nobility.downloader.core.Core
import nobility.downloader.ui.windows.AssetUpdateWindow
import nobility.downloader.ui.windows.MainWindow
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.KeyEvents


fun main(args: Array<String>) {

    val windowFlag = args.firstOrNull()

    @Suppress("KotlinConstantConditions")
    if (AppInfo.UPDATE_ASSETS_ON_LAUNCH) {
        val assetUpdateWindow = AssetUpdateWindow()
        assetUpdateWindow.open {
            //initialize the core singleton before anything besides the updater.
            //this is the root of the app.
            Core.initialize(windowFlag == "full_windows")
            /**
             * The main window is needed to keep the application running.
             */
            ApplicationState.newWindow(
                AppInfo.TITLE,
                KeyEvents.shared.loadKeyEvents(),
                maximized = true,
                onClose = {
                    Core.child.shutdown()
                    false
                }
            ) {
                MainWindow(this)
            }
            true
        }
    } else {
        Core.initialize(windowFlag == "full_windows")
        ApplicationState.newWindow(
            AppInfo.TITLE,
            KeyEvents.shared.loadKeyEvents(),
            maximized = true,
            onClose = {
                Core.child.shutdown()
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