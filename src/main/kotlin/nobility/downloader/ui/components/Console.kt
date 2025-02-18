package nobility.downloader.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import nobility.downloader.Page
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.Core
import nobility.downloader.core.settings.Defaults
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.AppInfo
import nobility.downloader.utils.Tools
import nobility.downloader.utils.hover
import nobility.downloader.utils.tone
import java.io.OutputStream
import java.util.*

class Console(
    private val errorMode: Boolean = false
) : OutputStream() {

    var consolePoppedOut by mutableStateOf(false)
    private var consoleText by mutableStateOf("")
    private val sb = StringBuilder()
    var unreadErrors by mutableStateOf(false)
    var size = 0
        private set

    init {
        if (!errorMode) {
            sb.append(time()).append(" ")
        }
    }

    private fun time(): String {
        val c = Calendar.getInstance()
        val hour = c[Calendar.HOUR]
        val minute = c[Calendar.MINUTE]
        return "[" + hour + ":" + (if (minute.toString().length == 1) "0" else "") + minute + "]"
    }

    override fun write(b: Int) {
        clearIfSize(
            @Suppress("KotlinConstantConditions")
            if (AppInfo.DEBUG_MODE)
                5_000
            else if (errorMode) 3_000
            else 1_000
        )
        if (b == '\r'.code) return
        if (b == '\n'.code) {
            var text = sb.toString()
            var filtered = false
            for (s in filters) {
                if (text.contains(s)) {
                    filtered = true
                    break
                }
            }
            if (!filtered) {
                consoleText = consoleText.plus(text.plus("\n"))
                size++
            }
            sb.clear()
            return
        }
        if (!errorMode) {
            if (sb.isEmpty()) {
                sb.append(time()).append(" ")
            }
        }
        sb.append(b.toChar())
        if (errorMode) {
            if (Core.currentPage != Page.ERROR_CONSOLE && !consolePoppedOut) {
                unreadErrors = true
            }
        }
    }

    override fun flush() {}

    override fun close() {
        clear()
    }

    private fun clearIfSize(sizeCheck: Int = 300) {
        if (size >= sizeCheck) {
            clear()
        }
    }

    fun isEmpty(): Boolean {
        return consoleText.isEmpty() && size <= 0
    }

    fun clear() {
        consoleText = ""
        size = 0
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun textField(
        windowScope: AppWindowScope,
        unbound: Boolean = false,
        popoutMode: Boolean = false
    ) {
        val scrollState = rememberScrollState(size)
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
                            clear()
                        }
                    )
                }
            ) {
                Column {
                    val modifier = if (unbound) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier.fillMaxWidth()
                            .height(200.dp)
                            .padding(start = 5.dp, end = 5.dp, bottom = 5.dp)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        if ((!popoutMode && !consolePoppedOut) || popoutMode) {
                            tooltipIconButton(
                                "Copy Console Text",
                                EvaIcons.Fill.Copy,
                                iconColor = MaterialTheme.colorScheme.primary
                            ) {
                                Tools.clipboardString = consoleText
                                windowScope.showToast("Copied Console Text")
                            }
                        }
                        if ((!popoutMode && !consolePoppedOut) || popoutMode) {
                            tooltipIconButton(
                                "Clear Console",
                                EvaIcons.Fill.Trash,
                                iconColor = MaterialTheme.colorScheme.primary
                            ) {
                                clear()
                            }
                        }
                        tooltipIconButton(
                            if (consolePoppedOut)
                                "Close Console Window" else "Pop out Console",
                            if (consolePoppedOut)
                                EvaIcons.Outline.Close else EvaIcons.Outline.DiagonalArrowRightUp,
                            iconColor = MaterialTheme.colorScheme.primary
                        ) {
                            if (consolePoppedOut) {
                                closeWindow()
                            } else {
                                openWindow()
                            }
                        }
                    }
                    if ((!popoutMode && !consolePoppedOut) || (popoutMode && consolePoppedOut)) {
                        TextField(
                            value = consoleText,
                            readOnly = true,
                            onValueChange = {
                                consoleText = it
                            },
                            modifier = modifier.onClick(
                                matcher = PointerMatcher.mouse(PointerButton.Secondary)
                            ) {}.verticalScroll(scrollState),
                            colors = TextFieldDefaults.colors(),
                            textStyle = if (errorMode)
                                MaterialTheme.typography.labelSmall
                            else
                                MaterialTheme.typography.labelLarge
                        )
                    } else if (!popoutMode && consolePoppedOut) {
                        Column(
                            modifier = modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.tone(20.0)
                                )
                        ) {}
                    }
                    //auto scroll
                    LaunchedEffect(size) {
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
        consolePoppedOut = true
        ApplicationState.newWindow(
            windowTitle,
            size = DpSize(400.dp, 250.dp),
            windowAlignment = Alignment.BottomEnd,
            alwaysOnTop = Defaults.CONSOLE_ON_TOP.boolean(),
            onClose = {
                consolePoppedOut = false
                return@newWindow true
            }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.background(
                    MaterialTheme.colorScheme.surface
                )
            ) {
                textField(
                    this@newWindow,
                    true,
                    true
                )
            }
        }
    }

    companion object {
        private val filters = listOf<String>(
            "Hint: use closeThreadResources() to avoid finalizing recycled transactions"
        )
    }
}