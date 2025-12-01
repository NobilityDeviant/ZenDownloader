package nobility.downloader.ui.windows.utils

import AppInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import nobility.downloader.core.Core
import nobility.downloader.ui.components.toast.ToastHost
import nobility.downloader.ui.components.toast.ToastHostState
import nobility.downloader.ui.theme.CoreTheme
import nobility.downloader.utils.FrogLog
import nobility.downloader.utils.ImageUtils
import org.apache.commons.lang3.SystemUtils
import java.awt.Frame

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
        val existing = windows.find { it.scope.windowId == data.scope.windowId }
        if (existing != null) {
            try {
                existing.scope.composeWindow?.apply {
                    if (extendedState and Frame.ICONIFIED != 0) {
                        extendedState = extendedState and Frame.ICONIFIED.inv()
                    }
                    toFront()
                    requestFocus()
                }
            } catch (e: Exception) {
                FrogLog.error(
                    "Failed to bring existing window to the front.",
                    e
                )
            }
            return
        }
        windows.add(data)
    }


    companion object {

        val shared = ApplicationState()

        fun removeWindowWithId(
            id: String,
            triggerOnClose: Boolean = true
        ) {
            shared.windows.toList().forEach {
                if (it.scope.windowId == id) {
                    it.scope.open.value = false
                    if (triggerOnClose) {
                        it.scope.onClose?.invoke()
                    }
                    shared.windows.remove(it)
                    return@forEach
                }
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
        private fun Toaster(scope: AppWindowScope) {
            val toastHostState = remember { ToastHostState() }
            Box(
                contentAlignment = Alignment.Center
            ) {
                ToastHost(
                    hostState = toastHostState
                )
            }
            LaunchedEffect(scope.toastContent.value) {
                if (scope.toastContent.value.isNotEmpty()) {
                    toastHostState.showToast(
                        scope.toastContent.value
                    )
                    scope.toastContent.value = ""
                }
            }
        }

        fun newWindow(
            title: String,
            keyEvents: ((Boolean, KeyEvent) -> Boolean)? = null,
            size: DpSize = DpSize(800.dp, 600.dp),
            onClose: (() -> Boolean)? = null,
            maximized: Boolean = false,
            undecorated: Boolean = false,
            transparent: Boolean = false,
            resizable: Boolean = true,
            alwaysOnTop: Boolean = false,
            windowAlignment: Alignment = Alignment.Center,
            isAssetWindow: Boolean = false,
            addToast: Boolean = true,
            content: @Composable (AppWindowScope.() -> Unit)
        ) {
            val scope = object : AppWindowScope {
                override val windowId: String = title
                override var open = mutableStateOf(true)
                override var focused = mutableStateOf(false)
                override var toastContent = mutableStateOf("")
                override var onClose: (() -> Boolean)? = onClose
                override fun closeWindow(triggerOnClose: Boolean) {
                    removeWindowWithId(windowId, triggerOnClose)
                }
                override var composeWindow: ComposeWindow? = null
            }
            shared.addWindow(defaultWindowData(
                title,
                scope
            ) {
                val open by remember { scope.open }
                if (open) {
                    Window(
                        icon = remember { ImageUtils.loadPainterFromResource(AppInfo.APP_ICON_PATH) },
                        //undecorated windows don't work on Windows 7
                        undecorated = if (!SystemUtils.IS_OS_WINDOWS_7 || (!isAssetWindow && Core.windowFlag)) undecorated else false,
                        transparent = if (!SystemUtils.IS_OS_WINDOWS_7 || (!isAssetWindow && Core.windowFlag)) transparent else false,
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
                            placement = if (maximized)
                                WindowPlacement.Maximized
                            else WindowPlacement.Floating
                        ),
                        onKeyEvent = {
                            keyEvents?.invoke(
                                scope.focused.value,
                                it
                            )?: false
                        },
                        content = {
                            scope.composeWindow = window
                            val focused = LocalWindowInfo.current.isWindowFocused
                            LaunchedEffect(focused) {
                                scope.focused.value = focused
                            }
                            CoreTheme {
                                scope.content()
                                if (addToast) {
                                    Toaster(scope)
                                }
                            }
                        }
                    )
                }
            })
        }

        fun bringMainToFront() {
            newWindow(AppInfo.TITLE) {}
        }

        private fun mainWindowScope(): AppWindowScope? {
            val main = shared.windows.find { it.title == AppInfo.TITLE }
            return main?.scope
        }

        fun showToastForMain(message: String) {
            mainWindowScope()?.showToast(message)
        }
    }
}
