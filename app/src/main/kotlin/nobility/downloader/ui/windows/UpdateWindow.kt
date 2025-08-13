package nobility.downloader.ui.windows

import AppInfo
import Update
import Updater
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.BoxHelper.Companion.update
import nobility.downloader.core.Core
import nobility.downloader.core.settings.Defaults
import nobility.downloader.ui.components.DefaultButton
import nobility.downloader.ui.components.FullBox
import nobility.downloader.ui.components.GithubText
import nobility.downloader.ui.components.defaultButton
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.Constants.bottomBarHeight
import nobility.downloader.utils.FrogLog

class UpdateWindow(
    private val justCheck: Boolean = false
) {

    private var mUpdate: Update? = null
    private val update get() = mUpdate!!
    private val downloadScope = CoroutineScope(Dispatchers.IO)
    private var appWindowScope: AppWindowScope? = null
    private var updateButtonEnabled = mutableStateOf(true)

    init {
        downloadScope.launch {
            checkForUpdates()
        }
    }

    private fun open() {
        if (mUpdate == null) {
            return
        }
        ApplicationState.newWindow(
            if (update.isLatest) "Updated" else "Update Available",
            onClose = {
                if (downloadScope.isActive) {
                    downloadScope.cancel()
                }
                appWindowScope = null
                true
            }
        ) {
            appWindowScope = this
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    Column(
                        modifier = Modifier.fillMaxWidth().height(bottomBarHeight)
                    ) {
                        HorizontalDivider()
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                                .padding(10.dp)
                        ) {
                            if (!Defaults.DENIED_UPDATE.boolean()) {
                                defaultButton(
                                    "Deny Update",
                                    height = 40.dp,
                                    width = 150.dp,
                                    enabled = !update.isLatest
                                ) {
                                    Defaults.DENIED_UPDATE.update(true)
                                    FrogLog.message(
                                        "The latest update has been denied. You will no longer receive a notification about it until the next update."
                                    )
                                    close()
                                }
                            }
                            DefaultButton(
                                "Update",
                                height = 40.dp,
                                width = 150.dp,
                                enabled = updateButtonEnabled
                            ) {
                                Core.child.shutdown(true)
                            }
                        }
                    }
                }
            ) { padding ->
                FullBox {
                    Column(
                        modifier = Modifier.padding(
                            bottom = padding.calculateBottomPadding()
                        ).fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            if (update.isLatest)
                                "You have the latest update."
                            else
                                "There's a new update available!",
                            textAlign = TextAlign.Center,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(4.dp)
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            GithubText(
                                text = """
                                      Current Version: ${AppInfo.VERSION}
                                      Latest Version: ${update.version}
                                      
                                      Update Download Link:
                                       
                                      ${update.downloadLink}
                                   """.trimIndent(),
                                style = MaterialTheme.typography.bodyMedium,
                                textColor = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                linkColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth().padding(4.dp)
                            )
                        }
                        Text(
                            "Update Log:",
                            textAlign = TextAlign.Center,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                            fontWeight = FontWeight.Bold
                        )
                        val scrollState = rememberScrollState()
                        val scope = rememberCoroutineScope()
                        Surface(
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            GithubText(
                                text = update.updateDescription,
                                style = MaterialTheme.typography.bodyMedium,
                                textColor = MaterialTheme.colorScheme.onSurface,
                                linkColor = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Justify,
                                modifier = Modifier.padding(10.dp).verticalScroll(scrollState)
                                    .draggable(
                                        state = rememberDraggableState {
                                            scope.launch {
                                                scrollState.scrollBy(-it)
                                            }
                                        },
                                        orientation = Orientation.Vertical,
                                    )
                            )
                        }
                    }
                }
                ApplicationState.AddToastToWindow(this)
            }
        }
    }

    private fun close() {
        if (downloadScope.isActive) {
            downloadScope.cancel()
        }
        appWindowScope?.closeWindow()
    }

    private suspend fun checkForUpdates() = withContext(Dispatchers.IO) {
        if (!justCheck) {
            FrogLog.message(
                "Checking for updates..."
            )
        }
        if (mUpdate == null) {
            val result = Updater.parseLatestRelease()
            if (result.data != null) {
                mUpdate = result.data
            } else {
                val message = result.message
                FrogLog.error(
                    "Failed to check for updates. " +
                            if (!message.isNullOrEmpty()) message else ""
                )
                return@withContext
            }
        }
        if (!Defaults.UPDATE_VERSION.string().equals(
                update.version, ignoreCase = true
            )
        ) {
            Defaults.DENIED_UPDATE.update(false)
        }
        Defaults.UPDATE_VERSION.update(update.version)
        val latest = Updater.isLatest(update.version)
        if (justCheck && Defaults.DENIED_UPDATE.boolean()) {
            close()
            return@withContext
        }
        if (justCheck && latest) {
            close()
            return@withContext
        }
        update.isLatest = latest
        updateButtonEnabled.value = !latest
        withContext(Dispatchers.Main) {
            open()
        }
    }
}