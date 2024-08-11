package nobility.downloader.ui.components.dialog.dialogs

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogState
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import nobility.downloader.ui.components.defaultButton
import nobility.downloader.ui.components.dialog.DialogSetup
import nobility.downloader.ui.components.dialog.dialogHeader

class MessageDialog : DialogSetup() {

    @Composable
    override fun content() {
        if (dialogOpen) {
            DialogWindow(
                title = dialogTitle.value,
                state = DialogState(
                    position = WindowPosition.Aligned(Alignment.Center),
                    size = size
                ),
                undecorated = true,
                transparent = true,
                resizable = false,
                onKeyEvent = {
                    if (it.key == Key.Enter) {
                        hide()
                        return@DialogWindow true
                    }
                    false
                },
                onCloseRequest = { hide() },
                content = {
                    Card(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(
                                1.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                shape = RoundedCornerShape(10.dp)
                            ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 10.dp
                        )
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            dialogHeader(
                                dialogTitle,
                                dialogContent
                            )
                            defaultButton(
                                "Close",
                                width = 120.dp,
                                height = 40.dp,
                                padding = 10.dp
                            ) {
                                hide()
                            }
                        }
                    }
                })
        }
    }
}