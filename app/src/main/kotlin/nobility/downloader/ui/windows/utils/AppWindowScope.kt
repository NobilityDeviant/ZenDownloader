package nobility.downloader.ui.windows.utils

import androidx.compose.runtime.MutableState
import androidx.compose.ui.awt.ComposeWindow

interface AppWindowScope {
    val windowId: String
    var toastContent: MutableState<String>
    var open: MutableState<Boolean>
    var focused: MutableState<Boolean>
    var composeWindow: ComposeWindow?


    /**
     * When a window is closed programmatically, it doesn't actually clean anything.
     * The open variable and this are needed to help.
     */
    var onClose: (() -> Boolean)?
    fun closeWindow(
        triggerOnClose: Boolean = true
    )
    fun showToast(content: String) {
        toastContent.value = content
    }
}