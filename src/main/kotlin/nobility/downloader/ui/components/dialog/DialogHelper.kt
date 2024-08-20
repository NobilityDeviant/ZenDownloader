package nobility.downloader.ui.components.dialog

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import nobility.downloader.ui.components.defaultButton
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.Option
import nobility.downloader.utils.Tools
import java.awt.Desktop
import java.net.URI
import java.util.*

object DialogHelper {

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

    fun showLinkPrompt(
        link: String,
        message: String,
        prompt: Boolean = true
    ) {
        if (link.isEmpty()) {
            return
        }
        if (prompt) {
            showConfirm(
                message,
                "Link Manager",
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

    @Suppress("UNUSED")
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

    fun showOptions(
        title: String = "",
        message: String = "",
        supportLinks: Boolean = true,
        size: DpSize = DpSize(400.dp, 300.dp),
        buttonWidth: Dp = 120.dp,
        options: List<Option>
    ) {
        ApplicationState.newWindow(
            title,
            undecorated = true,
            resizable = false,
            transparent = true,
            alwaysOnTop = true,
            size = size
        ) {
            dialogWrapper(
                title,
                message,
                supportLinks
            ) {
                options.forEach {
                    defaultButton(
                        it.title,
                        width = buttonWidth,
                        height = 35.dp
                    ) {
                        closeWindow()
                        it.func()
                    }
                }
            }
        }
    }

    fun showConfirm(
        message: String,
        title: String = "Confirm",
        size: DpSize = DpSize(400.dp, 300.dp),
        supportLinks: Boolean = true,
        onDenyTitle: String = "Cancel",
        onConfirmTitle: String = "Confirm",
        onDeny: () -> Unit = {},
        onConfirm: () -> Unit
    ) {
        ApplicationState.newWindow(
            title,
            undecorated = true,
            resizable = false,
            transparent = true,
            alwaysOnTop = true,
            size = size
        ) {
            dialogWrapper(
                title,
                message,
                supportLinks
            ) {
                defaultButton(
                    onDenyTitle,
                    width = 120.dp,
                    height = 40.dp
                ) {
                    closeWindow()
                    onDeny()
                }
                defaultButton(
                    onConfirmTitle,
                    width = 120.dp,
                    height = 40.dp
                ) {
                    closeWindow()
                    onConfirm()
                }
            }
        }
    }

    fun showMessage(
        title: String,
        message: String,
        size: DpSize = DpSize(400.dp, 300.dp)
    ) {
        ApplicationState.newWindow(
            title,
            undecorated = true,
            resizable = false,
            transparent = true,
            alwaysOnTop = true,
            size = size
        ) {
            dialogWrapper(
                title,
                message
            ) {
                defaultButton(
                    "Close",
                    height = 40.dp,
                    width = 120.dp
                ) {
                    closeWindow()
                }
            }
        }
    }

    fun showError(
        title: String,
        message: String?,
        e: Exception? = null,
        size: DpSize = DpSize(400.dp, 300.dp)
    ) {
        ApplicationState.newWindow(
            title,
            undecorated = true,
            resizable = false,
            transparent = true,
            alwaysOnTop = true,
            size = size
        ) {
            dialogWrapper(
                title,
                message + if (e != null) "\nError: ${e.localizedMessage}" else "",
                themeColor = MaterialTheme.colorScheme.error
            ) {
                defaultButton(
                    "Close",
                    height = 40.dp,
                    width = 120.dp,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    closeWindow()
                }
            }
        }
    }

    fun showError(
        message: String,
        size: DpSize = DpSize(400.dp, 300.dp)
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
        size: DpSize = DpSize(400.dp, 300.dp)
    ) {
        showError(
            "Error",
            message,
            e,
            size
        )
    }
}