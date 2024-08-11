package nobility.downloader.ui.components.dialog.dialogs

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogState
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import nobility.downloader.ui.components.defaultButton
import nobility.downloader.ui.components.dialog.DialogSetup
import nobility.downloader.ui.components.dialog.dialogHeader

class ButtonDialog: DialogSetup() {

    var onConfirm: () -> Unit = {}
    var onDeny: () -> Unit = {}
    var confirmTitle by mutableStateOf("Confirm")
    var denyTitle by mutableStateOf("Cancel")
    var supportLinks = true

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
                                dialogContent,
                                supportLinks
                            )
                            Row(
                                modifier = Modifier
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                defaultButton(
                                    denyTitle,
                                    width = 120.dp,
                                    height = 40.dp
                                ) {
                                    hide()
                                    onDeny()
                                }
                                defaultButton(
                                    confirmTitle,
                                    width = 120.dp,
                                    height = 40.dp
                                ) {
                                    hide()
                                    onConfirm()
                                }
                            }
                        }
                    }
                })
        }
    }
}