package nobility.downloader.ui.windows

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.fill.ChevronDown
import kotlinx.coroutines.*
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.BoxMaker
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Episode
import nobility.downloader.core.entities.data.SeriesIdentity
import nobility.downloader.core.scraper.SeriesUpdater
import nobility.downloader.core.scraper.data.ToDownload
import nobility.downloader.core.scraper.video_download.VideoDownloadHandler
import nobility.downloader.core.settings.Defaults
import nobility.downloader.core.settings.Quality
import nobility.downloader.ui.components.*
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.*
import nobility.downloader.utils.Constants.bottomBarHeight
import java.util.*

class DownloadConfirmWindow(
    private val toDownload: ToDownload
) {

    //series is never null
    private val series = toDownload.series!!
    private val movieMode = toDownload.isMovie || series.identity == SeriesIdentity.MOVIE.type
    private val selectedEpisodes = mutableStateListOf<Episode>()
    private val highlightedEpisodes = mutableStateListOf<Int>()
    private var allSelected = selectedEpisodes.isNotEmpty()
    private var selectText by mutableStateOf(if (allSelected) "Deselect All" else "Select All")
    private var downloadButtonEnabled = mutableStateOf(true)
    private var checkForEpisodesButtonEnabled = mutableStateOf(true)
    private var temporaryQuality by mutableStateOf(
        Quality.qualityForTag(Defaults.QUALITY.string())
    )
    private var singleEpisode by mutableStateOf(false)
    private var shiftHeld by mutableStateOf(false)
    private var searchText by mutableStateOf("")

    init {
        if (toDownload.episode != null) {
            selectedEpisodes.add(toDownload.episode)
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun seriesInfoHeader(
        scope: AppWindowScope,
        coroutineScope: CoroutineScope
    ) {
        val genresListState = rememberLazyListState()
        Row(
            modifier = Modifier.height(235.dp).fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxHeight()
                    .width(400.dp)
                    .padding(10.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(5.dp)
                    )
            ) {
                Text(
                    text = series.name,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                        .padding(5.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
                Image(
                    bitmap = ImageUtils.loadSeriesImageFromFileWithBackup(series),
                    contentDescription = "Series Image",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.size(300.dp, 235.dp)
                        .padding(10.dp)
                        .align(Alignment.CenterHorizontally)
                        .onClick {
                            DialogHelper.showLinkPrompt(
                                series.imageLink,
                                true
                            )
                        }.pointerHoverIcon(PointerIcon.Hand)
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .padding(10.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(5.dp)
                    )
            ) {
                if (series.genreNames.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                            .padding(5.dp)
                            .draggable(
                                state = rememberDraggableState {
                                    coroutineScope.launch {
                                        genresListState.scrollBy(-it)
                                    }
                                },
                                orientation = Orientation.Horizontal
                            ),
                        state = genresListState
                    ) {
                        items(
                            series.genreNames.map {
                                BoxHelper.genreForName(it)
                            }.distinct(),
                            key = { it.id }
                        ) {
                            defaultButton(
                                it.name,
                                height = 30.dp,
                                width = 120.dp
                            ) {
                                Core.openWco(it.name)
                                scope.showToast("Searching for ${it.name} in Database.")
                                /*if (it.slug.isNotEmpty()) {
                                    DialogHelper.showLinkPrompt(
                                        it.slug.slugToLink(),
                                        true,
                                    )
                                } else {
                                    scope.showToast("${it.name} doesn't have a slug.")
                                }*/
                            }
                        }
                    }
                }
                val contextMenuRepresentation = if (isSystemInDarkTheme()) {
                    DarkDefaultContextMenuRepresentation
                } else {
                    LightDefaultContextMenuRepresentation
                }
                CompositionLocalProvider(LocalContextMenuRepresentation provides contextMenuRepresentation) {
                    ContextMenuDataProvider(
                        items = {
                            listOf(
                                ContextMenuItem("Copy Description") {
                                    Tools.clipboardString = series.description
                                    scope.showToast("Copied")
                                }
                            )
                        }
                    ) {
                        var description by remember { mutableStateOf(series.description) }
                        TextField(
                            value = description,
                            readOnly = true,
                            onValueChange = {
                                description = it
                            },
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(0.95f)
                                .padding(10.dp)
                                .align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }
    }

    private fun updateSelectedText() {
        allSelected = selectedEpisodes.isNotEmpty()
        selectText = if (allSelected)
            "Unselect All" else "Select All"
    }

    fun open() {
        ApplicationState.newWindow(
            "Download ${series.name}",
            maximized = true,
            keyEvents = {
                if (it.isShiftPressed) {
                    shiftHeld = true
                    return@newWindow true
                } else {
                    if (shiftHeld) {
                        shiftHeld = false
                    }
                }
                false
            }
        ) {
            val scope = rememberCoroutineScope()
            val episodesListState = rememberLazyListState()
            Scaffold(
                modifier = Modifier.fillMaxSize(50f),
                bottomBar = {
                    bottomBar(this, scope)
                }
            ) { it ->
                Column(
                    modifier = Modifier.padding(bottom = it.calculateBottomPadding())
                        .fillMaxSize()
                ) {
                    seriesInfoHeader(this@newWindow, scope)
                    if (!singleEpisode) {
                        Row(
                            modifier = Modifier.align(Alignment.End).padding(end = 5.dp)
                        ) {
                            defaultSettingsTextField(
                                searchText,
                                onValueChanged = {
                                    searchText = it
                                },
                                hint = "Search By Name",
                                textStyle = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.fillMaxWidth(0.50f)
                                    .padding(10.dp).height(40.dp),
                                requestFocus = true
                            )
                            defaultButton(
                                selectText,
                                height = 35.dp,
                                width = 120.dp,
                                padding = 10.dp,
                            ) {
                                if (allSelected) {
                                    allSelected = false
                                    selectText = "Select All"
                                    selectedEpisodes.clear()
                                } else {
                                    allSelected = true
                                    selectText = "Deselect All"
                                    selectedEpisodes.clear()
                                    selectedEpisodes.addAll(series.episodes)
                                }
                            }
                        }
                    }
                    FullBox {
                        val seasonData = filteredEpisodes
                        val isExpandedMap = rememberSavableSnapshotStateMap {
                            List(seasonData.size) { index: Int ->
                                index to seasonData[index].expandOnStart
                            }.toMutableStateMap()
                        }
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                            modifier = Modifier.padding(
                                top = 5.dp,
                                bottom = 5.dp,
                                end = verticalScrollbarEndPadding
                            ).fillMaxSize().draggable(
                                state = rememberDraggableState {
                                    scope.launch {
                                        episodesListState.scrollBy(-it)
                                    }
                                },
                                orientation = Orientation.Vertical,
                            ),
                            state = episodesListState
                        ) {
                            seasonData.onEachIndexed { index, seasonData ->
                                section(
                                    seasonData = seasonData,
                                    isExpanded = isExpandedMap[index] == true,
                                    onHeaderClick = {
                                        isExpandedMap[index] = isExpandedMap[index] != true
                                    }
                                )
                            }
                        }
                        verticalScrollbar(episodesListState)
                        if (toDownload.episode != null) {
                            LaunchedEffect(Unit) {
                                val index = indexForEpisode(toDownload.episode)
                                if (index != -1) {
                                    episodesListState.animateScrollToItem(index)
                                }
                            }
                        }
                    }
                }
            }
            ApplicationState.addToastToWindow(this@newWindow)
        }
    }

    private val episodes: List<Episode>
        get() = series.episodes.sortedWith(Tools.baseEpisodesComparator)

    private val filteredEpisodes: List<SeasonData>
        get() {
            return if (searchText.isNotEmpty()) {
                val data = filteredSeasonData
                return data.map {
                    SeasonData(
                        "Search: $searchText",
                        it.episodes.filter { episode ->
                            episode.name.contains(searchText, true)
                        },
                        true,
                        searchMode = true
                    )
                }
            } else {
                seasonData()
            }
        }

    private val filteredSeasonData: List<SeasonData>
        get() = seasonData().filter {
            return@filter it.containsEpisodeName(searchText)
                    || it.seasonTitle.contains(searchText, true)
        }

    private fun seasonData(): List<SeasonData> {
        if (episodes.isEmpty()) {
            return emptyList()
        }
        singleEpisode = episodes.size == 1
        val seasonDataList = mutableListOf<SeasonData>()
        val episodes = episodes.toMutableList()
        if (!movieMode) {
            val movies = episodes.filter {
                it.name.contains("Movie", true) || it.name.contains("Film", true)
            }
            if (movies.isNotEmpty()) {
                seasonDataList.add(
                    SeasonData(
                        "${series.name} Movies (${movies.size})",
                        movies,
                        false
                    )
                )
                episodes.removeAll(movies)
            }
            val ovas = episodes.filter {
                it.name.contains("OVA", true)
            }
            if (ovas.isNotEmpty()) {
                seasonDataList.add(
                    SeasonData(
                        "${series.name} OVA (${ovas.size})",
                        ovas,
                        false
                    )
                )
                episodes.removeAll(ovas)
            }
        } else {
            return listOf(
                SeasonData(
                    "Movies (${episodes.size})",
                    episodes,
                    true
                )
            )
        }
        val hasSeasons = episodes.any { it.name.contains("Season", true) }
        if (hasSeasons) {
            val subLists = episodes.groupBy {
                val match = Regex("Season(?:\\s|\\s?[:/]\\s?)\\d+").find(it.name)
                return@groupBy if (match != null) {
                    val seasonName = it.name.substring(match.range)
                    seasonName.filter { char -> char.isDigit() }.ifEmpty { 1 }
                } else {
                    1
                }
            }.mapValues { map -> map.value.distinctBy { episode -> episode.name } }
            return seasonDataList.plus(subLists.map {
                val seasonNumber = it.key.toString().toInt()
                SeasonData(
                    "${series.name} Season $seasonNumber (${it.value.size})",
                    it.value,
                    it.value.contains(toDownload.episode)
                )
            }.sortedBy { data ->
                val number = data.seasonTitle.filter { ch -> ch.isDigit() }.toIntOrNull()
                return@sortedBy number?.toString()?: data.seasonTitle

            })
        } else {
            if (episodes.isNotEmpty()) {
                seasonDataList.add(
                    SeasonData(
                        "${series.name} (${episodes.size})",
                        episodes,
                        true
                    )
                )
            }
            return seasonDataList
        }
    }

    private fun indexForEpisode(
        episode: Episode,
        forScroll: Boolean = true
    ): Int {
        var index = -1
        val seasonData = seasonData()
        seasonData.forEach {
            index++
            if (forScroll) {
                if (it.expandOnStart) {
                    it.episodes.forEach { e ->
                        index++
                        if (e.matches(episode)) {
                            return index
                        }
                    }
                }
            } else {
                it.episodes.forEach { e ->
                    index++
                    if (e.matches(episode)) {
                        return index
                    }
                }
            }
        }
        return index
    }

    private fun episodeForIndex(index: Int): Episode? {
        var mIndex = -1
        val seasonData = seasonData()
        seasonData.forEach {
            mIndex++
            it.episodes.forEach { e ->
                mIndex++
                if (mIndex == index) {
                    return e
                }
            }
        }
        return null
    }

    private fun addHighlightedEpisode(index: Int) {
        if (!highlightedEpisodes.contains(index)) {
            highlightedEpisodes.add(index)
        }
    }

    @Composable
    private fun episodeRow(episode: Episode) {
        var checked = selectedEpisodes.contains(episode)
        val highlighted = highlightedEpisodes.contains(
            indexForEpisode(episode, false)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(55.dp).background(
                color = if (!highlighted)
                    MaterialTheme.colorScheme.secondaryContainer
                else
                    MaterialTheme.colorScheme.primary
                        .tone(40.0),
                shape = RoundedCornerShape(5.dp)
            ).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(
                    color = if (!highlighted)
                        MaterialTheme.colorScheme.secondaryContainer.hover()
                    else
                        MaterialTheme.colorScheme.primary
                            .tone(40.0)
                            .hover()
                )
            ) {
                if (shiftHeld) {
                    val index = indexForEpisode(episode, false)
                    if (highlighted) {
                        highlightedEpisodes.remove(index)
                    } else {
                        if (highlightedEpisodes.isEmpty()) {
                            highlightedEpisodes.add(index)
                        } else {
                            val firstIndex = highlightedEpisodes.first()
                            if (firstIndex > index) {
                                for (i in firstIndex downTo index) {
                                    addHighlightedEpisode(i)
                                }
                            } else {
                                for (i in firstIndex..index) {
                                    addHighlightedEpisode(i)
                                }
                            }
                        }
                    }
                } else {
                    checked = if (!checked) {
                        selectedEpisodes.add(episode)
                        updateSelectedText()
                        true
                    } else {
                        selectedEpisodes.remove(episode)
                        updateSelectedText()
                        false
                    }
                }
            }.height(40.dp)
        ) {
            Text(
                text = episode.name,
                modifier = Modifier
                    .padding(4.dp)
                    .weight(1f, true)
                    .align(Alignment.CenterVertically),
                color = if (!highlighted)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
                        .tone(80.0)
            )
            if (!singleEpisode && !shiftHeld) {
                Checkbox(
                    checked,
                    onCheckedChange = {
                        checked = if (it) {
                            selectedEpisodes.add(episode)
                            updateSelectedText()
                            true
                        } else {
                            selectedEpisodes.remove(episode)
                            updateSelectedText()
                            false
                        }
                    }
                )
            }
        }
    }

    data class SeasonData(
        val seasonTitle: String,
        val episodes: List<Episode>,
        val expandOnStart: Boolean,
        val searchMode: Boolean = false
    ) {
        fun containsEpisodeName(search: String): Boolean {
            return episodes.map { it.name }.toString()
                .contains(search, true)
        }
    }

    @Composable
    fun seasonHeader(
        seasonData: SeasonData,
        expanded: Boolean,
        onHeaderClicked: () -> Unit
    ) {
        Row(modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(
                    color = MaterialTheme.colorScheme.primaryContainer
                )
            ) { onHeaderClicked() }
            .background(MaterialTheme.colorScheme.primary)
            .height(seasonDataHeaderHeight)
            .padding(vertical = 1.dp)) {
            Text(
                text = seasonData.seasonTitle,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1.0f)
                    .align(Alignment.CenterVertically).padding(start = 4.dp),
                color = MaterialTheme.colorScheme.onPrimary
            )
            if (!singleEpisode) {
                var checked by mutableStateOf(selectedEpisodes.containsOne(seasonData.episodes))
                var selectedCount = selectedEpisodes.filter { episode ->
                    seasonData.episodes.contains(episode)
                }.size
                defaultButton(
                    if (checked) "($selectedCount) Selected" else "Select All",
                    modifier = Modifier.align(Alignment.CenterVertically),
                    fontSize = 13.sp,
                    fontColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    checked = checked.not()
                    if (checked) {
                        selectedEpisodes.addAll(seasonData.episodes)
                        updateSelectedText()
                        true
                    } else {
                        selectedEpisodes.removeAll(seasonData.episodes)
                        updateSelectedText()
                        false
                    }
                }
            }
            //https://github.com/mohammadestk/compose-expandable/blob/master/expandable/src/main/java/dev/esteki/expandable/Expandable.kt
            val expandAnimation = animateFloatAsState(
                targetValue = if (!expanded) 540f else 0f,
                animationSpec = tween(500, easing = FastOutSlowInEasing)
            )
            defaultIcon(
                EvaIcons.Fill.ChevronDown,
                iconSize = Constants.largeIconSize,
                iconColor = MaterialTheme.colorScheme.onPrimary,
                iconModifier = Modifier.align(Alignment.CenterVertically)
                    .rotate(expandAnimation.value)
            )
        }
    }

    private fun LazyListScope.section(
        seasonData: SeasonData,
        isExpanded: Boolean,
        onHeaderClick: () -> Unit
    ) {
        if (!seasonData.searchMode) {
            item(UUID.randomUUID().toString()) {
                seasonHeader(
                    seasonData = seasonData,
                    expanded = isExpanded,
                    onHeaderClicked = onHeaderClick
                )
            }
            if (isExpanded) {
                items(
                    seasonData.episodes,
                    key = { UUID.randomUUID().toString() }
                ) {
                    episodeRow(it)
                }
            }
        } else {
            items(
                seasonData.episodes,
                key = { UUID.randomUUID().toString() }
            ) {
                episodeRow(it)
            }
        }
    }

    private fun <K, V> snapshotStateMapSaver() = Saver<SnapshotStateMap<K, V>, Any>(
        save = { state -> state.toList() },
        restore = { value ->
            @Suppress("UNCHECKED_CAST")
            (value as? List<Pair<K, V>>)?.toMutableStateMap() ?: mutableStateMapOf()
        }
    )

    @Composable
    fun <K, V> rememberSavableSnapshotStateMap(
        init: () -> SnapshotStateMap<K, V>
    ): SnapshotStateMap<K, V> = rememberSaveable(
        saver = snapshotStateMapSaver(), init = init
    )

    private suspend fun checkForNewEpisodes(): Resource<Int> = withContext(Dispatchers.IO) {
        downloadButtonEnabled.value = false
        checkForEpisodesButtonEnabled.value = false
        val result = SeriesUpdater.checkForNewEpisodes(series)
        if (result.data != null) {
            for (e in result.data.newEpisodes) {
                if (!selectedEpisodes.contains(e)) {
                    selectedEpisodes.add(e)
                }
            }
            val updatedEpisodes = result.data.updatedEpisodes
            val seriesWco = BoxHelper.seriesForSlug(series.slug)
            seriesWco?.updateEpisodes(updatedEpisodes)
            series.updateEpisodes(updatedEpisodes)
            downloadButtonEnabled.value = true
            checkForEpisodesButtonEnabled.value = true
            Resource.Success(result.data.newEpisodes.size)
        } else {
            downloadButtonEnabled.value = true
            checkForEpisodesButtonEnabled.value = true
            Resource.Error(result.message)
        }
    }

    @Composable
    private fun bottomBar(
        windowScope: AppWindowScope,
        coroutineScope: CoroutineScope
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().height(bottomBarHeight)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.CenterHorizontally)
                    .padding(10.dp)
            ) {
                if (shiftHeld) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Shift Mode",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        Text(
                            "Click any episode to highlight it from Point A to Point B.",
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    if (highlightedEpisodes.isNotEmpty()) {
                        defaultButton(
                            "Clear Highlighted Episodes",
                            height = bottomBarButtonHeight,
                            width = 175.dp,
                            padding = 0.dp
                        ) {
                            highlightedEpisodes.clear()
                        }
                        defaultButton(
                            "Select ${highlightedEpisodes.size} Highlighted Episode(s)",
                            height = bottomBarButtonHeight,
                            width = 200.dp,
                            padding = 0.dp,
                        ) {
                            highlightedEpisodes.forEach {
                                val episode = episodeForIndex(it)
                                if (episode != null) {
                                    if (!selectedEpisodes.contains(episode)) {
                                        selectedEpisodes.add(episode)
                                    }
                                }
                            }
                            highlightedEpisodes.clear()
                            updateSelectedText()
                        }
                    } else {
                        if (!movieMode) {
                            defaultButton(
                                "Check For New Episodes",
                                height = bottomBarButtonHeight,
                                width = 150.dp,
                                padding = 0.dp,
                                enabled = checkForEpisodesButtonEnabled,
                            ) {
                                windowScope.showToast("Checking for new episodes...")
                                coroutineScope.launch {
                                    val result = checkForNewEpisodes()
                                    val data = result.data
                                    if (data != null) {
                                        windowScope.showToast(
                                            "Found ${result.data} new episode(s). They have been selected."
                                        )
                                        singleEpisode = result.data > 1
                                    } else {
                                        windowScope.showToast("No new episodes were found.")
                                        FrogLog.logError(
                                            "Failed to find new episodes for ${series.name}",
                                            result.message
                                        )
                                    }
                                }
                            }
                        }
                        if (movieMode) {
                            defaultButton(
                                "Download Movie",
                                height = bottomBarButtonHeight,
                                width = 175.dp,
                                padding = 0.dp,
                                enabled = downloadButtonEnabled,
                            ) {
                                BoxMaker.makeHistory(
                                    series.slug
                                )
                                if (Core.child.isRunning) {
                                    val added = Core.child.addEpisodesToQueue(
                                        if (singleEpisode)
                                            listOf(series.episodes.first())
                                        else
                                            selectedEpisodes
                                    )
                                    if (added > 0) {
                                        windowScope.showToast("Added $added movie(s) to current queue.")
                                    } else {
                                        windowScope.showToast(
                                            "No movies have been added to current queue. They have already been added before."
                                        )
                                    }
                                    return@defaultButton
                                }
                                Core.child.softStart()
                                //must use an outside scope because closing this window
                                //will cancel the local coroutine
                                Core.child.taskScope.launch(Dispatchers.IO) {
                                    //sort in case the episodes are not in order
                                    Core.child.addEpisodesToQueue(
                                        if (singleEpisode)
                                            listOf(series.episodes.first())
                                        else
                                            selectedEpisodes.sortedWith(Tools.baseEpisodesComparator)
                                    )
                                    try {
                                        var threads = if (!Defaults.HEADLESS_MODE.boolean())
                                            1 else Defaults.DOWNLOAD_THREADS.int()
                                        if (Core.child.currentEpisodes.size < threads) {
                                            threads = Core.child.currentEpisodes.size
                                        }
                                        val tasks = mutableListOf<Job>()
                                        for (i in 1..threads) {
                                            tasks.add(
                                                launch {
                                                    val downloader = VideoDownloadHandler(temporaryQuality)
                                                    try {
                                                        downloader.run()
                                                    } catch (e: Exception) {
                                                        downloader.killDriver()
                                                        val error = e.localizedMessage
                                                        if (error.contains("unknown error: cannot find")
                                                            || error.contains("Unable to find driver executable")
                                                            || error.contains("unable to find binary")
                                                        ) {
                                                            FrogLog.writeMessage(
                                                                "[$i] Failed to find Chrome. You must install Chrome before downloading videos."
                                                            )
                                                        } else {
                                                            FrogLog.logError(
                                                                "[$i] VideoDownloader failed.",
                                                                e
                                                            )
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                        tasks.joinAll()
                                        if (Core.child.downloadsFinishedForSession > 0) {
                                            FrogLog.writeMessage(
                                                "Gracefully finished downloading ${Core.child.downloadsFinishedForSession} video(s)."
                                            )
                                        } else {
                                            FrogLog.writeMessage(
                                                "Gracefully finished. No downloads have been made."
                                            )
                                        }
                                        Core.child.stop()
                                    } catch (e: Exception) {
                                        Core.child.stop()
                                        FrogLog.logError("Download task failed with the error: " + e.localizedMessage)
                                    }
                                }
                                FrogLog.writeMessage("Successfully launched video downloader for movie.")
                                windowScope.closeWindow()
                            }
                        } else {
                            defaultButton(
                                if (singleEpisode)
                                    "Download Episode"
                                else if (selectedEpisodes.isNotEmpty())
                                    "Download ${selectedEpisodes.size} Episodes"
                                else
                                    "Select Episodes",
                                height = bottomBarButtonHeight,
                                width = 200.dp,
                                padding = 0.dp,
                                enabled = downloadButtonEnabled,
                            ) {
                                if (series.episodes.isEmpty()) {
                                    windowScope.showToast("There's no episodes to download.")
                                    return@defaultButton
                                }
                                if (!singleEpisode && selectedEpisodes.isEmpty()) {
                                    windowScope.showToast("You must select at least 1 episode.")
                                    return@defaultButton
                                }
                                BoxMaker.makeHistory(
                                    series.slug
                                )
                                if (Core.child.isRunning) {
                                    val added = Core.child.addEpisodesToQueue(
                                        if (singleEpisode)
                                            listOf(series.episodes.first())
                                        else
                                            selectedEpisodes
                                    )
                                    if (added > 0) {
                                        windowScope.showToast("Added $added episode(s) to current queue.")
                                    } else {
                                        windowScope.showToast(
                                            "No episodes have been added to current queue. They have already been added before."
                                        )
                                    }
                                    return@defaultButton
                                }
                                if (singleEpisode) {
                                    selectedEpisodes.add(series.episodes.first())
                                }
                                Core.child.softStart()
                                //must use an outside scope because closing this window
                                //will cancel the local coroutine
                                Core.child.taskScope.launch(Dispatchers.IO) {
                                    //sort in case the episodes are not in order
                                    Core.child.addEpisodesToQueue(
                                        selectedEpisodes.sortedWith(Tools.baseEpisodesComparator)
                                    )
                                    try {
                                        var threads = if (!Defaults.HEADLESS_MODE.boolean())
                                            1 else Defaults.DOWNLOAD_THREADS.int()
                                        if (Core.child.currentEpisodes.size < threads) {
                                            threads = Core.child.currentEpisodes.size
                                        }
                                        val tasks = mutableListOf<Job>()
                                        for (i in 1..threads) {
                                            tasks.add(
                                                launch {
                                                    val downloader = VideoDownloadHandler(temporaryQuality)
                                                    try {
                                                        downloader.run()
                                                    } catch (e: Exception) {
                                                        downloader.killDriver()
                                                        val error = e.localizedMessage
                                                        if (error.contains("unknown error: cannot find")
                                                            || error.contains("Unable to find driver executable")
                                                            || error.contains("unable to find binary")
                                                        ) {
                                                            FrogLog.writeMessage(
                                                                "[$i] Failed to find Chrome. You must install Chrome before downloading videos."
                                                            )
                                                        } else {
                                                            FrogLog.logError(
                                                                "[$i] VideoDownloader failed.",
                                                                e
                                                            )
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                        tasks.joinAll()
                                        if (Core.child.downloadsFinishedForSession > 0) {
                                            FrogLog.writeMessage(
                                                "Gracefully finished downloading ${Core.child.downloadsFinishedForSession} video(s)."
                                            )
                                        } else {
                                            FrogLog.writeMessage(
                                                "Gracefully finished. No downloads have been made."
                                            )
                                        }
                                        Core.child.stop()
                                    } catch (e: Exception) {
                                        Core.child.stop()
                                        FrogLog.logError("Download task failed with the error: " + e.localizedMessage)
                                    }
                                }
                                FrogLog.writeMessage("Successfully launched video downloader for ${selectedEpisodes.size} episode(s).")
                                windowScope.closeWindow()
                            }
                        }

                        if (!movieMode) {
                            var openQuality by remember { mutableStateOf(false) }
                            var qualityName by remember { mutableStateOf(temporaryQuality.tag) }

                            defaultDropdown(
                                "Quality: $qualityName",
                                openQuality,
                                Quality.entries.map {
                                    DropdownOption(it.tag) {
                                        temporaryQuality = it
                                        qualityName = it.tag
                                        openQuality = false
                                    }
                                },
                                boxColor = MaterialTheme.colorScheme.primary,
                                boxTextColor = MaterialTheme.colorScheme.onPrimary,
                                boxWidth = 140.dp,
                                boxHeight = bottomBarButtonHeight,
                                centerBoxText = true,
                                onTextClick = { openQuality = true },
                            ) { openQuality = false }
                        }
                    }
                }
            }
        }
    }

    companion object {
        private val seasonDataHeaderHeight = 45.dp
        private val bottomBarButtonHeight = 45.dp
    }

}