package nobility.downloader.ui.components.dialog.dialogs

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogState
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import nobility.downloader.ui.components.defaultButton
import nobility.downloader.ui.components.dialog.DialogSetup
import nobility.downloader.ui.components.dialog.dialogHeader
import nobility.downloader.utils.Option

class OptionsDialog : DialogSetup() {

    var options = mutableListOf<Option>()
    var supportLinks = true

    @Composable
    override fun content() {
        if (dialogOpen) {
            DialogWindow(
                onCloseRequest = {
                    hide()
                },
                state = DialogState(
                    position = WindowPosition.Aligned(Alignment.Center),
                    size = size
                ),
                undecorated = true,
                transparent = true,
                resizable = false,
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
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(4.dp)
                            ) {
                                options.forEach {
                                    defaultButton(
                                        it.title,
                                        width = 100.dp,
                                        height = 35.dp
                                    ) {
                                        hide()
                                        it.func()
                                    }
                                }
                            }
                        }
                    }
                })
        }
    }
}