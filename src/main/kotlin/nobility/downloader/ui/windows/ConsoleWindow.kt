package nobility.downloader.ui.windows

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.Core
import nobility.downloader.core.settings.Defaults
import nobility.downloader.ui.windows.utils.ApplicationState

object ConsoleWindow {

    private const val TITLE = "Console"

    fun close() {
        ApplicationState.removeWindowWithId(TITLE)
    }

    fun open() {
        Core.consolePoppedOut = true
        ApplicationState.newWindow(
            TITLE,
            size = DpSize(400.dp, 250.dp),
            windowAlignment = Alignment.BottomEnd,
            alwaysOnTop = Defaults.CONSOLE_ON_TOP.boolean(),
            onClose = {
                Core.consolePoppedOut = false
                return@newWindow true
            }
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.background(
                    MaterialTheme.colorScheme.surface
                )
            ) {
                Core.console.textField(true)
            }
        }
    }

}