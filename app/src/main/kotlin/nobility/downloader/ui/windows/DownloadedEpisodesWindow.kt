package nobility.downloader.ui.windows

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.sp
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.fill.Folder
import compose.icons.evaicons.fill.Info
import compose.icons.evaicons.fill.Trash
import kotlinx.coroutines.*
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.BoxMaker
import nobility.downloader.core.Core
import nobility.downloader.core.entities.DownloadedEpisode
import nobility.downloader.core.entities.Episode
import nobility.downloader.core.settings.Defaults
import nobility.downloader.ui.components.*
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.ui.components.dialog.DialogHelper.smallWindowSize
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.Constants.bottomBarHeight
import nobility.downloader.utils.Tools
import nobility.downloader.utils.hover
import java.io.File

class DownloadedEpisodesWindow(
    private val initialEpisodeSlug: String = ""
) {

    private val downloadedEpisodes = mutableStateListOf<EpisodeHistory>()
    private val scope = CoroutineScope(Dispatchers.Default)
    private var loading by mutableStateOf(true)
    private var updating by mutableStateOf(false)
    private var mInitialEpisodeSlug by mutableStateOf(initialEpisodeSlug)

    private fun loadData() {
        BoxHelper.shared.downloadedEpisodeBoxStore.callInReadTx {
            BoxHelper.shared.downloadedEpisodeBox.all.map {
                val episode = BoxHelper.episodeForSlug(it.episodeSlug)
                if (episode != null) {
                    downloadedEpisodes.add(EpisodeHistory(it, episode))
                }
            }
            loading = false
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    fun open() {
        ApplicationState.newWindow(
            "Downloaded Episodes History",
            onClose = {
                scope.cancel()
                return@newWindow true
            }
        ) {
            val lazyListState = rememberLazyListState()
            val composeScope = rememberCoroutineScope()

            Scaffold(
                Modifier.fillMaxSize(),
                bottomBar = {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .height(bottomBarHeight)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                                .padding(8.dp)
                        ) {
                            DefaultButton(
                                "Clear All",
                                modifier = Modifier.height(40.dp)
                                    .width(200.dp)
                            ) {
                                DialogHelper.showConfirm(
                                    "Are you sure you want to delete your downloaded episodes history?",
                                    onConfirmTitle = "Clear All",
                                    size = smallWindowSize
                                ) {
                                    if (downloadedEpisodes.isEmpty()) {
                                        showToast("There's nothing to clear.")
                                        return@showConfirm
                                    }
                                    BoxHelper.shared.downloadedEpisodeBox.removeAll()
                                    downloadedEpisodes.clear()
                                    mInitialEpisodeSlug = ""
                                    showToast("Downloaded Episodes History successfully cleared.")
                                }
                            }
                            DefaultButton(
                                "Check For Downloads",
                                modifier = Modifier.height(40.dp)
                                    .width(200.dp),
                                enabled = !updating
                            ) {
                                showToast("Checking Download Folder For Episodes")
                                mInitialEpisodeSlug = ""
                                //using a coroutine scope to avoid ui lag and to not have to worry about objectbox txs
                                scope.launch {
                                    updating = true
                                    var updated = 0
                                    val downloadFolder = File(Defaults.SAVE_FOLDER.string())
                                    if (downloadFolder.exists()) {
                                        downloadFolder.walk().forEach {
                                            if (!it.isDirectory) {
                                                //remove all extras it adds when saving the file
                                                val name = it.nameWithoutExtension
                                                    .replace(Regex("\\(\\d+p\\)", RegexOption.IGNORE_CASE), "")
                                                    .replace(Regex("(?<=Episode )0([1-9])", RegexOption.IGNORE_CASE), "$1")
                                                    .trim()
                                                val episode = BoxHelper.episodeForName(name)
                                                if (episode != null) {
                                                    val downloaded = BoxHelper.isDownloadedEpisode(episode)
                                                    if (downloaded == null) {
                                                        val new = BoxMaker.makeDownloadedEpisode(
                                                            episode.slug
                                                        )
                                                        updated++
                                                        downloadedEpisodes.add(
                                                            EpisodeHistory(
                                                                new, episode
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        if (updated > 0) {
                                            composeScope.launch {
                                                lazyListState.animateScrollToItem(0)
                                            }
                                            DialogHelper.showMessage(
                                                "Success",
                                                "Successfully found and updated $updated downloaded episode(s).",
                                                smallWindowSize
                                            )
                                        } else {
                                            DialogHelper.showMessage(
                                                "Failed",
                                                "There's no new downloads to update.",
                                                smallWindowSize
                                            )
                                        }
                                    } else {
                                        showToast("Your download folder doesn't exist.")
                                        Core.openSettings()

                                    }
                                    updating = false
                                }
                            }
                        }
                    }

                }
            ) { paddingValues ->
                if (loading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(80.dp)
                        )
                    }
                } else {
                    SortedLazyColumn(
                        listOf(
                            HeaderItem(
                                "Name",
                                NAME_WEIGHT
                            ) {
                                it.episode.name
                            },
                            HeaderItem(
                                "Downloaded Date",
                                DATE_WEIGHT,
                                true
                            ) {
                                it.downloadedEpisode.downloadedDate
                            }
                        ),
                        downloadedEpisodes,
                        key = { it.downloadedEpisode.episodeSlug + it.downloadedEpisode.id },
                        modifier = Modifier.padding(paddingValues),
                        lazyListState = lazyListState,
                        scrollToPredicate = {
                            it.downloadedEpisode.episodeSlug == initialEpisodeSlug
                        }
                    ) { i, episode ->
                        EpisodeRow(episode, this)
                    }
                }
                LaunchedEffect(Unit) {
                    delay(1000)
                    loading = true
                    loadData()
                    loading = false
                }
            }
            ApplicationState.AddToastToWindow(this)
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun EpisodeRow(
        episode: EpisodeHistory,
        windowScope: AppWindowScope
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
                        episode.episode.seriesSlug,
                        windowScope
                    )
                },
                DropdownOption(
                    "Open Episode Folder",
                    EvaIcons.Fill.Folder
                ) {
                    windowScope.showToast(
                        "Checking files for episode"
                    )
                    scope.launch {
                        val downloadFolder = File(Defaults.SAVE_FOLDER.string())
                        if (downloadFolder.exists()) {
                            var opened = false
                            downloadFolder.walk().forEach {
                                if (!it.isDirectory) {
                                    val name = it.nameWithoutExtension
                                        .replace(Regex("\\(\\d+p\\)", RegexOption.IGNORE_CASE), "")
                                        .replace(Regex("(?<=Episode )0([1-9])", RegexOption.IGNORE_CASE), "$1")
                                        .trim()
                                    if (episode.episode.name.equals(name, true)) {
                                        Tools.openFile(it.absolutePath, true)
                                        opened = true
                                        return@forEach
                                    }
                                }
                            }
                            if (!opened) {
                                windowScope.showToast(
                                    "Failed to find episode in your download folder."
                                )
                            }
                        } else {
                            windowScope.showToast("Your download folder doesn't exist.")
                        }
                    }
                },
                DropdownOption(
                    "Remove",
                    EvaIcons.Fill.Trash
                ) {
                    DialogHelper.showConfirm(
                        "Are you sure you want to remove this from your Downloaded Episode History?",
                        size = smallWindowSize
                    ) {
                        BoxHelper.removeDownloadedEpisode(episode.downloadedEpisode)
                        downloadedEpisodes.remove(episode)
                        mInitialEpisodeSlug = ""
                    }
                }
            )
        ) { showFileMenu = false }

        Row(
            modifier = Modifier
                .multiClickable(
                    indication = ripple(
                        color = MaterialTheme.colorScheme
                            .secondaryContainer.hover()
                    ),
                    onSecondaryClick = {
                        showFileMenu = showFileMenu.not()
                    }
                ) {
                    showFileMenu = showFileMenu.not()
                }
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(4.dp)
                ).height(rowHeight).fillMaxWidth()
                .border(
                    1.dp,
                    if (episode.downloadedEpisode.episodeSlug == mInitialEpisodeSlug)
                        Color.Green else Color.Transparent
                ),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = episode.episode.name,
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.CenterVertically)
                    .weight(NAME_WEIGHT),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
            Divider()
            Text(
                text = Tools.dateAndTimeFormatted(episode.downloadedEpisode.downloadedDate),
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.CenterVertically)
                    .weight(DATE_WEIGHT),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }

    companion object {
        private val rowHeight = 60.dp
        private const val NAME_WEIGHT = 5f
        private const val DATE_WEIGHT = 1.5f

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

        data class EpisodeHistory(
            val downloadedEpisode: DownloadedEpisode,
            val episode: Episode
        )
    }
}