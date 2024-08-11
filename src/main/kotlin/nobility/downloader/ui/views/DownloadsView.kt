package nobility.downloader.ui.views

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CursorDropdownMenu
import androidx.compose.material.Icon
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.fill.*
import kotlinx.coroutines.launch
import nobility.downloader.core.BoxHelper.Companion.wcoSeriesForSlug
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Download
import nobility.downloader.core.scraper.data.ToDownload
import nobility.downloader.ui.components.defaultDropdownItem
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.utils.Tools
import nobility.downloader.utils.slugToLink

class DownloadsView {

    private var sort by mutableStateOf(Sort.DATE_DESC)
    private var deletedFile = false //used so it doesn't scroll to top on deletion (hacky)

    @Composable
    fun downloadsUi(windowScope: AppWindowScope) {
        val scope = rememberCoroutineScope()
        val listState = rememberLazyListState()
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            header()
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(
                        top = 5.dp,
                        bottom = 5.dp,
                        end = 12.dp
                    ).fillMaxSize()
                        .draggable(
                            state = rememberDraggableState {
                                scope.launch {
                                    listState.scrollBy(-it)
                                }
                            },
                            orientation = Orientation.Vertical,
                        ),
                    state = listState
                ) {
                    items(downloads, key = { it.name }) {
                        downloadRow(it, windowScope)
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.onSurfaceVariant)
                        .padding(top = 3.dp, bottom = 3.dp),
                    style = ScrollbarStyle(
                        minimalHeight = 16.dp,
                        thickness = 10.dp,
                        shape = RoundedCornerShape(10.dp),
                        hoverDurationMillis = 300,
                        unhoverColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f),
                        hoverColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.90f)
                    ),
                    adapter = rememberScrollbarAdapter(
                        scrollState = listState
                    )
                )
            }
        }
        LaunchedEffect(downloads.size) {
            if (!deletedFile) {
                listState.animateScrollToItem(0)
            } else {
                deletedFile = false
            }
        }
    }

    private val downloads: List<Download>
        get() {
            return when (sort) {
                Sort.NAME -> {
                    Core.child.downloadList.sortedBy { it.name }
                }

                Sort.NAME_DESC -> {
                    Core.child.downloadList.sortedByDescending { it.name }
                }

                Sort.DATE -> {
                    Core.child.downloadList.sortedBy { it.dateAdded }
                }

                Sort.DATE_DESC -> {
                    Core.child.downloadList.sortedByDescending { it.dateAdded }
                }

                Sort.PROGRESS -> {
                    Core.child.downloadList.sortedBy { it.progress.value }
                }

                Sort.PROGRESS_DESC -> {
                    Core.child.downloadList.sortedByDescending { it.progress.value }
                }
            }
        }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun header() {
        Row(
            modifier = Modifier.background(
                color = MaterialTheme.colorScheme.inversePrimary,
                shape = RectangleShape
            ).height(40.dp).fillMaxWidth().padding(end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(NAME_WEIGHT)
                    .align(Alignment.CenterVertically).onClick {
                        sort = when (sort) {
                            Sort.NAME -> {
                                Sort.NAME_DESC
                            }

                            Sort.NAME_DESC -> {
                                Sort.NAME
                            }

                            else -> {
                                Sort.NAME_DESC
                            }
                        }
                    }
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spaceBetweenNameAndIcon),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Name (Resolution)",
                        modifier = Modifier
                            .padding(4.dp)
                            .align(Alignment.CenterVertically),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        textAlign = TextAlign.Center
                    )
                    if (sort == Sort.NAME || sort == Sort.NAME_DESC) {
                        Icon(
                            if (sort == Sort.NAME_DESC)
                                EvaIcons.Fill.ArrowIosDownward
                            else
                                EvaIcons.Fill.ArrowIosUpward,
                            "",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            divider(true)
            Text(
                text = "File Size",
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.CenterVertically)
                    .weight(FILE_SIZE_WEIGHT),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                textAlign = TextAlign.Center
            )
            divider(true)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(DATE_WEIGHT)
                    .align(Alignment.CenterVertically).onClick {
                        sort = when (sort) {
                            Sort.DATE -> {
                                Sort.DATE_DESC
                            }

                            Sort.DATE_DESC -> {
                                Sort.DATE
                            }

                            else -> {
                                Sort.DATE_DESC
                            }
                        }
                    }
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spaceBetweenNameAndIcon),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Date Created",
                        modifier = Modifier
                            .padding(4.dp)
                            .align(Alignment.CenterVertically),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        textAlign = TextAlign.Center
                    )
                    if (sort == Sort.DATE || sort == Sort.DATE_DESC) {
                        Icon(
                            if (sort == Sort.DATE_DESC)
                                EvaIcons.Fill.ArrowIosDownward
                            else
                                EvaIcons.Fill.ArrowIosUpward,
                            "",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            divider(true)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(PROGRESS_WEIGHT)
                    .align(Alignment.CenterVertically).onClick {
                        sort = when (sort) {
                            Sort.PROGRESS -> {
                                Sort.PROGRESS_DESC
                            }

                            Sort.PROGRESS_DESC -> {
                                Sort.PROGRESS
                            }

                            else -> {
                                Sort.PROGRESS_DESC
                            }
                        }
                    }
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spaceBetweenNameAndIcon),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Progress",
                        modifier = Modifier
                            .padding(4.dp)
                            .align(Alignment.CenterVertically),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        textAlign = TextAlign.Center
                    )
                    if (sort == Sort.PROGRESS || sort == Sort.PROGRESS_DESC) {
                        Icon(
                            if (sort == Sort.PROGRESS_DESC)
                                EvaIcons.Fill.ArrowIosDownward
                            else
                                EvaIcons.Fill.ArrowIosUpward,
                            "",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun downloadRow(
        download: Download,
        windowScope: AppWindowScope
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
            if (!download.isComplete) {
                if (download.downloading) {
                    defaultDropdownItem(
                        "Pause",
                        EvaIcons.Fill.PauseCircle
                    ) {
                        closeMenu()
                    }
                } else {
                    defaultDropdownItem(
                        "Resume",
                        EvaIcons.Fill.Download
                    ) {
                        if (Core.child.isRunning) {
                            if (Core.child.addEpisodeToQueue(download)) {
                                windowScope.showToast("Added episode to the download queue.")
                            } else {
                                windowScope.showToast("This episode is already in the download queue.")
                            }
                        } else {
                            Core.currentUrl = download.slug.slugToLink()
                            Core.child.start()
                            windowScope.showToast("Launched downloader for ${download.name}")
                        }
                        closeMenu()
                    }
                }
            }
            defaultDropdownItem(
                "Series Details",
                EvaIcons.Fill.Info
            ) {
                closeMenu()
                val slug = download.seriesSlug
                if (slug.isNotEmpty()) {
                    val series = wcoSeriesForSlug(slug)
                    if (series != null) {
                        Core.openDownloadConfirm(
                            ToDownload(series)
                        )
                    } else {
                        if (!Core.child.isRunning) {
                            DialogHelper.showMessage(
                                "Failed to find local series",
                                "Launching the downloader for ${slug.slugToLink()}"
                            )
                            Core.currentUrl = slug.slugToLink()
                            Core.child.start()
                        } else {
                            DialogHelper.showError(
                                """
                                    Failed to find local series. 
                                    Unable to download it because the downloader is currently running.
                                    Here's the link: ${slug.slugToLink()}
                                """.trimIndent()
                            )
                        }
                    }
                } else {
                    windowScope.showToast("There is no series slug for this download.")
                }
            }
            defaultDropdownItem(
                "Open Folder",
                EvaIcons.Fill.Folder
            ) {
                closeMenu()
                Tools.openFolder(
                    download.downloadPath,
                    true
                )
            }
            if (download.isComplete) {
                defaultDropdownItem(
                    "Play Video",
                    EvaIcons.Fill.Video
                ) {
                    closeMenu()
                    Tools.openFolder(download.downloadPath)
                }
            }
            if (!download.downloading) {
                defaultDropdownItem(
                    "Remove From Downloads",
                    EvaIcons.Fill.Trash
                ) {
                    closeMenu()
                    if (download.downloading || download.queued) {
                        windowScope.showToast("You can't remove a download that's still downloading.")
                    } else {
                        deletedFile = true
                        Core.child.removeDownload(download)
                    }
                }
                defaultDropdownItem(
                    "Delete FIle",
                    EvaIcons.Fill.FileRemove
                ) {
                    closeMenu()
                    if (download.downloading || download.queued) {
                        windowScope.showToast("You can't delete a file that's downloading.")
                    } else {
                        DialogHelper.showConfirm(
                            """
                                Are you sure you want to delete the video:
                                ${download.name}?
                            """.trimIndent(),
                            onConfirmTitle = "Delete File"
                        ) {
                            val file = download.downloadFile()
                            if (file != null) {
                                if (file.delete()) {
                                    windowScope.showToast("File successfully deleted.")
                                    deletedFile = true
                                    Core.child.removeDownload(download)
                                } else {
                                    windowScope.showToast("Failed to delete file.")
                                }
                            } else {
                                windowScope.showToast("This file doesn't exist.")
                            }
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .onClick(
                    matcher = PointerMatcher.mouse(PointerButton.Secondary)
                ) { showFileMenu = showFileMenu.not() }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(color = MaterialTheme.colorScheme.primary)
                ) { showFileMenu = showFileMenu.not() }
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(5.dp)
                ).height(rowHeight).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = download.nameAndResolution(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.CenterVertically)
                    .weight(NAME_WEIGHT)
            )
            divider()
            Text(
                text = Tools.bytesToString(download.fileSize),
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.CenterVertically)
                    .weight(FILE_SIZE_WEIGHT),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                textAlign = TextAlign.Center
            )
            divider()
            Text(
                text = Tools.dateFormatted(download.dateAdded),
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.CenterVertically)
                    .weight(DATE_WEIGHT),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                textAlign = TextAlign.Center
            )
            divider()
            Text(
                text = download.progress.value + if (download.downloading)
                    "\n${Tools.secondsToRemainingTime(
                        download.remainingDownloadSeconds.value
                    )}" else "",
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.CenterVertically)
                    .weight(PROGRESS_WEIGHT),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                textAlign = TextAlign.Center
            )
            LaunchedEffect(download.progress.value) {

            }
        }
    }

    @Composable
    private fun divider(
        header: Boolean = false
    ) {
        Divider(
            modifier = Modifier.fillMaxHeight()
                .width(1.dp)
                .background(
                    if (header)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                ),
            color = Color.Transparent
        )
    }

    private enum class Sort {
        NAME,
        NAME_DESC,
        DATE,
        DATE_DESC,
        PROGRESS,
        PROGRESS_DESC
    }

    companion object {
        private val spaceBetweenNameAndIcon = 1.dp
        private val rowHeight = 80.dp
        private const val NAME_WEIGHT = 5f
        private const val FILE_SIZE_WEIGHT = 1f
        private const val DATE_WEIGHT = 1.5f
        private const val PROGRESS_WEIGHT = 1.5f
    }

}