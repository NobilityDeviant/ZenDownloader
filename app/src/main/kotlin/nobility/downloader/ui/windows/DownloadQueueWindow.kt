package nobility.downloader.ui.windows

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CursorDropdownMenu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.fill.*
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Episode
import nobility.downloader.ui.components.*
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.Constants
import nobility.downloader.utils.Constants.mediumIconSize
import nobility.downloader.utils.hover

class DownloadQueueWindow {

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
                                    if (Core.child.downloadThread.downloadQueue.isEmpty()) {
                                        showToast("No downloads are in the queue.")
                                        return@TooltipIconButton
                                    }
                                    DialogHelper.showConfirm(
                                        """
                                                    This will remove all queued downloads.
                                                    Are you sure you want to remove everything?
                                                """.trimIndent()
                                    ) {
                                        Core.child.downloadThread.clear()
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
                        ) { it.name },
                        HeaderItem(
                            "Position",
                            0.1f
                        ),
                    ),
                    Core.child.downloadThread.downloadQueue,
                    key = { it.name + it.id },
                    lazyListState = scrollState,
                    headerColor = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(paddingValues)
                ) { index, item ->
                    EpisodeRow(
                        item,
                        index
                    )
                }
                LaunchedEffect(
                    Core.child.downloadThread.downloadQueue.size
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
    private fun EpisodeRow(
        episode: Episode,
        index: Int
    ) {
        var showFileMenu by remember {
            mutableStateOf(false)
        }
        val closeMenu = { showFileMenu = false }

        CursorDropdownMenu(
            expanded = showFileMenu,
            onDismissRequest = { closeMenu() },
            modifier = Modifier.background(
                MaterialTheme.colorScheme.background
            )
        ) {
            DefaultDropdownItem(
                "Series Details",
                EvaIcons.Fill.Info
            ) {
                closeMenu()
                Core.openSeriesDetails(
                    episode.seriesSlug
                )
            }
            if (index != 0) {
                DefaultDropdownItem(
                    "Move Up",
                    EvaIcons.Fill.ArrowUp
                ) {
                    closeMenu()
                    val currentIndex = Core.child.downloadThread.downloadQueue.indexOf(episode)
                    Core.child.downloadThread.downloadQueue.removeAt(currentIndex)
                    Core.child.downloadThread.downloadQueue.add(currentIndex - 1, episode)
                }
            }
            if (index != Core.child.downloadThread.downloadQueue.lastIndex) {
                DefaultDropdownItem(
                    "Move Down",
                    EvaIcons.Fill.ArrowDown
                ) {
                    closeMenu()
                    val currentIndex = Core.child.downloadThread.downloadQueue.indexOf(episode)
                    Core.child.downloadThread.downloadQueue.removeAt(currentIndex)
                    Core.child.downloadThread.downloadQueue.add(currentIndex + 1, episode)
                }
            }
            DefaultDropdownItem(
                "Remove",
                EvaIcons.Fill.Trash
            ) {
                closeMenu()
                Core.child.downloadThread.removeFromQueue(episode)
            }
        }

        Row(
            modifier = Modifier
                .onClick(
                    matcher = PointerMatcher.mouse(PointerButton.Secondary)
                ) { showFileMenu = showFileMenu.not() }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(
                        color = MaterialTheme.colorScheme
                            .tertiaryContainer.hover()
                    )
                ) { showFileMenu = showFileMenu.not() }
                .background(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(5.dp)
                )
                .height(85.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = episode.name,
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