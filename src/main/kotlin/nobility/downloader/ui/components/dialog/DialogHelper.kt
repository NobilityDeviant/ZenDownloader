package nobility.downloader.ui.components.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import nobility.downloader.core.Core
import nobility.downloader.ui.components.defaultButton
import nobility.downloader.ui.theme.CoreTheme
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.Option
import nobility.downloader.utils.Tools
import java.awt.Desktop
import java.net.URI
import java.util.*

object DialogHelper {

    fun showError(
        title: String,
        message: String?,
        e: Exception? = null,
        size: DpSize = DpSize.Unspecified
    ) {
        Core.messageDialog.updateMessage(
            title,
            message + if (e != null) "\nError: ${e.localizedMessage}" else "",
            size
        )
        Core.messageDialog.show()
    }

    fun showError(
        message: String,
        size: DpSize = DpSize.Unspecified
    ) {
        showError(
            "Error",
            message,
            size = size
        )
    }

    fun showError(
        message: String,
        e: Exception,
        size: DpSize = DpSize.Unspecified
    ) {
        showError(
            "Error",
            message,
            e,
            size
        )
    }

    fun showMessage(
        title: String = "",
        message: String,
        size: DpSize = DpSize.Unspecified
    ) {
        Core.messageDialog.updateMessage(title, message, size)
        Core.messageDialog.show()
    }

    fun showConfirm(
        message: String,
        size: DpSize = DpSize.Unspecified,
        supportLinks: Boolean = true,
        onDenyTitle: String = "",
        onConfirmTitle: String = "",
        onDeny: () -> Unit = {},
        onConfirm: () -> Unit
    ) {
        Core.confirmDialog.updateMessage(message = message, size = size)
        if (onConfirmTitle.isNotEmpty()) {
            Core.confirmDialog.confirmTitle = onConfirmTitle
        }
        if (onDenyTitle.isNotEmpty()) {
            Core.confirmDialog.denyTitle = onDenyTitle
        }
        Core.confirmDialog.onConfirm = onConfirm
        Core.confirmDialog.onDeny = onDeny
        Core.confirmDialog.supportLinks = supportLinks
        Core.confirmDialog.show()
    }

    fun showOptions(
        title: String = "",
        message: String = "",
        supportLinks: Boolean = true,
        vararg options: Option
    ) {
        Core.optionDialog.updateMessage(title, message)
        Core.optionDialog.options = options.toMutableList()
        Core.optionDialog.supportLinks = supportLinks
        Core.optionDialog.show()
    }

    fun showLinkPrompt(
        link: String,
        prompt: Boolean = true
    ) {
        showLinkPrompt(
            link,
            """
                Do you want to open:
                $link
                in your default browser?
            """.trimIndent(),
            prompt
        )
    }

    private fun showLinkPrompt(
        link: String,
        message: String,
        prompt: Boolean
    ) {
        if (link.isEmpty()) {
            return
        }
        if (prompt) {
            showConfirm(
                message,
                supportLinks = false
            ) { openLink(link) }
        } else {
            openLink(link)
        }
    }

    private fun openLink(link: String) {
        try {
            openInBrowser(URI(link))
        } catch (e: Exception) {
            showError("Failed to open the link: $link", e)
        }
    }

    //https://stackoverflow.com/a/68426773/8595744
    //i never even thought about the desktop not supporting everything
    private fun openInBrowser(uri: URI) {
        val osName by lazy(LazyThreadSafetyMode.NONE) { System.getProperty("os.name").lowercase(Locale.getDefault()) }
        val desktop = Desktop.getDesktop()
        when {
            Desktop.isDesktopSupported() && desktop.isSupported(Desktop.Action.BROWSE) -> desktop.browse(uri)
            "mac" in osName -> Runtime.getRuntime().exec("open $uri")
            "nix" in osName || "nux" in osName -> Runtime.getRuntime().exec("xdg-open $uri")
            else -> throw RuntimeException("Your operating system ($osName) isn't supported.")
        }
    }

    fun showCopyPrompt(
        textToCopy: String,
        message: String = "Do you want to copy this to your clipboard?",
        prompt: Boolean = true,
        appWindowScope: AppWindowScope? = null
    ) {
        if (prompt) {
            showConfirm(message) {
                copyToClipboard(textToCopy)
                appWindowScope?.showToast("Copied")
            }
        } else {
            copyToClipboard(textToCopy)
            appWindowScope?.showToast("Copied")
        }
    }

    private fun copyToClipboard(text: String) {
        Tools.clipboardString = text
    }

    //has potential. work on the colors and default size
    @Suppress("unused")
    fun showMessageExperimental(
        title: String,
        message: String
    ) {
        ApplicationState.newWindow(
            title,
            undecorated = true,
            resizable = false,
            transparent = true,
            alwaysOnTop = true,
            size = DpSize.Unspecified
        ) {
            CoreTheme {
                Column(
                    modifier = Modifier.background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(4.dp)
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        dialogHeader(
                            mutableStateOf(title),
                            mutableStateOf(message)
                        )
                        defaultButton(
                            "Close",
                            height = 40.dp,
                            width = 120.dp,
                            padding = 10.dp
                        ) {
                            closeWindow()
                        }
                    }
                }
            }
        }
    }

}