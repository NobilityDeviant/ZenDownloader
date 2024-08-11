
import androidx.compose.runtime.key
import androidx.compose.ui.window.application
import nobility.downloader.core.Core
import nobility.downloader.ui.windows.mainWindow
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.AppInfo
import nobility.downloader.utils.loadKeyEvents

fun main() {
    //initialize the core singleton before anything.
    //this is the root of the app.
    Core.initialize()
    /**
     * The main window is needed to keep the application running.
     * This function is strictly non-composable.
     */
    ApplicationState.newWindow(
        AppInfo.TITLE,
        loadKeyEvents(),
        onClose = {
            Core.child.shutdown(false)
            false
        }
    ) {
        mainWindow(this@newWindow)
    }
    application {
        for (window in ApplicationState.shared.windows) {
            key(window) {
                window.content()
            }
        }
    }
}