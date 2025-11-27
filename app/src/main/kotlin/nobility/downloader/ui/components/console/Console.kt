package nobility.downloader.ui.components.console

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.Outline
import compose.icons.evaicons.fill.Copy
import compose.icons.evaicons.fill.Trash
import compose.icons.evaicons.outline.Close
import compose.icons.evaicons.outline.DiagonalArrowRightUp
import kotlinx.coroutines.launch
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.settings.Defaults
import nobility.downloader.ui.components.DefaultScrollbarStyle
import nobility.downloader.ui.components.FullBox
import nobility.downloader.ui.components.TooltipIconButton
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.Tools
import nobility.downloader.utils.hover
import nobility.downloader.utils.tone
import java.io.OutputStream

class Console(
    private val errorMode: Boolean = false
) : OutputStream() {

    val state = ConsoleState(errorMode)

    override fun write(b: Int) {}

    override fun write(b: ByteArray, off: Int, len: Int) {
        val text = String(b, off, len, Charsets.UTF_8)
        text.forEach {
            state.appendChar(it)
        }
    }

    override fun close() {
        state.clear()
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun ConsoleTextField(
        windowScope: AppWindowScope,
        modifier: Modifier = Modifier,
        popoutMode: Boolean = false,
    ) {

        val scrollState = rememberScrollState(state.lineCount)
        val scope = rememberCoroutineScope()
        val contextMenuRepresentation = DefaultContextMenuRepresentation(
            backgroundColor = if (isSystemInDarkTheme()) Color.DarkGray else Color.LightGray,
            textColor = if (isSystemInDarkTheme()) Color.White else Color.Black,
            itemHoverColor = (if (isSystemInDarkTheme()) Color.DarkGray else Color.LightGray).hover(),
        )
        CompositionLocalProvider(LocalContextMenuRepresentation provides contextMenuRepresentation) {
            ContextMenuDataProvider(
                items = {
                    //use mutable list or it can't add copy
                    mutableListOf(
                        ContextMenuItem("Scroll To Top") {
                            scope.launch {
                                scrollState.scrollTo(0)
                            }
                        },
                        ContextMenuItem("Scroll To Bottom") {
                            scope.launch {
                                scrollState.scrollTo(scrollState.maxValue)
                            }
                        },
                        ContextMenuItem("Clear Console") {
                            state.clear()
                        }
                    )
                }
            ) {
                val consoleText by remember {
                    derivedStateOf { state.consoleText }
                }
                Column(
                    modifier
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        if ((!popoutMode && !state.consolePoppedOut) || popoutMode) {
                            TooltipIconButton(
                                "Copy Console Text",
                                EvaIcons.Fill.Copy,
                                iconColor = MaterialTheme.colorScheme.primary
                            ) {
                                Tools.clipboardString = consoleText
                                windowScope.showToast("Copied Console Text")
                            }
                        }
                        if ((!popoutMode && !state.consolePoppedOut) || popoutMode) {
                            TooltipIconButton(
                                "Clear Console",
                                EvaIcons.Fill.Trash,
                                iconColor = MaterialTheme.colorScheme.primary
                            ) {
                                state.clear()
                            }
                        }
                        TooltipIconButton(
                            if (state.consolePoppedOut)
                                "Close Console Window" else "Pop out Console",
                            if (state.consolePoppedOut)
                                EvaIcons.Outline.Close else EvaIcons.Outline.DiagonalArrowRightUp,
                            iconColor = MaterialTheme.colorScheme.primary
                        ) {
                            if (state.consolePoppedOut) {
                                closeWindow()
                            } else {
                                openWindow()
                            }
                        }
                    }
                    if ((!popoutMode && !state.consolePoppedOut) || (popoutMode && state.consolePoppedOut)) {
                        FullBox {
                            Column(
                                Modifier.padding(1.dp)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(8.dp)
                                    )
                            ) {
                                TextField(
                                    value = consoleText,
                                    readOnly = true,
                                    onValueChange = {},
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .onClick(
                                            matcher = PointerMatcher.mouse(PointerButton.Secondary)
                                        ) {}
                                        .verticalScroll(scrollState),
                                    colors = TextFieldDefaults.colors(),
                                    shape = RoundedCornerShape(8.dp),
                                    textStyle = if (errorMode)
                                        MaterialTheme.typography.labelSmall
                                    else
                                        MaterialTheme.typography.labelLarge
                                )
                            }
                            VerticalScrollbar(
                                modifier = Modifier.align(Alignment.CenterEnd)
                                    .padding(
                                        top = 5.dp,
                                        bottom = 5.dp,
                                        end = 3.dp
                                    )
                                    .background(
                                        MaterialTheme.colorScheme
                                            .surface
                                            .tone(15.0),
                                    )
                                    .clipToBounds()
                                    .fillMaxHeight(),
                                style = DefaultScrollbarStyle,
                                adapter = rememberScrollbarAdapter(
                                    scrollState = scrollState
                                )
                            )
                        }
                    } else if (!popoutMode && state.consolePoppedOut) {
                        Column(
                            modifier = modifier
                                .padding(1.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.tone(20.0),
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.error,
                                    RoundedCornerShape(8.dp)
                                )
                        ) {}
                    }
                    LaunchedEffect(consoleText) {
                        //Auto-scroll to bottom when new line appears
                        if (Defaults.AUTO_SCROLL_CONSOLES.boolean()) {
                            scrollState.animateScrollTo(scrollState.maxValue)
                        }
                    }
                }
            }
        }

    }

    private val windowTitle = "${if (errorMode) "Error " else ""}Console"

    fun closeWindow() {
        ApplicationState.removeWindowWithId(windowTitle)
    }

    fun openWindow() {
        state.consolePoppedOut = true
        ApplicationState.newWindow(
            windowTitle,
            size = DpSize(400.dp, 250.dp),
            windowAlignment = Alignment.BottomEnd,
            alwaysOnTop = Defaults.CONSOLE_ON_TOP.boolean(),
            onClose = {
                state.consolePoppedOut = false
                return@newWindow true
            }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.background(
                    MaterialTheme.colorScheme.surface
                )
            ) {
                ConsoleTextField(
                    this@newWindow,
                    modifier = Modifier.fillMaxSize(),
                    popoutMode = true
                )
            }
        }
    }
}