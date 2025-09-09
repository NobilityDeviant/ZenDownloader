package nobility.downloader.ui.windows

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.common.collect.Lists
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.fill.ArrowDown
import compose.icons.evaicons.fill.ArrowUp
import kotlinx.coroutines.*
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.BoxHelper.Companion.update
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Episode
import nobility.downloader.core.entities.Series
import nobility.downloader.core.scraper.DownloadHandler
import nobility.downloader.core.scraper.SeriesUpdater
import nobility.downloader.core.settings.Defaults
import nobility.downloader.ui.components.*
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.Constants.bottomBarHeight
import nobility.downloader.utils.FrogLog

class EpisodeUpdaterWindow(
    nSeries: List<Series>,
    tag: String = ""
) {

    constructor(
        series: Series,
        tag: String = ""
    ): this(listOf(series), tag)

    private val seriesList = nSeries.distinctBy { it.name }.map {
        SeriesUpdate(it)
    }
    private val scope = CoroutineScope(Dispatchers.IO)
    private var running by mutableStateOf(false)
    private var complete by mutableStateOf(false)
    private val totalSeries by mutableStateOf(seriesList.size)
    private var checkedSeries by mutableStateOf(0)
    private var failed by mutableStateOf(0)
    private var newEpisodesFound by mutableStateOf(0)
    private var threads by mutableStateOf(Defaults.EPISODE_UPDATER_THREADS.int())
    private val windowTitle = TITLE + if (tag.isNotEmpty()) " ($tag)" else ""
    private lateinit var windowScope: AppWindowScope

    init {
        if (seriesList.isEmpty()) {
            complete = true
            checkedSeries = totalSeries
        }
    }

    fun open() {
        ApplicationState.newWindow(
            windowTitle,
            maximized = true,
            onClose = {
                if (running || newEpisodesFound > 0) {
                    DialogHelper.showConfirm(
                        if (running)
                            """
                            The process is still checking for new episodes.
                            Do you want to close the window?
                        """.trimIndent()
                        else """
                            New episodes have been found.
                            Closing this window won't allow you to download them here.
                            Do you want to close the window?
                        """.trimIndent()
                    ) {
                        running = false
                        scope.cancel()
                        ApplicationState.removeWindowWithId(
                            windowTitle,
                            false
                        )
                    }
                    return@newWindow false
                }
                running = false
                scope.cancel()
                return@newWindow true
            }
        ) {
            windowScope = this
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .height(bottomBarHeight)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                                .padding(10.dp)
                        ) {
                            Tooltip(
                                """
                                    The number of series to check at the same time.
                                    Default: ${Defaults.EPISODE_UPDATER_THREADS.value}
                                    Minimum: 1
                                    Maximum: 5
                                """.trimIndent()
                            ) {
                                Text("Threads: ")
                            }
                            DefaultSettingsTextField(
                                threads.toString(),
                                {
                                    val num = it.toIntOrNull()
                                    if (num != null) {
                                        threads = if (num > 5) {
                                            5
                                        } else {
                                            num
                                        }
                                        Defaults.EPISODE_UPDATER_THREADS.update(threads)
                                    } else {
                                        threads = 1
                                    }
                                },
                                numbersOnly = true,
                                modifier = Modifier.height(40.dp)
                                    .width(60.dp)
                            )
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                DefaultIcon(
                                    EvaIcons.Fill.ArrowUp,
                                    Modifier.size(
                                        24.dp,
                                        16.dp
                                    ).pointerHoverIcon(PointerIcon.Hand),
                                    onClick = {
                                        if (threads < 5) {
                                            threads++
                                            Defaults.EPISODE_UPDATER_THREADS.update(threads)
                                        }
                                    }
                                )

                                DefaultIcon(
                                    EvaIcons.Fill.ArrowDown,
                                    Modifier.size(
                                        24.dp,
                                        16.dp
                                    ).pointerHoverIcon(PointerIcon.Hand),
                                    onClick = {
                                        if (threads > 1) {
                                            threads--
                                            Defaults.EPISODE_UPDATER_THREADS.update(threads)
                                        }
                                    }
                                )
                            }
                            if (complete) {
                                DefaultButton(
                                    "Close",
                                    modifier = Modifier.height(40.dp)
                                        .width(200.dp)
                                ) {
                                    closeWindow()
                                }
                            } else {
                                DefaultButton(
                                    if (running) "Cancel" else "Start",
                                    modifier = Modifier.height(40.dp)
                                        .width(200.dp)
                                ) {
                                    if (running) {
                                        scope.coroutineContext.cancelChildren()
                                        running = false
                                    } else {
                                        scope.launch {
                                            start()
                                        }
                                    }
                                }
                            }
                            DefaultButton(
                                "Download New Episodes",
                                modifier = Modifier.height(40.dp)
                                    .width(200.dp),
                                enabled = !running && newEpisodesFound > 0
                            ) {
                                scope.launch {
                                    downloadNewEpisodes()
                                }
                            }
                        }
                    }
                }
            ) { padding ->

                Column(
                    Modifier.fillMaxSize()
                        .padding(padding)
                ) {

                    val lazyListState = rememberLazyListState()

                    SortedLazyColumn(
                        listOf(
                            HeaderItem(
                                "Name",
                                NAME_WEIGHT
                            ),
                            HeaderItem(
                                "New Episodes",
                                EPISODES_WEIGHT
                            ),
                            HeaderItem(
                                "Complete",
                                COMPLETE_WEIGHT
                            )
                        ),
                        items = seriesList,
                        lazyListState = lazyListState,
                        key = { it.series.name },
                        modifier = Modifier.weight(0.85f)
                            .fillMaxWidth()
                    ) { _, series ->
                        SeriesRow(series)
                    }

                    Text(
                        "$checkedSeries/$totalSeries Finished\nFailed: $failed\nNew Episodes Found: $newEpisodesFound",
                        modifier = Modifier.weight(0.15f)
                            .fillMaxWidth()
                            .padding(4.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 20.sp
                    )
                }
            }
            ApplicationState.AddToastToWindow(this)
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun SeriesRow(
        seriesUpdate: SeriesUpdate
    ) {
        Row(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(5.dp)
                )
                .border(
                    2.dp, if (seriesUpdate.newEpisodeSlugs.isNotEmpty())
                        Color.Green else Color.Transparent
                )
                .height(rowHeight)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = seriesUpdate.series.name,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 20.sp,
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.CenterVertically)
                    .weight(NAME_WEIGHT)
            )
            Divider()
            Text(
                text = seriesUpdate.newEpisodeSlugs.size.toString(),
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.CenterVertically)
                    .weight(EPISODES_WEIGHT),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            Divider()
            Text(
                text = if (seriesUpdate.complete.value) "Finished" else "Not Checked",
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.CenterVertically)
                    .weight(COMPLETE_WEIGHT),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
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

    private suspend fun start() = withContext(Dispatchers.IO) {
        if (running) {
            return@withContext
        }
        running = true
        val incomplete = seriesList.filter { !it.complete.value }
        if (incomplete.isEmpty()) {
            windowScope.showToast("There's no more series to check.")
            return@withContext
        }
        val threads = if (threads <= 5) threads else 5
        val jobs = mutableListOf<Job>()
        val split = Lists.partition(
            incomplete,
            incomplete.size / threads
        )
        split.forEach { list ->
            jobs.add(
                launch {
                    list.forEach { seriesUpdate ->
                        if (!running) {
                            return@forEach
                        }
                        val result = SeriesUpdater.getNewEpisodesAlwaysSuccess(
                            seriesUpdate.series
                        )
                        val data = result.data
                        if (data != null) {
                            if (data.updatedEpisodes.isNotEmpty()) {
                                seriesUpdate.series.updateEpisodes(data.updatedEpisodes)
                            }
                            markAsComplete(
                                seriesUpdate,
                                data.newEpisodes
                            )
                            newEpisodesFound += data.newEpisodes.size
                        } else {
                            FrogLog.error(
                                "Failed to find new episodes for: ${seriesUpdate.series.name}",
                                result.message
                            )
                            failed++
                            markAsComplete(seriesUpdate)
                        }
                        checkedSeries++
                    }
                }
            )
        }
        jobs.joinAll()
        if (checkedSeries >= totalSeries) {
            checkedSeries = totalSeries
            complete = true
            windowScope.showToast("Finished updating all series episodes.")
            if (newEpisodesFound > 0) {
                DialogHelper.showConfirm(
                    """
                        $newEpisodesFound new episode(s) have been found.
                        Do you want to download them?
                    """.trimIndent()
                ) {
                    scope.launch {
                        downloadNewEpisodes()
                    }
                }
            }
        }
        running = false
    }

    private suspend fun downloadNewEpisodes() {
        val newEpisodeSlugs = buildList {
            seriesList.forEach { series ->
                series.newEpisodeSlugs.forEach { slug ->
                    add(slug)
                }
            }
        }

        if (newEpisodeSlugs.isEmpty()) {
            windowScope.showToast("There's no new episodes to download.")
            return
        }
        val toEpisodes = buildList {
            newEpisodeSlugs.forEach {  slug ->
                val episodeResult = DownloadHandler.launchForEpisodeData(slug)
                val episodeData = episodeResult.data
                if (episodeData != null) {
                    add(episodeData)
                } else {
                    FrogLog.error(
                        "Failed to find or scrape episode from slug: $slug",
                        episodeResult.message
                    )
                }
            }
        }
        if (toEpisodes.isNotEmpty()) {
            if (!Core.child.isRunning) {
                Core.child.softStart()
                Core.child.downloadThread.addToQueue(
                    toEpisodes
                )
                Core.child.launchStopJob()
                running = false
                scope.cancel()
                ApplicationState.showToastForMain(
                    "Launched video downloader for ${toEpisodes.size} episode(s)."
                )
                windowScope.closeWindow(false)
            } else {
                val added = Core.child.downloadThread.addToQueue(
                    toEpisodes
                )
                if (added > 0) {
                    ApplicationState.showToastForMain(
                        "Added $added episode(s) to current queue."
                    )
                    windowScope.closeWindow()
                } else {
                    windowScope.showToast(
                        "No episodes have been added to current queue. They have already been added before."
                    )
                }
            }
        }
    }

    private fun markAsComplete(
        seriesUpdate: SeriesUpdate,
        newEpisodes: List<Episode> = emptyList()
    ) {
        seriesList.forEachIndexed { i, s ->
            if (s.series.name == seriesUpdate.series.name) {
                if (newEpisodes.isNotEmpty()) {
                    seriesList[i].newEpisodeSlugs.addAll(
                        newEpisodes.map {
                            it.slug
                        }
                    )
                }
                seriesList[i].complete.value = true
                return@forEachIndexed
            }
        }
    }

    companion object {

        data class SeriesUpdate(
            val series: Series,
            var newEpisodeSlugs: SnapshotStateList<String> = mutableStateListOf(),
            var complete: MutableState<Boolean> = mutableStateOf(false)
        )

        private const val TITLE = "New Episode Checker"
        private val rowHeight = 85.dp
        private const val NAME_WEIGHT = 5f
        private const val EPISODES_WEIGHT = 1f
        private const val COMPLETE_WEIGHT = 1f
    }

}