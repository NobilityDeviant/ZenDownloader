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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import nobility.downloader.core.entities.Series
import nobility.downloader.core.scraper.SeriesUpdater
import nobility.downloader.ui.components.DefaultButton
import nobility.downloader.ui.components.HeaderItem
import nobility.downloader.ui.components.SortedLazyColumn
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.Constants.bottomBarHeight

class EpisodeUpdaterWindow(
    nSeries: List<Series>,
    tag: String = ""
) {

    private val seriesList = nSeries.distinctBy { it.name }.map {
        SeriesUpdate(it)
    }
    private val scope = CoroutineScope(Dispatchers.IO)
    private var running by mutableStateOf(false)
    private var complete by mutableStateOf(false)
    private val total by mutableStateOf(seriesList.size)
    private var finished by mutableStateOf(0)
    private var newEpisodesFound by mutableStateOf(0)
    private val windowTitle = TITLE + if (tag.isNotEmpty()) " ($tag)" else ""
    private lateinit var windowScope: AppWindowScope

    init {
        if (seriesList.isEmpty()) {
            complete = true
            finished = total
        } else {
            scope.launch {
                start()
            }
        }
    }

    fun open() {
        ApplicationState.newWindow(
            windowTitle,
            maximized = true,
            onClose = {
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
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                                .padding(10.dp)
                        ) {
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
                        "$finished/$total Finished\nNew Episodes Found: $newEpisodesFound",
                        modifier = Modifier.weight(0.15f)
                            .fillMaxWidth()
                            .padding(4.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 20.sp
                    )

                    //add setting to auto close?
                    //LaunchedEffect(finished) {

                    //}

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
                    2.dp, if (seriesUpdate.episodeSize.value > 0)
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
                text = seriesUpdate.episodeSize.value.toString(),
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

    private suspend fun start() {
        if (running) {
            return
        }
        running = true
        val incomplete = seriesList.filter { !it.complete.value }
        if (incomplete.isEmpty()) {
            windowScope.showToast("There's no more series to check.")
            return
        }
        incomplete.forEach { seriesUpdate ->
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
                    data.newEpisodes.size
                )
                newEpisodesFound += data.newEpisodes.size
            }
            finished++
        }
        if (finished >= total) {
            finished = total
            complete = true
            windowScope.showToast("Finished updating all series episodes.")
        }
        running = false
    }

    private fun markAsComplete(
        seriesUpdate: SeriesUpdate,
        newEpisodesSize: Int
    ) {
        seriesList.forEachIndexed { i, s ->
            if (s.series.name == seriesUpdate.series.name) {
                seriesList[i].episodeSize.value = newEpisodesSize
                seriesList[i].complete.value = true
                return@forEachIndexed
            }
        }
    }

    companion object {

        data class SeriesUpdate(
            val series: Series,
            var episodeSize: MutableState<Int> = mutableStateOf(0),
            var complete: MutableState<Boolean> = mutableStateOf(false)
        )

        private const val TITLE = "New Episode Checker"
        private val rowHeight = 85.dp
        private const val NAME_WEIGHT = 5f
        private const val EPISODES_WEIGHT = 1f
        private const val COMPLETE_WEIGHT = 1f
    }

}