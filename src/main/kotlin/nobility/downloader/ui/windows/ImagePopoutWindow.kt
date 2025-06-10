package nobility.downloader.ui.windows

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.dp
import nobility.downloader.ui.components.DefaultImage
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.fileExists
import nobility.downloader.utils.isUp

object ImagePopoutWindow {

    fun open(
        imagePath: String,
        urlBackup: String? = null
    ) {
        val title = if (urlBackup != null)
            if (imagePath.fileExists())
                imagePath else urlBackup
        else imagePath
        ApplicationState.newWindow(
            title,
            keyEvents = { focused, e ->
                if (focused && e.isUp && e.key == Key.Escape) {
                    ApplicationState.removeWindowWithId(title)
                    true
                }
                false
            }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
            ) {
                DefaultImage(
                    imagePath,
                    urlBackup,
                    pointerIcon = null
                ) {}
            }
        }
    }

}