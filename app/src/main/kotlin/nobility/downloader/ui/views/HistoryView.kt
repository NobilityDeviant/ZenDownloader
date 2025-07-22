package nobility.downloader.ui.views

import Resource
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.Outline
import compose.icons.evaicons.fill.Info
import compose.icons.evaicons.fill.Search
import compose.icons.evaicons.fill.Star
import compose.icons.evaicons.fill.Trash
import compose.icons.evaicons.outline.Star
import kotlinx.coroutines.*
import nobility.downloader.Page
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.BoxMaker
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Series
import nobility.downloader.core.entities.SeriesHistory
import nobility.downloader.core.scraper.SeriesUpdater
import nobility.downloader.ui.components.*
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.utils.Constants.bottomBarHeight
import nobility.downloader.utils.FrogLog
import nobility.downloader.utils.Tools
import nobility.downloader.utils.hover

class HistoryView : ViewPage {

    override val page = Page.HISTORY

    private val downloadScope = CoroutineScope(Dispatchers.IO)
    private var checkForEpisodesButtonEnabled = mutableStateOf(true)
    private var clearHistoryEnabled = mutableStateOf(true)
    private var loading by mutableStateOf(false)

    data class SeriesData(
        val series: Series,
        val history: SeriesHistory
    )

    private val seriesDatas = mutableStateListOf<SeriesData>()

    private fun loadHistoryData() {
        BoxHelper.shared.wcoBoxStore.callInReadTx {
            BoxHelper.shared.historyBox.all.forEach {
                val series = BoxHelper.seriesForSlug(it.seriesSlug)
                if (series != null) {
                    seriesDatas.add(
                        SeriesData(
                            series,
                            it
                        )
                    )
                }
            }
        }
        BoxHelper.shared.wcoBoxStore.closeThreadResources()
    }

    override fun onClose() {
        seriesDatas.clear()
        downloadScope.cancel()
    }

    @Composable
    override fun Ui(windowScope: AppWindowScope) {
        val scope = rememberCoroutineScope()
        Scaffold(
            modifier = Modifier.fillMaxSize(50f),
            bottomBar = {
                Column(
                    modifier = Modifier.fillMaxWidth().height(bottomBarHeight)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                            .padding(10.dp)
                    ) {
                        DefaultButton(
                            "Clear History",
                            height = 40.dp,
                            width = 200.dp,
                            enabled = clearHistoryEnabled
                        ) {
                            DialogHelper.showConfirm(
                                "Are you sure you want to delete your download history?",
                                onConfirmTitle = "Clear History"
                            ) {
                                if (seriesDatas.isEmpty()) {
                                    windowScope.showToast("There's no history to clear.")
                                    return@showConfirm
                                }
                                BoxHelper.shared.historyBox.removeAll()
                                seriesDatas.clear()
                                windowScope.showToast("History successfully cleared.")
                            }
                        }
                        //todo add a progress bar
                        DefaultButton(
                            "Check All For New Episodes",
                            height = 40.dp,
                            width = 200.dp,
                            enabled = checkForEpisodesButtonEnabled
                        ) {
                            if (seriesDatas.isEmpty()) {
                                windowScope.showToast("There's no history to check.")
                                return@DefaultButton
                            }
                            scope.launch {
                                windowScope.showToast("Checking ${seriesDatas.size} series for new episodes...")
                                val result = checkAllHistoryForNewEpisodes()
                                val data = result.data
                                if (data != null) {
                                    if (data.episodesUpdated > 0 && data.seriesUpdated > 0) {
                                        DialogHelper.showMessage(
                                            "Success",
                                            "Successfully updated ${data.seriesUpdated} series and found ${data.episodesUpdated} new episodes."
                                        )
                                    } else {
                                        windowScope.showToast("No new episodes were found.")
                                    }
                                } else {
                                    DialogHelper.showError(
                                        "Failed to check for new episodes.",
                                        result.message
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { padding ->
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
                val lazyListState = rememberLazyListState()
                val fastScrolling = rememberScrollSpeed(lazyListState)
                SortedLazyColumn<SeriesData>(
                    listOf(
                        HeaderItem(
                            "Name",
                            NAME_WEIGHT
                        ) {
                            it.series.name
                        },
                        HeaderItem(
                            "Date Added",
                            DATE_WEIGHT,
                            true
                        ) {
                            it.history.dateAdded
                        },
                        HeaderItem(
                            "Episodes",
                            EPISODES_WEIGHT
                        ) {
                            it.series.episodesSize
                        },
                        HeaderItem(
                            "Image",
                            IMAGE_WEIGHT
                        )
                    ),
                    seriesDatas,
                    lazyListState = lazyListState,
                    key = { it.series.slug + it.series.id },
                    modifier = Modifier.padding(
                        bottom = padding.calculateBottomPadding()
                    ).fillMaxSize()
                ) { _, item ->
                    SeriesDataRow(
                        item,
                        windowScope,
                        scope,
                        fastScrolling.value
                    )
                }
            }
        }
        LaunchedEffect(Unit) {
            loading = true
            loadHistoryData()
            loading = false
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun SeriesDataRow(
        seriesData: SeriesData,
        windowScope: AppWindowScope,
        coroutineSeries: CoroutineScope,
        fastScrolling: Boolean
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
                    seriesData.series.slug,
                    windowScope
                )
            }
            DefaultDropdownItem(
                "Check For New Episodes",
                EvaIcons.Fill.Search
            ) {
                closeMenu()
                coroutineSeries.launch {
                    val result = checkForNewEpisodes(seriesData)
                    val data = result.data
                    if (data != null) {
                        windowScope.showToast(
                            "Found ${result.data} new episode(s) for: ${seriesData.series.name}"
                        )
                    } else {
                        windowScope.showToast("No new episodes were found for: ${seriesData.series.name}")
                        if (!result.message.isNullOrEmpty()) {
                            FrogLog.error(
                                "Failed to find new episodes for ${seriesData.series.name}",
                                result.message
                            )
                        }
                    }
                }
            }

            val favorited by remember {
                mutableStateOf(BoxHelper.isSeriesFavorited(seriesData.series))
            }
            DefaultDropdownItem(
                if (favorited)
                    "Remove From Favorite" else "Add To Favorite",
                if (favorited)
                    EvaIcons.Fill.Star else EvaIcons.Outline.Star,
                iconColor = if (favorited)
                    Color.Yellow else LocalContentColor.current
            ) {
                closeMenu()
                if (favorited) {
                    BoxHelper.removeSeriesFavorite(seriesData.series.slug)
                } else {
                    BoxMaker.makeFavorite(seriesData.series.slug)
                }
            }

            DefaultDropdownItem(
                "Remove From History",
                EvaIcons.Fill.Trash
            ) {
                closeMenu()
                BoxHelper.shared.historyBox.remove(seriesData.history)
                this@HistoryView.seriesDatas.remove(seriesData)
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
                            .secondaryContainer.hover()
                    )
                ) { showFileMenu = showFileMenu.not() }
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(5.dp)
                ).height(rowHeight).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .weight(NAME_WEIGHT)
            ) {
                val favorited = BoxHelper.isSeriesFavorited(seriesData.series)
                if (favorited) {
                    DefaultIcon(
                        EvaIcons.Fill.Star,
                        iconColor = Color.Yellow,
                        modifier = Modifier
                            .padding(start = 4.dp, top = 8.dp)
                            .align(Alignment.TopStart)
                    )
                }
                Text(
                    text = seriesData.series.name,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                    modifier = Modifier
                        .padding(4.dp)
                        .align(Alignment.CenterStart)
                )
            }
            Divider()
            Text(
                text = Tools.dateAndTimeFormatted(seriesData.history.dateAdded),
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.CenterVertically)
                    .weight(DATE_WEIGHT),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                textAlign = TextAlign.Center
            )
            Divider()
            Text(
                text = seriesData.series.episodesSize.toString(),
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.CenterVertically)
                    .weight(EPISODES_WEIGHT),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                textAlign = TextAlign.Center
            )
            Divider()
            DefaultImage(
                seriesData.series.imagePath,
                seriesData.series.imageLink,
                fastScrolling = fastScrolling,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
                    .padding(10.dp)
                    .align(Alignment.CenterVertically)
                    .weight(IMAGE_WEIGHT)
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

    data class CheckedResults(
        val seriesUpdated: Int,
        val episodesUpdated: Int
    )

    private suspend fun checkForNewEpisodes(
        seriesData: SeriesData
    ): Resource<Int> = withContext(Dispatchers.IO) {
        clearHistoryEnabled.value = false
        checkForEpisodesButtonEnabled.value = false
        val series = BoxHelper.seriesForSlug(seriesData.series.slug)
            ?: return@withContext Resource.Error("Failed to find local series.")
        val result = SeriesUpdater.getNewEpisodes(series)
        val data = result.data
        return@withContext if (data != null) {
            val updatedEpisodes = data.updatedEpisodes
            series.updateEpisodes(updatedEpisodes)
            clearHistoryEnabled.value = true
            checkForEpisodesButtonEnabled.value = true
            Resource.Success(data.newEpisodes.size)
        } else {
            clearHistoryEnabled.value = true
            checkForEpisodesButtonEnabled.value = true
            Resource.Error(result.message)
        }
    }

    private suspend fun checkAllHistoryForNewEpisodes(): Resource<CheckedResults> = withContext(Dispatchers.IO) {
        clearHistoryEnabled.value = false
        checkForEpisodesButtonEnabled.value = false
        val seriesHistory = mutableListOf<Series>()
        BoxHelper.shared.historyBox.all.forEach {
            val series = BoxHelper.seriesForSlug(it.seriesSlug)
            if (series != null) {
                seriesHistory.add(series)
            }
        }
        if (seriesHistory.isEmpty()) {
            return@withContext Resource.Error("Failed to find local series data.")
        }
        var seriesUpdated = 0
        var episodesUpdated = 0
        seriesHistory.forEach { series ->
            val result = SeriesUpdater.getNewEpisodes(series)
            val data = result.data
            if (data != null) {
                seriesUpdated++
                episodesUpdated += data.newEpisodes.size
                val updatedEpisodes = data.updatedEpisodes
                series.updateEpisodes(updatedEpisodes)
                //val seriesWco = BoxHelper.seriesForSlug(series.slug)
                //seriesWco?.updateEpisodes(updatedEpisodes)
            }
        }
        clearHistoryEnabled.value = true
        checkForEpisodesButtonEnabled.value = true
        Resource.Success(
            CheckedResults(
                seriesUpdated,
                episodesUpdated
            )
        )
    }

    companion object {
        private val rowHeight = 130.dp
        private const val NAME_WEIGHT = 5f
        private const val EPISODES_WEIGHT = 1f
        private const val DATE_WEIGHT = 1.1f
        private const val IMAGE_WEIGHT = 1.9f
    }
}