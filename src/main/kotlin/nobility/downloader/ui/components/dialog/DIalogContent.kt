package nobility.downloader.ui.components.dialog

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nobility.downloader.ui.components.linkifyText

@Composable
fun dialogHeader(
    dialogTitle: MutableState<String>,
    dialogContent: MutableState<String>,
    supportLinks: Boolean = true
) {
    val title by remember { dialogTitle }
    val content by remember { dialogContent }
    if (title.isNotEmpty()) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(10.dp)
                .heightIn(max = 100.dp)
                .widthIn(125.dp, 450.dp)
        )
    }
    if (content.isNotEmpty()) {
        Surface(
            //default dimensions are needed because the buttons
            //below the texts always come out weird.
            //this ensures they usually look right.
            modifier = Modifier.heightIn(50.dp, 150.dp)
                .widthIn(125.dp, 450.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                val scrollState = rememberScrollState()
                val scope = rememberCoroutineScope()
                if (supportLinks) {
                    linkifyText(
                        text = content,
                        style = MaterialTheme.typography.bodyMedium,
                        textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center,
                        linkColor = MaterialTheme.colorScheme.onSurface,
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
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
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
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(top = 3.dp, bottom = 3.dp, end = 1.dp).shadow(0.dp),
                    style = ScrollbarStyle(
                        minimalHeight = 16.dp,
                        thickness = 10.dp,
                        shape = RoundedCornerShape(10.dp),
                        hoverDurationMillis = 300,
                        unhoverColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f),
                        hoverColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.90f)
                    ),
                    adapter = rememberScrollbarAdapter(
                        scrollState = scrollState
                    )
                )
            }
        }
    }
}