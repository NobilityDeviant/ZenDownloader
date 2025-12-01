package nobility.downloader.ui.windows

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.fill.*
import nobility.downloader.core.Core
import nobility.downloader.core.settings.Save
import nobility.downloader.ui.components.ColumnItem
import nobility.downloader.ui.components.DropdownOption
import nobility.downloader.ui.components.LazyTable
import nobility.downloader.ui.components.TooltipIconButton
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.Constants
import nobility.downloader.utils.Constants.mediumIconSize

class DownloadQueueWindow {

    private val thread get() = Core.child.downloadThread

    @OptIn(ExperimentalMaterial3Api::class)
    fun open() {
        ApplicationState.newWindow(
            "Download Queue"
        ) {
            Scaffold(
                Modifier.fillMaxSize(),
                topBar = {
                    TopAppBar(
                        modifier = Modifier.height(Constants.topBarHeight),
                        title = {},
                        actions = {
                            TooltipIconButton(
                                "Clear All",
                                EvaIcons.Fill.Trash2,
                                mediumIconSize,
                                onClick = {
                                    if (thread.isQueueEmpty) {
                                        showToast("No downloads are in the queue.")
                                        return@TooltipIconButton
                                    }
                                    DialogHelper.showConfirm(
                                        """
                                                    This will remove all queued downloads.
                                                    Are you sure you want to remove everything?
                                                """.trimIndent()
                                    ) {
                                        thread.clear()
                                    }
                                },
                                iconColor = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    )
                }
            ) { paddingValues ->
                val scrollState = rememberLazyListState()

                LazyTable(
                    listOf(
                        ColumnItem(
                            "Name",
                            weightSaveKey = Save.DQ_N_WEIGHT,
                            sortSelector = { it.episode.name }
                        ) { _, queue ->
                            Text(
                                text = queue.episode.name,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize
                            )
                        },
                        ColumnItem(
                            "Position",
                            0.1f,
                            weightSaveKey = Save.DQ_P_WEIGHT
                        ) { index, _ ->
                            Text(
                                text = index.toString(),
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                textAlign = TextAlign.Center
                            )
                        }
                    ),
                    thread.downloadQueue,
                    key = { it.episode.name + it.episode.id },
                    lazyListState = scrollState,
                    headerColor = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(paddingValues),
                    sortSaveKey = Save.DQ_SORT
                ) { index, queue ->
                    listOf(
                        DropdownOption(
                            "Series Details",
                            EvaIcons.Fill.Info
                        ) {
                            Core.openSeriesDetails(
                                queue.episode.seriesSlug
                            )
                        },
                        DropdownOption(
                            "Move Up",
                            EvaIcons.Fill.ArrowUp,
                            index != 0
                        ) {

                            val currentIndex = thread.downloadQueue.indexOfFirst {
                                it.episode.matches(queue.episode)
                            }
                            thread.downloadQueue.removeAt(currentIndex)
                            thread.downloadQueue.add(currentIndex - 1, queue)
                        },
                        DropdownOption(
                            "Move Down",
                            EvaIcons.Fill.ArrowDown,
                            index != thread.downloadQueue.lastIndex
                        ) {

                            val currentIndex = thread.downloadQueue.indexOfFirst {
                                it.episode.matches(queue.episode)
                            }
                            thread.downloadQueue.removeAt(currentIndex)
                            thread.downloadQueue.add(currentIndex + 1, queue)
                        },
                        DropdownOption(
                            "Remove",
                            EvaIcons.Fill.Trash
                        ) {
                            thread.removeFromQueue(queue)
                        }
                    )
                }
                LaunchedEffect(
                    thread.downloadQueue.size
                ) {
                    scrollState.animateScrollToItem(0)
                }
            }
        }
    }
}