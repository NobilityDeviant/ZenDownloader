package nobility.downloader.ui.views

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp
import compose.icons.EvaIcons
import compose.icons.evaicons.Outline
import compose.icons.evaicons.outline.Close
import compose.icons.evaicons.outline.DiagonalArrowRightUp
import nobility.downloader.core.Core
import nobility.downloader.ui.components.defaultButton
import nobility.downloader.ui.components.defaultTextField
import nobility.downloader.ui.components.tooltipIconButton
import nobility.downloader.ui.windows.ConsoleWindow
import nobility.downloader.utils.Tools

@Composable
fun downloaderUi() {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.align(Alignment.TopCenter)
                .padding(top = 15.dp)
        ) {
            defaultTextField(
                Core.currentUrl,
                onValueChanged = {
                    Core.currentUrl = it
                },
                hint = Core.currentUrlHint,
                singleLine = true,
                modifier = Modifier.align(Alignment.CenterHorizontally)
                    .fillMaxWidth(0.9f).onKeyEvent {
                        if (it.key == Key.Enter) {
                            Core.child.start()
                            return@onKeyEvent true
                        }
                        return@onKeyEvent false
                    },
                contextMenuItems = {
                    val items = mutableListOf<ContextMenuItem>()
                    if (Tools.clipboardString.isNotEmpty()) {
                        items.add(ContextMenuItem("Paste & Start") {
                            Core.currentUrl = Tools.clipboardString
                            Core.child.start()
                        })
                    }
                    if (Core.currentUrl.isNotEmpty()) {
                        items.add(ContextMenuItem("Clear") {
                            Core.currentUrl = ""
                        })
                    }
                    items
                }
            )
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    val buttonWidth = 150.dp
                    val buttonHeight = 50.dp
                    defaultButton(
                        "Start",
                        modifier = Modifier.defaultMinSize(
                            minWidth = buttonWidth,
                            minHeight = buttonHeight
                        ),
                        enabled = Core.startButtonEnabled
                    ) {
                        Core.child.start()
                    }
                    defaultButton(
                        "Stop",
                        modifier = Modifier.defaultMinSize(
                            minWidth = buttonWidth,
                            minHeight = buttonHeight
                        ),
                        enabled = Core.stopButtonEnabled
                    ) {
                        Core.child.stop()
                    }
                }
                tooltipIconButton(
                    if (Core.consolePoppedOut)
                        "Close Console Window" else "Pop out Console",
                    if (Core.consolePoppedOut)
                        EvaIcons.Outline.Close else EvaIcons.Outline.DiagonalArrowRightUp,
                    iconSize = 30.dp,
                    iconColor = MaterialTheme.colorScheme.primary,
                    tooltipModifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    if (Core.consolePoppedOut) {
                        ConsoleWindow.close()
                    } else {
                        ConsoleWindow.open()
                    }
                }
            }
            if (!Core.consolePoppedOut) {
                Core.console.textField()
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .height(200.dp)
                        .padding(start = 5.dp, end = 5.dp, bottom = 5.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {}
            }
        }
    }
}