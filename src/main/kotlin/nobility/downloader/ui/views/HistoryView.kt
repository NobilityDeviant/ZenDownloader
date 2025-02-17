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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.Outline
import compose.icons.evaicons.fill.*
import compose.icons.evaicons.outline.Star
import kotlinx.coroutines.*
import nobility.downloader.Page
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.BoxMaker
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Series
import nobility.downloader.core.entities.SeriesHistory
import nobility.downloader.core.scraper.SeriesUpdater
import nobility.downloader.core.scraper.data.ToDownload
import nobility.downloader.ui.components.*
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.utils.Constants.bottomBarHeight
import nobility.downloader.utils.FrogLog
import nobility.downloader.utils.Resource
import nobility.downloader.utils.Tools
import nobility.downloader.utils.hover

class HistoryView : ViewPage {

    override val page = Page.HISTORY

    private val downloadScope = CoroutineScope(Dispatchers.IO)
    private var sort by mutableStateOf(Sort.DATE_DESC)
    private var checkForEpisodesButtonEnabled = mutableStateOf(true)
    private var clearHistoryEnabled = mutableStateOf(true)
    private var loading by mutableStateOf(false)

    data class SeriesData(
        val series: Series,
        val history: SeriesHistory
    )

    private val seriesDatas = mutableStateListOf<SeriesData>()

    private val sortedSeriesDataData: List<SeriesData>
        get() {
            return when (sort) {
                Sort.NAME -> seriesDatas.sortedBy { it.series.name }
                Sort.NAME_DESC -> seriesDatas.sortedByDescending { it.series.name }
                Sort.DATE -> seriesDatas.sortedBy { it.history.dateAdded }
                Sort.DATE_DESC -> seriesDatas.sortedByDescending { it.history.dateAdded }
                Sort.EPISODES -> seriesDatas.sortedBy { it.series.episodesSize }
                Sort.EPISODES_DESC -> seriesDatas.sortedByDescending { it.series.episodesSize }
            }
        }

    private fun loadHistoryData() {
        //todo use this more often
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
    override fun ui(windowScope: AppWindowScope) {
        val scope = rememberCoroutineScope()
        val seasonsListState = rememberLazyListState()
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
                        defaultButton(
                            "Clear History",
                            height = 40.dp,
                            width = 150.dp,
                            enabled = clearHistoryEnabled
                        ) {
                            DialogHelper.showConfirm(
                                "Are you sure you would like to delete your download history?",
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
                        defaultButton(
                            "Check All For New Episodes",
                            height = 40.dp,
                            width = 150.dp,
                            enabled = checkForEpisodesButtonEnabled
                        ) {
                            if (seriesDatas.isEmpty()) {
                                windowScope.showToast("There's no history to check.")
                                return@defaultButton
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
            }

            Column(
                modifier = Modifier.padding(
                    bottom = padding.calculateBottomPadding()
                ).fillMaxSize()
            ) {
                header()
                FullBox {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(
                            top = 5.dp,
                            bottom = 5.dp,
                            end = verticalScrollbarEndPadding
                        ).fillMaxSize().draggable(
                            state = rememberDraggableState {
                                scope.launch {
                                    seasonsListState.scrollBy(-it)
                                }
                            },
                            orientation = Orientation.Vertical,
                        ),
                        state = seasonsListState
                    ) {
                        items(
                            sortedSeriesDataData,
                            key = { it.series.slug + it.series.id }
                        ) {
                            seriesDataRow(
                                it,
                                windowScope,
                                scope
                            )
                        }
                    }
                    verticalScrollbar(seasonsListState)
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
    private fun header() {
        Row(
            modifier = Modifier.background(
                color = MaterialTheme.colorScheme.inversePrimary,
                shape = RectangleShape
            ).height(40.dp).fillMaxWidth().padding(end = verticalScrollbarEndPadding),
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
                        text = "Series Name",
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
                        text = "Date Added",
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
                modifier = Modifier.weight(EPISODES_WEIGHT)
                    .align(Alignment.CenterVertically).onClick {
                        sort = when (sort) {
                            Sort.EPISODES -> {
                                Sort.EPISODES_DESC
                            }

                            Sort.EPISODES_DESC -> {
                                Sort.EPISODES
                            }

                            else -> {
                                Sort.EPISODES_DESC
                            }
                        }
                    }
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spaceBetweenNameAndIcon),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Episodes",
                        modifier = Modifier
                            .padding(4.dp)
                            .align(Alignment.CenterVertically),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        textAlign = TextAlign.Center
                    )
                    if (sort == Sort.EPISODES || sort == Sort.EPISODES_DESC) {
                        Icon(
                            if (sort == Sort.EPISODES_DESC)
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
                text = "Image",
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.CenterVertically)
                    .weight(IMAGE_WEIGHT),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                textAlign = TextAlign.Center
            )
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun seriesDataRow(
        seriesData: SeriesData,
        windowScope: AppWindowScope,
        coroutineSeries: CoroutineScope
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
            defaultDropdownItem(
                "Series Details",
                EvaIcons.Fill.Info
            ) {
                closeMenu()
                Core.openDownloadConfirm(
                    ToDownload(seriesData.series)
                )
            }
            defaultDropdownItem(
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
                            FrogLog.logError(
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
            defaultDropdownItem(
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

            defaultDropdownItem(
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
                    defaultIcon(
                        EvaIcons.Fill.Star,
                        iconColor = Color.Yellow,
                        iconModifier = Modifier
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
            divider()
            Text(
                text = Tools.dateFormatted(seriesData.history.dateAdded),
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
                text = seriesData.series.episodesSize.toString(),
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.CenterVertically)
                    .weight(EPISODES_WEIGHT),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                textAlign = TextAlign.Center
            )
            divider()
            defaultImage(
                seriesData.series.imagePath,
                seriesData.series.imageLink,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
                    .padding(10.dp)
                    .align(Alignment.CenterVertically)
                    .weight(IMAGE_WEIGHT)
            )
        }
    }

    @Composable
    private fun divider(
        header: Boolean = false
    ) {
        VerticalDivider(
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
        return@withContext if (result.data != null) {
            val updatedEpisodes = result.data.updatedEpisodes
            series.updateEpisodes(updatedEpisodes)
            clearHistoryEnabled.value = true
            checkForEpisodesButtonEnabled.value = true
            Resource.Success(result.data.newEpisodes.size)
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
            if (result.data != null) {
                seriesUpdated++
                episodesUpdated += result.data.newEpisodes.size
                val updatedEpisodes = result.data.updatedEpisodes
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

    private enum class Sort {
        NAME,
        NAME_DESC,
        DATE,
        DATE_DESC,
        EPISODES,
        EPISODES_DESC
    }

    companion object {
        private val spaceBetweenNameAndIcon = 1.dp
        private val rowHeight = 130.dp
        private const val NAME_WEIGHT = 5f
        private const val EPISODES_WEIGHT = 1f
        private const val DATE_WEIGHT = 1.1f
        private const val IMAGE_WEIGHT = 1.9f
    }
}