package nobility.downloader.ui.views

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.fill.*
import nobility.downloader.Page
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.Core
import nobility.downloader.core.settings.Defaults
import nobility.downloader.core.settings.Save
import nobility.downloader.ui.components.*
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.ui.components.dialog.DialogHelper.smallWindowSize
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.Tools

class DownloadsView : ViewPage {

    override val page = Page.DOWNLOADS
    private var deletedFile = false //used so it doesn't scroll to top on deletion (hacky)

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun Ui(windowScope: AppWindowScope) {
        val lazyListState = rememberLazyListState()

        LazyTable(
            listOf(
                ColumnItem(
                    "Series",
                    1.5f,
                    sortSelector = { it.seriesName },
                    weightSaveKey = Save.DV_S_WEIGHT
                ) { _, download ->
                    Text(
                        text = download.seriesName,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.multiClickable {
                            Core.openSeriesDetails(
                                download.seriesSlug,
                                windowScope
                            )
                        }.pointerHoverIcon(PointerIcon.Hand)
                    )
                },
                ColumnItem(
                    "Name",
                    3f,
                    contentAlignment = Alignment.CenterStart,
                    weightSaveKey = Save.DV_N_WEIGHT,
                    sortSelector = { it.name }
                ) { _, download ->
                    Text(
                        text = download.nameAndResolution(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                ColumnItem(
                    "File Size",
                    1f,
                    weightSaveKey = Save.DV_F_WEIGHT,
                    sortSelector = { it.fileSize }
                ) { _, download ->
                    Text(
                        text = Tools.bytesToString(download.fileSize),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        textAlign = TextAlign.Center
                    )
                },
                ColumnItem(
                    "Date Created",
                    1.5f,
                    defaultSort = true to true,
                    weightSaveKey = Save.DV_DC_WEIGHT,
                    sortSelector = { it.dateAdded }
                ) { _, download ->
                    Text(
                        text = Tools.dateAndTimeFormatted(download.dateAdded, false),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        textAlign = TextAlign.Center
                    )
                },
                ColumnItem(
                    "Progress",
                    1.5f,
                    weightSaveKey = Save.DV_P_WEIGHT,
                    sortSelector = { it.downloadProgress.value }
                ) { _, download ->
                    val time = if (download.downloading)
                        Tools.secondsToRemainingTime(
                            download.downloadSeconds.value,
                            true
                        )
                    else ""
                    val text = download.downloadProgress.value + time
                    Text(
                        text = text,
                        modifier = Modifier
                            .padding(4.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            ),
            Core.child.downloadList,
            key = { it.nameAndResolution() + it.hashCode() },
            lazyListState = lazyListState,
            endingComparator = Tools.downloadProgressComparator,
            rowHeight = 65.dp,
            sortSaveKey = Save.DV_SORT,
            rightClickOptions = { _, download ->
                listOf(
                    DropdownOption(
                        "Resume",
                        EvaIcons.Fill.Download,
                        !download.isComplete && !download.downloading
                    ) {
                        Core.openSeriesDetails(
                            download.slug,
                            windowScope
                        )
                    },
                    DropdownOption(
                        "Series Details",
                        EvaIcons.Fill.Info
                    ) {
                        Core.openSeriesDetails(
                            download.seriesSlug,
                            windowScope
                        )
                    },
                    DropdownOption(
                        "Open Folder",
                        EvaIcons.Fill.Folder
                    ) {
                        Tools.openFile(
                            download.downloadPath,
                            true
                        )
                    },
                    DropdownOption(
                        "Play Video",
                        EvaIcons.Fill.Video,
                        download.isComplete
                    ) {
                        Tools.openFile(download.downloadPath)
                    },
                    DropdownOption(
                        "Remove From Downloads",
                        EvaIcons.Fill.Trash,
                        !download.downloading
                    ) {
                        if (download.downloading || download.queued) {
                            windowScope.showToast("You can't remove a download that's downloading.")
                        } else {
                            deletedFile = true
                            Core.child.removeDownload(download)
                        }
                    },
                    DropdownOption(
                        "Delete File",
                        EvaIcons.Fill.FileRemove,
                        visible = !download.downloading
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
        )
    }

    override val menuOptions: List<OverflowOption>
        get() = listOf(
            OverflowOption(
                EvaIcons.Fill.Sync,
                "Download Queue",
                badge = {
                    if (Core.child.downloadThread.downloadsInQueue.value > 0) {
                        Badge(
                            containerColor = Color.Red,
                            modifier = Modifier.offset(y = 2.dp, x = (-8).dp)
                        ) {
                            Text(
                                Core.child.downloadThread.downloadsInQueue.value.toString(),
                                overflow = TextOverflow.Clip,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            ) {
                Core.openDownloadQueue()
            },
            OverflowOption(
                EvaIcons.Fill.Save,
                "Downloaded Episodes History",
            ) {
                Core.openDownloadedEpisodesWindow()
            },
            OverflowOption(
                EvaIcons.Fill.Folder,
                "Open Download Folder"
            ) {
                Tools.openFile(
                    Defaults.SAVE_FOLDER.string()
                )
            },
            OverflowOption(
                EvaIcons.Fill.Close,
                "Stop Downloads",
                visible = Core.child.isRunning
            ) {
                Core.child.stop()
                ApplicationState.showToastForMain("All downloads have been stopped.")
            },
            OverflowOption(
                EvaIcons.Fill.Trash,
                "Clear All Downloads"
            ) {
                if (Core.child.downloadList.isEmpty()) {
                    return@OverflowOption
                }
                if (!Core.child.isRunning) {
                    DialogHelper.showConfirm(
                        """
                                     Are you sure you want to clear all downloads?
                                     This is just going to delete the entire download list. 
                                     No files will be deleted.
                                     This action is irreversible unless you save a backup of the database folder first.
                                   """.trimIndent(),
                        "Clear Downloads"
                    ) {
                        val size = Core.child.downloadList.size
                        Core.child.downloadList.clear()
                        BoxHelper.shared.downloadBox.removeAll()
                        DialogHelper.showMessage(
                            "Success",
                            "Deleted $size downloads.",
                            size = smallWindowSize
                        )
                    }
                } else {
                    DialogHelper.showError("You can't clear downloads while things are downloading.")
                }
            },

            OverflowOption(
                EvaIcons.Fill.Trash,
                "Clear Downloads & Delete Incomplete Files"
            ) {
                if (Core.child.downloadList.isEmpty()) {
                    return@OverflowOption
                }
                if (!Core.child.isRunning) {
                    DialogHelper.showConfirm(
                        """
                                    Are you sure you want to clear all downloads?
                                    This is going to delete the entire download list. 
                                    All found incomplete files will be deleted as well.
                                    Please note that this may potentially delete videos that are completed.
                                    It shouldn't, but that's why there's a second option.
                                    This action is irreversible unless you save a backup of the database folder first.
                                  """.trimIndent(),
                        "Clear All Downloads"
                    ) {
                        val size = Core.child.downloadList.size
                        Core.child.downloadList.forEach { download ->
                            val file = download.downloadFile()
                            if (file != null && !download.isComplete) {
                                file.delete()
                            }
                        }
                        Core.child.downloadList.clear()
                        BoxHelper.shared.downloadBox.removeAll()
                        DialogHelper.showMessage(
                            "Success",
                            "Deleted $size downloads.",
                            size = smallWindowSize
                        )
                    }
                } else {
                    DialogHelper.showError("You can't clear downloads while things are downloading.")
                }
            }
        )
}