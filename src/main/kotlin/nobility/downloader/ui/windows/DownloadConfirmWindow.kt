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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import nobility.downloader.core.scraper.SeriesUpdater
import nobility.downloader.core.scraper.VideoDownloader
import nobility.downloader.core.scraper.data.ToDownload
import nobility.downloader.core.settings.Defaults
import nobility.downloader.core.settings.Quality
import nobility.downloader.ui.components.DropdownOption
import nobility.downloader.ui.components.defaultButton
import nobility.downloader.ui.components.defaultDropdown
import nobility.downloader.ui.components.defaultIcon
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.*
import java.util.*

class DownloadConfirmWindow(
    private val toDownload: ToDownload
) {

    //series is never null
    private val series = toDownload.series!!
    private val selectedEpisodes = mutableStateListOf<Episode>()
    private var allSelected = selectedEpisodes.isNotEmpty()
    private var selectText by mutableStateOf(if (allSelected) "Deselect All" else "Select All")
    private var downloadButtonEnabled = mutableStateOf(true)
    private var checkForEpisodesButtonEnabled = mutableStateOf(true)
    private var temporaryQuality by mutableStateOf(
        Quality.qualityForTag(Defaults.QUALITY.string())
    )
    private var singleEpisode by mutableStateOf(false)

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
                    bitmap = ImageUtils.loadImageFromFileWithBackup(
                        series.imagePath,
                        series.imageLink
                    ),
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
                                if (it.slug.isNotEmpty()) {
                                    DialogHelper.showLinkPrompt(
                                        it.slug.slugToLink(),
                                        true,
                                    )
                                } else {
                                    scope.showToast("${it.name} doesn't have a slug.")
                                }
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
            maximized = true
        ) {
            val scope = rememberCoroutineScope()
            val episodesListState = rememberLazyListState()
            Scaffold(
                modifier = Modifier.fillMaxSize(50f),
                bottomBar = {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(15.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            if (!toDownload.isMovie) {
                                defaultButton(
                                    "Check For New Episodes",
                                    height = 50.dp,
                                    width = 150.dp,
                                    padding = 0.dp,
                                    enabled = checkForEpisodesButtonEnabled,
                                ) {
                                    showToast("Checking for new episodes...")
                                    scope.launch {
                                        val result = checkForNewEpisodes()
                                        val data = result.data
                                        if (data != null) {
                                            showToast("Found ${result.data} new episode(s). They have been selected.")
                                            singleEpisode = result.data > 1
                                        } else {
                                            showToast("No new episodes were found.")
                                            FrogLog.logError(
                                                "Failed to find new episodes for ${series.name}",
                                                result.message
                                            )
                                        }
                                    }
                                }
                            }
                            if (toDownload.isMovie) {
                                defaultButton(
                                    "Download Movie",
                                    height = 50.dp,
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
                                            showToast("Added $added movie(s) to current queue.")
                                        } else {
                                            showToast("No movies have been added to current queue. They have already been added before.")
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
                                                        val downloader = VideoDownloader(temporaryQuality)
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
                                    closeWindow()
                                }
                            } else {
                                defaultButton(
                                    if (singleEpisode)
                                        "Download Episode"
                                    else if (selectedEpisodes.isNotEmpty())
                                        "Download ${selectedEpisodes.size} Episodes"
                                    else
                                        "Select Episodes",
                                    height = 50.dp,
                                    width = 175.dp,
                                    padding = 0.dp,
                                    enabled = downloadButtonEnabled,
                                ) {
                                    if (series.episodes.isEmpty()) {
                                        showToast("There's no episodes to download,")
                                        return@defaultButton
                                    }
                                    if (!singleEpisode && selectedEpisodes.isEmpty()) {
                                        showToast("You must select at least 1 episode.")
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
                                            showToast("Added $added episode(s) to current queue.")
                                        } else {
                                            showToast(
                                                "No episodes have been added to current queue. They have already been added before."
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
                                                        val downloader = VideoDownloader(temporaryQuality)
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
                                    closeWindow()
                                }
                            }

                            if (!toDownload.isMovie) {
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
                                    boxHeight = 50.dp,
                                    centerBoxText = true,
                                    onTextClick = { openQuality = true },
                                ) { openQuality = false }
                            }
                        }
                    }
                }
            ) { it ->
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier.padding(bottom = it.calculateBottomPadding())
                        .verticalScroll(scrollState)
                ) {
                    seriesInfoHeader(this@newWindow, scope)
                    if (!singleEpisode) {
                        Row(
                            modifier = Modifier.align(Alignment.End).padding(end = 5.dp)
                        ) {
                            defaultButton(
                                selectText,
                                height = 30.dp,
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
                    val seasonData = seasonData()
                    val isExpandedMap = rememberSavableSnapshotStateMap {
                        List(seasonData.size) { index: Int ->
                            index to seasonData[index].expandOnStart
                        }.toMutableStateMap()
                    }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(5.dp)
                            .height(300.dp).draggable(
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
                                isExpanded = isExpandedMap[index] ?: false,
                                onHeaderClick = {
                                    isExpandedMap[index] = !(isExpandedMap[index] ?: false)
                                }
                            )
                        }
                    }
                    if (toDownload.episode != null) {
                        //used so the effect doesn't trigger again.
                        val key = rememberSaveable { true }
                        LaunchedEffect(key) {
                            val index = indexOfEpisodeNew(toDownload.episode)
                            if (index != -1) {
                                episodesListState.animateScrollToItem(index)
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

    private fun seasonData(): List<SeasonData> {
        if (episodes.isEmpty()) {
            return emptyList()
        }
        singleEpisode = episodes.size == 1
        val hasSeasons = episodes.any { it.name.contains("Season") }
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
            return subLists.map {
                val seasonNumber = it.key.toString().toInt()
                SeasonData(
                    "${series.name} Season $seasonNumber (${it.value.size})",
                    it.value,
                    it.value.contains(toDownload.episode)
                )
            }
        } else {
            return listOf(
                SeasonData(
                    "${series.name} Season 1 (${episodes.size})",
                    episodes,
                    true
                )
            )
        }
    }

    @Suppress("UNUSED")
    private fun indexOfEpisode(episode: Episode): Int {
        episodes.forEachIndexed { index, e ->
            if (e.matches(episode)) {
                return index
            }
        }
        return -1
    }

    private fun indexOfEpisodeNew(episode: Episode): Int {
        var index = -1
        val seasonData = seasonData()
        seasonData.forEach {
            index++
            if (it.expandOnStart) {
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

    @Composable
    private fun episodeRow(episode: Episode) {
        var checked = selectedEpisodes.contains(episode)
        Row(
            modifier = Modifier.background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(5.dp)
            ).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(color = MaterialTheme.colorScheme.primary)
            ) {
                checked = if (!checked) {
                    selectedEpisodes.add(episode)
                    updateSelectedText()
                    true
                } else {
                    selectedEpisodes.remove(episode)
                    updateSelectedText()
                    false
                }
            }.height(40.dp)
        ) {
            Text(
                text = episode.name,
                modifier = Modifier
                    .padding(4.dp)
                    .weight(1f, true)
                    .align(Alignment.CenterVertically),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!singleEpisode) {
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
        val expandOnStart: Boolean
    )

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
                val checked = mutableStateOf(selectedEpisodes.containsOne(seasonData.episodes))
                Checkbox(
                    checked.value,
                    modifier = Modifier.align(Alignment.CenterVertically),
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.surface,
                        uncheckedColor = MaterialTheme.colorScheme.onPrimary,
                        checkmarkColor = MaterialTheme.colorScheme.onSurface
                    ),
                    onCheckedChange = {
                        checked.value = if (it) {
                            selectedEpisodes.addAll(seasonData.episodes)
                            updateSelectedText()
                            true
                        } else {
                            selectedEpisodes.removeAll(seasonData.episodes)
                            updateSelectedText()
                            false
                        }
                    }
                )
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

    companion object {
        private val seasonDataHeaderHeight = 45.dp
    }

}