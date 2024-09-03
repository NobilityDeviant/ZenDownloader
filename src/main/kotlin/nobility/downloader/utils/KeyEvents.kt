package nobility.downloader.utils

import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import nobility.downloader.Page
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.Core
import nobility.downloader.core.settings.Defaults
import nobility.downloader.ui.components.dialog.DialogHelper

fun loadKeyEvents(): (KeyEvent) -> Boolean = {
    val up = it.type == KeyEventType.KeyUp
    if (it.isCtrlPressed && it.key == Key.D && up) {
        Core.currentPage = Page.DOWNLOADS
        true
    } else if (it.isCtrlPressed && it.key == Key.S && up) {
        Core.openSettings()
        true
    } else if (it.isCtrlPressed && it.key == Key.O && up) {
        Tools.openFile(
            Defaults.SAVE_FOLDER.string()
        )
        true
    } else if (it.isCtrlPressed && it.key == Key.R && up) {
        Core.openRecents()
        true
    } else if (it.isCtrlPressed && it.key == Key.W && up) {
        Core.openWco()
        true
    } else if (it.isCtrlPressed && it.key == Key.H && up) {
        Core.openHistory()
        true
    } else if (it.key == Key.Escape && up) {
        if (Core.currentPage == Page.SETTINGS) {
            if (Core.settings.settingsChanged()) {
                DialogHelper.showConfirm(
                    "You have unsaved settings. Would you like to save them?",
                    "Save Settings",
                    size = DpSize(300.dp, 200.dp),
                    onConfirmTitle = "Save",
                    onDeny = {
                        Core.currentPage = Page.HOME
                    }
                ) {
                    if (Core.settings.saveSettings()) {
                        Core.currentPage = Page.HOME
                    }
                }
            } else {
                Core.currentPage = Page.HOME
            }
        } else {
            if (Core.currentPage != Page.HOME) {
                Core.currentPage = Page.HOME
            } else {
                Core.currentPage = Page.SETTINGS
            }
        }
        true
    } else {
        false
    }
}

val keyGuide: String get() = """
    These are all the key combinations for this program.  
    
    Anything with CTRL + means you must be holding down the Control Key.
    
    ESC = Home Page/Settings Page
    CTRL + D = Downloads Page
    CTRL + S = Settings Page
    CTRL + W = Database Window
    CTRL + H = History Window
    CTRL + O = Open Download Folder
    CTRL + R = Wcofun Recents Window
    
""".trimIndent()