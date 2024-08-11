package nobility.downloader.ui.components.dialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize

open class DialogSetup {

    var dialogOpen by mutableStateOf(false)
    var dialogTitle = mutableStateOf("")
    var dialogContent = mutableStateOf("")
    var size = DpSize.Unspecified

    fun updateMessage(
        title: String = "",
        message: String = "",
        size: DpSize = DpSize.Unspecified,
        clear: Boolean = false
    ) {
        this.size = size
        if (clear) {
            dialogTitle.value = ""
            dialogContent.value = ""
        } else {
            if (title.isNotEmpty()) {
                dialogTitle.value = title
            }
            if (message.isNotEmpty()) {
                dialogContent.value = message
            }
        }
    }

    @Composable
    open fun content() {}

    fun hide() {
        dialogOpen = false
    }

    fun show() {
        dialogOpen = true
    }

}