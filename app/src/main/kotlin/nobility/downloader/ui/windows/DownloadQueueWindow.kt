package nobility.downloader.ui.windows

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.fill.*
import nobility.downloader.core.Core
import nobility.downloader.core.scraper.data.DownloadQueue
import nobility.downloader.ui.components.*
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.Constants
import nobility.downloader.utils.Constants.mediumIconSize
import nobility.downloader.utils.hover

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
                                iconColor = MaterialTheme.colorScheme.tertiary,
                                spacePosition = SpacePosition.START,
                                space = 10.dp
                            )
                        }
                    )
                }
            ) { paddingValues ->
                val scrollState = rememberLazyListState()
                SortedLazyColumn(
                    listOf(
                        HeaderItem(
                            "Name"
                        ) { it.episode.name },
                        HeaderItem(
                            "Position",
                            0.1f
                        ),
                    ),
                    thread.downloadQueue,
                    key = { it.episode.name + it.episode.id },
                    lazyListState = scrollState,
                    headerColor = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(paddingValues)
                ) { index, item ->
                    DownloadQueueRow(
                        item,
                        index
                    )
                }
                LaunchedEffect(
                    thread.downloadQueue.size
                ) {
                    scrollState.animateScrollToItem(0)
                }
                ApplicationState.AddToastToWindow(this)
            }
        }
    }

    //downloads really do need a priority.
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun DownloadQueueRow(
        queue: DownloadQueue,
        index: Int
    ) {
        var showFileMenu by remember {
            mutableStateOf(false)
        }

        DefaultCursorDropdownMenu(
            showFileMenu,
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
        ) { showFileMenu = false }

        Row(
            modifier = Modifier
                .multiClickable(
                    indication = ripple(
                        color = MaterialTheme.colorScheme
                            .tertiaryContainer.hover()
                    ),
                    onSecondaryClick = {
                        showFileMenu = showFileMenu.not()
                    }
                ) {
                    showFileMenu = showFileMenu.not()
                }
                .background(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(5.dp)
                )
                .height(85.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = queue.episode.name,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.CenterVertically)
                    .weight(1f)
            )
            Divider()
            Text(
                text = index.toString(),
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.CenterVertically)
                    .weight(0.1f)
            )
        }
    }

    @Composable
    private fun Divider() {
        VerticalDivider(
            modifier = Modifier.fillMaxHeight()
                .width(1.dp)
                .background(
                    MaterialTheme.colorScheme.onTertiaryContainer
                ),
            color = Color.Transparent
        )
    }
}