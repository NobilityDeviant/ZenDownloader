package nobility.downloader.ui.components.dialog

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nobility.downloader.ui.components.LinkifyText

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
            shape = RoundedCornerShape(10.dp),
            //default dimensions are needed because the buttons
            //below the texts always come out weird.
            //this ensures they usually look right.
            modifier = Modifier.heightIn(50.dp, 150.dp)
                .widthIn(125.dp, 450.dp)
        ) {
            val scrollState = rememberScrollState()
            val scope = rememberCoroutineScope()
            if (supportLinks) {
                LinkifyText(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    linkColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(10.dp).verticalScroll(scrollState)
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
                    modifier = Modifier.padding(10.dp).verticalScroll(scrollState)
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
        }
    }
}