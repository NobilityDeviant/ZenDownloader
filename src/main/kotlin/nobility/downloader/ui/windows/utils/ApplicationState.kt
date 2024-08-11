package nobility.downloader.ui.windows.utils

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.launch
import nobility.downloader.ui.components.toast.ToastHost
import nobility.downloader.ui.components.toast.ToastHostState
import nobility.downloader.ui.theme.CoreTheme
import nobility.downloader.utils.AppInfo

/**
 * My implementation of Jetpacks multi window example.
 * https://github.com/JetBrains/compose-multiplatform/blob/master/tutorials/Window_API_new/README.md#open-and-close-multiple-windows
 * This is more versatile.
 * I don't think it'll be so bad since the content is only stored, not executed(unless opened).
 * The whole point is the singleton!
 * We can create a new window ANYWHERE using:
 * ApplicationState.newWindow(title) { composable content }
 * We can also close the window inside the AppWindowScope like so:
 * ApplicationState.newWindow(title) { closeWindow() }
 * @author NobilityDev
 */

class ApplicationState {

    val windows = mutableStateListOf<WindowData>()

    fun addWindow(data: WindowData) {
        if (windows.any { it.scope.windowId == data.scope.windowId }) {
            return
        }
        windows.add(data)
    }

    companion object {

        val shared = ApplicationState()

        private fun removeWindowWithId(id: String) {
            var pos = -1
            shared.windows.forEachIndexed { index, windowData ->
                if (windowData.scope.windowId == id) {
                    pos = index
                    return@forEachIndexed
                }
            }
            if (pos != -1) {
                val window = shared.windows[pos]
                window.scope.open.value = false
                window.scope.onClose?.invoke()
                shared.windows.removeAt(pos)
            }
        }

        fun removeWindowWithTitle(title: String) {
            var pos = -1
            shared.windows.forEachIndexed { index, windowData ->
                if (windowData.title == title) {
                    pos = index
                    return@forEachIndexed
                }
            }
            if (pos != -1) {
                val window = shared.windows[pos]
                window.scope.open.value = false
                window.scope.onClose?.invoke()
                shared.windows.removeAt(pos)
            }
        }

        private fun defaultWindowData(
            title: String,
            scope: AppWindowScope,
            content: @Composable () -> Unit
        ) = WindowData(
            title,
            scope,
            content = content
        )

        @Composable
        fun addToastToWindow(scope: AppWindowScope) {
            val toastHostState = remember { ToastHostState() }
            val coScope = rememberCoroutineScope()
            Box(
                contentAlignment = Alignment.Center
            ) {
                ToastHost(
                    hostState = toastHostState
                )
            }
            if (scope.toastContent.value.isNotEmpty()) {
                coScope.launch {
                    toastHostState.showToast(
                        scope.toastContent.value
                    )
                    scope.toastContent.value = ""
                }
            }
        }

        fun newWindow(
            title: String,
            keyEvents: ((KeyEvent) -> Boolean)? = null,
            size: DpSize = DpSize(800.dp, 600.dp),
            onClose: (() -> Boolean)? = null,
            maximized: Boolean = false,
            undecorated: Boolean = false,
            transparent: Boolean = false,
            resizable: Boolean = true,
            alwaysOnTop: Boolean = false,
            windowAlignment: Alignment = Alignment.Center,
            content: @Composable (AppWindowScope.() -> Unit)
        ) {
            val scope = object : AppWindowScope {
                override val windowId: String = title
                override var open = mutableStateOf(true)
                override var toastContent = mutableStateOf("")
                override var onClose: (() -> Boolean)? = onClose
                override fun closeWindow() {
                    removeWindowWithId(windowId)
                }
            }
            shared.addWindow(defaultWindowData(
                title,
                scope
            ) {
                val open by remember { scope.open }
                if (open) {
                    Window(
                        icon = painterResource(AppInfo.APP_ICON_PATH),
                        undecorated = undecorated,
                        transparent = transparent,
                        resizable = resizable,
                        alwaysOnTop = alwaysOnTop,
                        onCloseRequest = {
                            if (onClose != null) {
                                if (onClose()) {
                                    scope.closeWindow()
                                }
                            } else {
                                scope.closeWindow()
                            }
                        },
                        title = title,
                        state = rememberWindowState(
                            position = WindowPosition.Aligned(alignment = windowAlignment),
                            size = size,
                            placement = if (maximized) WindowPlacement.Maximized else WindowPlacement.Floating
                        ),
                        onKeyEvent = keyEvents ?: { false },
                        content = {
                            CoreTheme {
                                val toastHostState = remember { ToastHostState() }
                                val coScope = rememberCoroutineScope()
                                ToastHost(
                                    hostState = toastHostState
                                )
                                if (scope.toastContent.value.isNotEmpty()) {
                                    coScope.launch {
                                        toastHostState.showToast(
                                            scope.toastContent.value
                                        )
                                        scope.toastContent.value = ""
                                    }
                                }
                                scope.content()
                            }
                        }
                    )
                }
            })
        }
    }
}
