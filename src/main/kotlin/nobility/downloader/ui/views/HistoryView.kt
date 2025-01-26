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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.fill.ArrowIosDownward
import compose.icons.evaicons.fill.ArrowIosUpward
import compose.icons.evaicons.fill.Info
import compose.icons.evaicons.fill.Search
import kotlinx.coroutines.*
import nobility.downloader.Page
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Series
import nobility.downloader.core.scraper.SeriesUpdater
import nobility.downloader.core.scraper.data.ToDownload
import nobility.downloader.ui.components.*
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.utils.*
import nobility.downloader.utils.Constants.bottomBarHeight

class HistoryView: ViewPage {

    override val page = Page.HISTORY

    private val downloadScope = CoroutineScope(Dispatchers.IO)
    private var sort by mutableStateOf(Sort.DATE_DESC)
    private var checkForEpisodesButtonEnabled = mutableStateOf(true)
    private var clearHistoryEnabled = mutableStateOf(true)
    private var loading by mutableStateOf(false)

    data class SeriesData(
        val slug: String,
        val name: String,
        val dateAdded: Long,
        val episodeCount: Int,
        val imageLink: String
    )

    private val historyData = mutableStateListOf<SeriesData>()

    private val sortedHistoryData: List<SeriesData>
        get() {
            return when (sort) {
                Sort.NAME -> historyData.sortedBy { it.name }
                Sort.NAME_DESC -> historyData.sortedByDescending { it.name }
                Sort.DATE -> historyData.sortedBy { it.dateAdded }
                Sort.DATE_DESC -> historyData.sortedByDescending { it.dateAdded }
                Sort.EPISODES -> historyData.sortedBy { it.episodeCount }
                Sort.EPISODES_DESC -> historyData.sortedByDescending { it.episodeCount }
            }
        }

    private fun loadHistoryData() {
        BoxHelper.shared.wcoBoxStore.callInReadTx {
            BoxHelper.shared.historyBox.all.forEach {
                val series = BoxHelper.seriesForSlug(it.seriesSlug)
                if (series != null) {
                    downloadScope.launch {
                        ImageUtils.downloadSeriesImage(series)
                    }
                    historyData.add(
                        SeriesData(
                            series.slug,
                            series.name,
                            it.dateAdded,
                            series.episodesSize,
                            series.imageLink
                        )
                    )
                }
            }
        }
    }

    override fun onClose() {
        historyData.clear()
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
                                if (historyData.isEmpty()) {
                                    windowScope.showToast("There's no history to clear.")
                                    return@showConfirm
                                }
                                BoxHelper.shared.historyBox.removeAll()
                                historyData.clear()
                                windowScope.showToast("History successfully cleared.")
                            }
                        }
                        defaultButton(
                            "Check For New Episodes",
                            height = 40.dp,
                            width = 150.dp,
                            enabled = checkForEpisodesButtonEnabled
                        ) {
                            if (historyData.isEmpty()) {
                                windowScope.showToast("There's no history to check.")
                                return@defaultButton
                            }
                            scope.launch {
                                windowScope.showToast("Checking ${historyData.size} series for new episodes...")
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
                            sortedHistoryData,
                            key = { it.slug }
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
            //windowScope.showToast("Loading series data...")
            //delay(2000)
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
                val slug = seriesData.slug
                if (slug.isNotEmpty()) {
                    val series = BoxHelper.seriesForSlug(slug)
                    if (series != null) {
                        Core.openDownloadConfirm(
                            ToDownload(series)
                        )
                    } else {
                        windowScope.showToast("Failed to find local series.")
                    }
                } else {
                    windowScope.showToast("There is no series slug for this download.")
                }
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
                            "Found ${result.data} new episode(s) for: ${seriesData.name}"
                        )
                    } else {
                        windowScope.showToast("No new episodes were found for: ${seriesData.name}")
                        FrogLog.logError(
                            "Failed to find new episodes for ${seriesData.name}",
                            result.message
                        )
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
                    indication = rememberRipple(
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
            Text(
                text = seriesData.name,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.CenterVertically)
                    .weight(NAME_WEIGHT)
            )
            divider()
            Text(
                text = Tools.dateFormatted(seriesData.dateAdded),
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
                text = seriesData.episodeCount.toString(),
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.CenterVertically)
                    .weight(EPISODES_WEIGHT),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                textAlign = TextAlign.Center
            )
            divider()
            val imagePath = BoxHelper.seriesImagesPath + Tools.titleForImages(seriesData.name)
            Image(
                bitmap = ImageUtils.loadImageFromFileWithBackup(
                    imagePath,
                    seriesData.imageLink
                ),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
                    .padding(10.dp)
                    .align(Alignment.CenterVertically)
                    .weight(IMAGE_WEIGHT)
                    .onClick {
                        DialogHelper.showLinkPrompt(
                            seriesData.imageLink,
                            true
                        )
                    }.pointerHoverIcon(PointerIcon.Hand)
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
        val series = BoxHelper.seriesForSlug(seriesData.slug)
            ?: return@withContext Resource.Error("Failed to find local series.")
        val result = SeriesUpdater.checkForNewEpisodes(series)
        if (result.data != null) {
            val updatedEpisodes = result.data.updatedEpisodes
            val seriesWco = BoxHelper.seriesForSlug(series.slug)
            seriesWco?.updateEpisodes(updatedEpisodes)
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
            val result = SeriesUpdater.checkForNewEpisodes(series)
            if (result.data != null) {
                seriesUpdated++
                episodesUpdated += result.data.newEpisodes.size
                val updatedEpisodes = result.data.updatedEpisodes
                val seriesWco = BoxHelper.seriesForSlug(series.slug)
                seriesWco?.updateEpisodes(updatedEpisodes)
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