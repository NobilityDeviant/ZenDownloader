package nobility.downloader.ui.components.dialog

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nobility.downloader.ui.components.linkifyText
import nobility.downloader.utils.tone

@Composable
fun dialogHeader(
    dialogTitle: String,
    dialogContent: String,
    supportLinks: Boolean = true,
    titleColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val title by remember { mutableStateOf(dialogTitle) }
    val content by remember { mutableStateOf(dialogContent) }
    if (title.isNotEmpty()) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = titleColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(10.dp).fillMaxWidth()
        )
    }
    if (content.isNotEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            val scrollState = rememberScrollState()
            val scope = rememberCoroutineScope()
            if (supportLinks) {
                linkifyText(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    textColor = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    linkColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(
                        top = 10.dp,
                        bottom = 10.dp,
                        start = 10.dp,
                        end = 13.dp
                    ).verticalScroll(scrollState)
                        .draggable(
                            state = rememberDraggableState {
                                scope.launch {
                                    scrollState.scrollBy(-it)
                                }
                            },
                            orientation = Orientation.Vertical,
                        ).fillMaxSize()
                )
            } else {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(
                        top = 10.dp,
                        bottom = 10.dp,
                        start = 10.dp,
                        end = 13.dp
                    ).verticalScroll(scrollState)
                        .draggable(
                            state = rememberDraggableState {
                                scope.launch {
                                    scrollState.scrollBy(-it)
                                }
                            },
                            orientation = Orientation.Vertical,
                        ).fillMaxSize()
                )
            }
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd)
                    .background(MaterialTheme.colorScheme.surface.tone(20.0))
                    .fillMaxHeight()
                    .padding(top = 3.dp, bottom = 3.dp, end = 1.dp).shadow(0.dp),
                style = ScrollbarStyle(
                    minimalHeight = 16.dp,
                    thickness = 10.dp,
                    shape = RoundedCornerShape(10.dp),
                    hoverDurationMillis = 300,
                    unhoverColor = MaterialTheme.colorScheme.surface.tone(50.0).copy(alpha = 0.70f),
                    hoverColor = MaterialTheme.colorScheme.surface.tone(50.0).copy(alpha = 0.90f)
                ),
                adapter = rememberScrollbarAdapter(
                    scrollState = scrollState
                )
            )
        }
    }
}

@Composable
fun dialogWrapper(
    title: String = "",
    message: String = "",
    supportLinks: Boolean = true,
    themeColor: Color = MaterialTheme.colorScheme.primary,
    bottomBarContent: @Composable () -> Unit = {}
) {
    Scaffold(
        modifier = Modifier.fillMaxSize()
            .border(
                1.dp,
                color = themeColor,
                shape = RoundedCornerShape(10.dp)
            ).background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp)
            ),
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(10.dp)
                ) {
                    bottomBarContent()
                }
            }
        }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp)
                )
        ) {
            dialogHeader(
                title,
                message,
                supportLinks,
                themeColor
            )
        }
    }
}