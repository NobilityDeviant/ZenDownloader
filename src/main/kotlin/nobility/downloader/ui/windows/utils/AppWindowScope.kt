package nobility.downloader.ui.windows.utils

import androidx.compose.runtime.MutableState

interface AppWindowScope {
    val windowId: String
    var toastContent: MutableState<String>
    var open: MutableState<Boolean>

    /**
     * When a window is closed programatically, it doesn't actually clean anything.
     * The open variable and this are needed to help.
     */
    var onClose: (() -> Boolean)?
    fun closeWindow()
    fun showToast(content: String) {
        toastContent.value = content
    }
}