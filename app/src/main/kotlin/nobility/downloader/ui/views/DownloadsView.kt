package nobility.downloader.ui.views

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.fill.*
import nobility.downloader.Page
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Download
import nobility.downloader.ui.components.*
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.utils.Tools
import nobility.downloader.utils.hover

class DownloadsView : ViewPage {

    override val page = Page.DOWNLOADS
    private var deletedFile = false //used so it doesn't scroll to top on deletion (hacky)

    @Composable
    override fun Ui(windowScope: AppWindowScope) {
        val lazyListState = rememberLazyListState()
        SortedLazyColumn(
            listOf(
                HeaderItem(
                    "Series",
                    SERIES_NAME_WEIGHT
                ) { it.seriesName },
                HeaderItem(
                    "Name",
                    NAME_WEIGHT
                ) { it.name },
                HeaderItem(
                    "File Size",
                    FILE_SIZE_WEIGHT
                ),
                HeaderItem(
                    "Date Created",
                    DATE_WEIGHT,
                    true
                ) { it.dateAdded },
                HeaderItem(
                    "Progress",
                    PROGRESS_WEIGHT
                ) { it.downloadProgress.value }
            ),
            Core.child.downloadList,
            key = { it.nameAndResolution() },
            lazyListState = lazyListState,
            endingComparator = Tools.downloadProgressComparator
        ) { _, item ->
            DownloadRow(
                item,
                windowScope
            )
        }
        LaunchedEffect(Core.child.downloadList.size) {
            if (!deletedFile) {
                lazyListState.animateScrollToItem(0)
            } else {
                deletedFile = false
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun DownloadRow(
        download: Download,
        windowScope: AppWindowScope
    ) {
        var showFileMenu by remember {
            mutableStateOf(false)
        }
        val options = mutableListOf<DropdownOption>()

        if (!download.isComplete) {
            if (!download.downloading) {
                options.add(
                    DropdownOption(
                        "Resume",
                        EvaIcons.Fill.Download
                    ) {
                        Core.openSeriesDetails(
                            download.slug,
                            windowScope
                        )
                    }
                )
            }
        }
        options.add(
            DropdownOption(
                "Series Details",
                EvaIcons.Fill.Info
            ) {
                Core.openSeriesDetails(
                    download.seriesSlug,
                    windowScope
                )
            }
        )
        options.add(
            DropdownOption(
                "Open Folder",
                EvaIcons.Fill.Folder
            ) {
                Tools.openFile(
                    download.downloadPath,
                    true
                )
            }
        )

        if (download.isComplete) {
            options.add(
                DropdownOption(
                    "Play Video",
                    EvaIcons.Fill.Video
                ) {
                    Tools.openFile(download.downloadPath)
                }
            )
        }
        if (!download.downloading) {
            options.add(
                DropdownOption(
                    "Remove From Downloads",
                    EvaIcons.Fill.Trash
                ) {
                    if (download.downloading || download.queued) {
                        windowScope.showToast("You can't remove a download that's downloading.")
                    } else {
                        deletedFile = true
                        Core.child.removeDownload(download)
                    }
                }
            )
            options.add(
                DropdownOption(
                    "Delete File",
                    EvaIcons.Fill.FileRemove
                ) {
                    if (download.downloading || download.queued) {
                        windowScope.showToast("You can't delete a file that's downloading.")
                    } else {
                        DialogHelper.showConfirm(
                            """
                                Are you sure you want to delete the video:
                                
                                ${download.name}?
                            """.trimIndent(),
                            "Delete File And Remove",
                            onConfirmTitle = "Delete File",
                            size = DpSize(340.dp, 250.dp)
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
                                deletedFile = true
                                Core.child.removeDownload(download)
                                windowScope.showToast("This file doesn't exist.")
                            }
                        }
                    }
                }
            )
        }

        DefaultCursorDropdownMenu(
            showFileMenu,
            options,
            onDismissRequest = { showFileMenu = false }
        )

        Row(
            modifier = Modifier
                .multiClickable(
                    indication = ripple(
                        color = MaterialTheme.colorScheme.secondaryContainer.hover()
                    ),
                    onSecondaryClick = {
                        showFileMenu = showFileMenu.not()
                    }
                ) {
                    showFileMenu = showFileMenu.not()
                }
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(5.dp)
                ).height(rowHeight).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = download.seriesName,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.CenterVertically)
                    .weight(SERIES_NAME_WEIGHT)
            )
            Divider()
            Text(
                text = download.nameAndResolution(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.CenterVertically)
                    .weight(NAME_WEIGHT)
            )
            Divider()
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
            Divider()
            Text(
                text = Tools.dateAndTimeFormatted(download.dateAdded),
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.CenterVertically)
                    .weight(DATE_WEIGHT),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                textAlign = TextAlign.Center
            )
            Divider()
            val time = if (download.downloading)
                Tools.secondsToRemainingTime(
                    download.downloadSeconds.value,
                    true
                )
            else ""
            val text = download.downloadProgress.value + " $time"
            Text(
                text = text,
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.CenterVertically)
                    .weight(PROGRESS_WEIGHT),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }

    @Composable
    private fun Divider() {
        VerticalDivider(
            modifier = Modifier.fillMaxHeight()
                .width(1.dp)
                .background(
                    MaterialTheme.colorScheme.onSurfaceVariant
                ),
            color = Color.Transparent
        )
    }

    override fun onClose() {

    }

    companion object {
        private val rowHeight = 85.dp
        private const val SERIES_NAME_WEIGHT = 0.7f
        private const val NAME_WEIGHT = 4f
        private const val FILE_SIZE_WEIGHT = 1f
        private const val DATE_WEIGHT = 1.5f
        private const val PROGRESS_WEIGHT = 1.5f
    }

}