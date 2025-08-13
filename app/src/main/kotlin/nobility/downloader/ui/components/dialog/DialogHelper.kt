package nobility.downloader.ui.components.dialog

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import nobility.downloader.ui.components.defaultButton
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.Option
import nobility.downloader.utils.Tools
import java.net.URI

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
            Tools.openInBrowser(URI(link))
        } catch (e: Exception) {
            showError("Failed to open the link: $link", e)
        }
    }

    fun showOptions(
        title: String = "",
        message: String = "",
        supportLinks: Boolean = true,
        size: DpSize = mediumWindowSize,
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
            DialogWrapper(
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
        size: DpSize = mediumWindowSize,
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
            keyEvents = { focused, e ->
                if (e.key == Key.Enter && e.type == KeyEventType.KeyUp) {
                    ApplicationState.removeWindowWithId(title)
                    onConfirm()
                }
                false
            },
            size = size
        ) {
            DialogWrapper(
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
        size: DpSize = mediumWindowSize
    ) {
        ApplicationState.newWindow(
            title,
            undecorated = true,
            resizable = false,
            transparent = true,
            alwaysOnTop = true,
            size = size
        ) {
            DialogWrapper(
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
        size: DpSize = mediumWindowSize
    ) {
        ApplicationState.newWindow(
            title,
            undecorated = true,
            resizable = false,
            transparent = true,
            alwaysOnTop = true,
            size = size
        ) {
            DialogWrapper(
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
        size: DpSize = mediumWindowSize
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
        size: DpSize = mediumWindowSize
    ) {
        showError(
            "Error",
            message,
            e,
            size
        )
    }

    val smallWindowSize = DpSize(300.dp, 200.dp)
    val mediumWindowSize = DpSize(400.dp, 300.dp)
}