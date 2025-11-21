
import Flags.Companion.toArg
import androidx.compose.runtime.key
import androidx.compose.ui.window.application
import nobility.downloader.core.Core
import nobility.downloader.ui.windows.AssetUpdateWindow
import nobility.downloader.ui.windows.MainWindow
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.KeyEvents

/**
 * The application flags. Use the lowercase name after the jar path split by spaces.
 * Launching from the bootstrapper also passes these arguments.
 * IE: java -jar ./ZenDownloader.jar -full_windows -other_command
 */
enum class Flags {
    FULL_WINDOWS, //makes all windows undecorated
    SKIP_ASSET_UPDATES
    ;

    companion object {
        val Flags.toArg: String get() {
            return name.lowercase()
        }
    }
}


fun main(args: Array<String>) {

    val windowFlag = args.contains(Flags.FULL_WINDOWS.toArg)
    val skipAssetUpdates = args.contains(Flags.SKIP_ASSET_UPDATES.toArg)

    @Suppress("KotlinConstantConditions", "SimplifyBooleanWithConstants")
    if (!skipAssetUpdates && AppInfo.UPDATE_ASSETS_ON_LAUNCH) {
        val assetUpdateWindow = AssetUpdateWindow()
        assetUpdateWindow.open {
            //initialize the core singleton before anything besides the updater.
            //this is the root of the app.
            Core.initialize(windowFlag)

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
        Core.initialize(windowFlag)
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