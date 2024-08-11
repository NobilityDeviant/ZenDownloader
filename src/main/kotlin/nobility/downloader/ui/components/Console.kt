package nobility.downloader.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.util.*

class Console(
    private val errorMode: Boolean = false
) : OutputStream() {

    private var consoleText = mutableStateOf("")
    private val sb = StringBuilder()
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
            if (errorMode) 2_000
            else 400
        )
        if (b == '\r'.code) return
        if (b == '\n'.code) {
            val text = """
                $sb
              
                """.trimIndent()
            consoleText.value = consoleText.value.plus(text)
            size++
            sb.setLength(0)
            if (!errorMode) {
                sb.append(time()).append(" ")
            }
            return
        }
        if (!errorMode) {
            if (sb.isEmpty()) {
                sb.append(time()).append(" ")
            }
        }
        sb.append(b.toChar())
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
        return consoleText.value.isEmpty() && size > 0
    }

    private fun clear() {
        consoleText.value = ""
        size = 0
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun textField(unbound: Boolean = false) {
        val scrollState = rememberScrollState(size)
        val scope = rememberCoroutineScope()
        val contextMenuRepresentation = if (isSystemInDarkTheme()) {
            DarkDefaultContextMenuRepresentation
        } else {
            LightDefaultContextMenuRepresentation
        }
        CompositionLocalProvider(LocalContextMenuRepresentation provides contextMenuRepresentation) {
            ContextMenuDataProvider(
                items = {
                    listOf(
                        ContextMenuItem("Clear Console") { clear() },
                        ContextMenuItem("Scroll To Top") {
                            scope.launch {
                                scrollState.scrollTo(0)
                            }
                        },
                        ContextMenuItem("Scroll To Bottom") {
                            scope.launch {
                                scrollState.scrollTo(scrollState.maxValue)
                            }
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
                    TextField(
                        value = consoleText.value,
                        readOnly = true,
                        onValueChange = {
                            consoleText.value = it
                        },
                        modifier = modifier.onClick(
                            matcher = PointerMatcher.mouse(PointerButton.Secondary)
                        ) {}.verticalScroll(scrollState),
                        colors = TextFieldDefaults.colors()
                    )
                    //auto scroll
                    LaunchedEffect(size) {
                        scrollState.animateScrollTo(scrollState.maxValue)
                    }
                }
            }
        }
    }

}