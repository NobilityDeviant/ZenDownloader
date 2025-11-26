package nobility.downloader.ui.windows

import Resource
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
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
import compose.icons.evaicons.fill.*
import kotlinx.coroutines.*
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.BoxMaker
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Episode
import nobility.downloader.core.entities.data.SeriesIdentity
import nobility.downloader.core.scraper.SeriesUpdater
import nobility.downloader.core.scraper.data.ToDownload
import nobility.downloader.core.scraper.player.VideoPlayerHandler
import nobility.downloader.core.settings.Defaults
import nobility.downloader.core.settings.Quality
import nobility.downloader.ui.components.*
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.*
import nobility.downloader.utils.Constants.bottomBarHeight
import nobility.downloader.utils.Constants.mediumIconSize
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
    private var favorited by mutableStateOf(BoxHelper.isSeriesFavorited(series))
    private val scope = CoroutineScope(Dispatchers.IO)
    @Volatile
    private var startedDownloading = false

    init {
        if (toDownload.episode != null) {
            selectedEpisodes.add(toDownload.episode)
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun SeriesInfoHeader(
        windowScope: AppWindowScope,
        coroutineScope: CoroutineScope
    ) {
        val genresListState = rememberLazyListState()
        Row(
            modifier = Modifier.height(
                if (!movieMode) 235.dp else 600.dp
            ).fillMaxWidth()
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
                DefaultImage(
                    series.imagePath,
                    series.imageLink,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.size(300.dp, 235.dp)
                        .padding(10.dp)
                        .align(Alignment.CenterHorizontally)
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
                val genres = series.filteredGenres
                if (genres.isNotEmpty()) {
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
                            )
                            .horizontalWheelScroll { scroll ->
                                coroutineScope.launch {
                                    genresListState.scrollBy(scroll)
                                }
                            },
                        state = genresListState
                    ) {
                        items(
                            genres.map {
                                BoxHelper.genreForName(it)
                            },
                            key = { it.id }
                        ) {
                            val color = Tools.randomColor(Tools.ColorStyle.PASTEL)
                            val onColor = color.toColorOnThis()
                            DefaultButton(
                                it.name,
                                Modifier.height(35.dp)
                                    .width(120.dp),
                                fontSize = 12.sp,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = color,
                                    contentColor = onColor
                                )
                            ) {
                                Core.openWco(it.name)
                                windowScope.showToast("Searching for ${it.name} in Database")
                            }
                        }
                    }
                }
                val contextMenuRepresentation = DefaultContextMenuRepresentation(
                    backgroundColor = if (isSystemInDarkTheme()) Color.DarkGray else Color.LightGray,
                    textColor = if (isSystemInDarkTheme()) Color.White else Color.Black,
                    itemHoverColor = (if (isSystemInDarkTheme()) Color.DarkGray else Color.LightGray).hover(),
                )
                CompositionLocalProvider(LocalContextMenuRepresentation provides contextMenuRepresentation) {
                    ContextMenuDataProvider(
                        items = {
                            listOf(
                                ContextMenuItem("Copy Description") {
                                    Tools.clipboardString = series.description
                                    windowScope.showToast("Copied")
                                }
                            )
                        }
                    ) {
                        var description by remember { mutableStateOf(series.description) }
                        TextField(
                            value = description.ifEmpty { "No Description" },
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
            keyEvents = { focused, it ->
                shiftHeld = it.isShiftPressed
                false
            },
            onClose = {
                scope.cancel()
                return@newWindow true
            }
        ) {
            val composeScope = rememberCoroutineScope()
            val episodesListState = rememberLazyListState()
            Scaffold(
                modifier = Modifier.fillMaxSize(50f),
                bottomBar = {
                    BottomBar(this)
                }
            ) { it ->
                Column(
                    modifier = Modifier.padding(bottom = it.calculateBottomPadding())
                        .fillMaxSize()
                ) {
                    SeriesInfoHeader(this@newWindow, composeScope)
                    if (!singleEpisode && !movieMode) {
                        Row(
                            modifier = Modifier.align(Alignment.End)
                                .padding(end = 4.dp)
                                .height(45.dp)
                        ) {
                            val focusRequester = remember { FocusRequester() }
                            DefaultSettingsTextField(
                                searchText,
                                onValueChanged = {
                                    searchText = it
                                },
                                hint = "Search By Name",
                                textStyle = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.fillMaxWidth(0.50f)
                                    .fillMaxHeight()
                                    .padding(4.dp),
                                requestFocus = true,
                                focusRequester = focusRequester,
                                trailingIcon = {
                                    if (searchText.isNotEmpty()) {
                                        DefaultIcon(
                                            EvaIcons.Fill.Close,
                                            Modifier.size(mediumIconSize)
                                                .pointerHoverIcon(PointerIcon.Hand),
                                            iconColor = MaterialTheme.colorScheme.primary,
                                            onClick = {
                                                searchText = ""
                                                focusRequester.requestFocus()
                                            }
                                        )
                                    }
                                }
                            )
                            DefaultButton(
                                selectText,
                                Modifier.fillMaxHeight()
                                    .width(120.dp)
                                    .padding(4.dp)
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
                    if (!movieMode) {
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
                                        composeScope.launch {
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
                            VerticalScrollbar(episodesListState)
                            if (toDownload.episode != null) {
                                LaunchedEffect(Unit) {
                                    val index = indexForEpisode(toDownload.episode)
                                    if (index >= 1) {
                                        //-1 due to sticky headers
                                        episodesListState.animateScrollToItem(index - 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            ApplicationState.AddToastToWindow(this@newWindow)
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
                }.sortedWith { data1, data2 ->
                    val num1 = data1.seasonTitle.filter { it.isDigit() }.toIntOrNull() ?: -1
                    val num2 = data2.seasonTitle.filter { it.isDigit() }.toIntOrNull() ?: -1
                    num1.compareTo(num2)
                }
            } else {
                seasonData().sortedWith { data1, data2 ->
                    val num1 = data1.seasonTitle.filter { it.isDigit() }.toIntOrNull() ?: -1
                    val num2 = data2.seasonTitle.filter { it.isDigit() }.toIntOrNull() ?: -1
                    num1.compareTo(num2)
                }
            }
        }

    private val filteredSeasonData: List<SeasonData>
        get() = seasonData().filter {
            return@filter it.containsEpisodeName(searchText)
                    || it.seasonTitle.contains(searchText, true)
        }

    fun parseKeywords(input: String): List<List<String>> {
        val regex = Regex("""\s*"([^"]+)"\s*|\s*([^,]+)\s*""")

        return regex.findAll(input).map { match ->
            val quoted = match.groups[1]?.value
            val unquoted = match.groups[2]?.value

            when {
                !quoted.isNullOrBlank() ->
                    quoted.split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }

                !unquoted.isNullOrBlank() ->
                    listOf(unquoted.trim())

                else -> emptyList()
            }
        }.filter { it.isNotEmpty() }.toList()
    }

    private fun seasonData(): List<SeasonData> {

        if (episodes.isEmpty()) {
            return emptyList()
        }
        singleEpisode = episodes.size == 1
        val seasonDataList = mutableListOf<SeasonData>()
        val episodes = episodes.toMutableList()
        if (!movieMode) {

            val keywords = parseKeywords(Defaults.EPISODE_ORGANIZERS.string())

            for (group in keywords) {

                val matches = episodes.filter { ep ->
                    group.any { keyword ->
                        if (keyword.endsWith("*")) {
                            val exact = keyword.removeSuffix("*")
                            Regex("\\b${Regex.escape(exact)}\\b")
                                .containsMatchIn(ep.name)
                        } else {
                            ep.name.contains(keyword, ignoreCase = true)
                        }
                    }
                }

                if (matches.isNotEmpty()) {

                    val title = if (group.size > 1) {
                        "${series.name} (${group.joinToString(" | ")})"
                    } else {
                        "${series.name} ${group.first()}"
                    }

                    seasonDataList.add(
                        SeasonData(
                            title,
                            matches,
                            false
                        )
                    )
                    episodes.removeAll(matches)
                }
            }
        } else {
            return listOf(
                SeasonData(
                    "Movies",
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
                    seasonName.filter { char -> char.isDigit() }.ifEmpty { "1" }
                } else {
                    "Random"
                }
            }.mapValues { map -> map.value.distinctBy { episode -> episode.name } }
            seasonDataList.addAll(subLists.map {
                SeasonData(
                    if (it.key == "Random") "${series.name} ${it.key}" else "${series.name} Season ${it.key}",
                    it.value,
                    it.value.contains(toDownload.episode)
                )
            }.sortedBy { data ->
                val number = data.seasonTitle.filter { ch -> ch.isDigit() }.toIntOrNull()
                return@sortedBy number?.toString() ?: data.seasonTitle

            })
        } else {
            if (episodes.isNotEmpty()) {
                seasonDataList.add(
                    SeasonData(
                        series.name,
                        episodes,
                        true
                    )
                )
            }
        }
        if (seasonDataList.size == 1) {
            return listOf(
                seasonDataList.first().copy(
                    expandOnStart = true
                )
            )
        }
        return seasonDataList
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

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun EpisodeRow(
        episode: Episode
    ) {
        var checked = selectedEpisodes.contains(episode)
        val highlighted = highlightedEpisodes.contains(
            indexForEpisode(episode, false)
        )
        var downloaded by remember {
            mutableStateOf(BoxHelper.isDownloadedEpisode(episode))
        }

        var showFileMenu by remember {
            mutableStateOf(false)
        }

        DefaultCursorDropdownMenu(
            showFileMenu,
            listOf(
                DropdownOption(
                    "Remove From Downloaded",
                    EvaIcons.Fill.Trash,
                    downloaded != null
                ) {
                    BoxHelper.removeDownloadedEpisode(episode)
                    downloaded = null
                },
                DropdownOption(
                    "Mark As Downloaded",
                    EvaIcons.Fill.Info,
                    downloaded == null
                ) {
                    downloaded = BoxMaker.makeDownloadedEpisode(
                        episode.slug
                    )
                },
                DropdownOption(
                    "View Downloaded Episode",
                    EvaIcons.Fill.Eye,
                    downloaded != null
                ) {
                    Core.openDownloadedEpisodesWindow(
                        episode.slug
                    )
                }
            )
        ) { showFileMenu = false }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(55.dp).background(
                color = if (!highlighted)
                    MaterialTheme.colorScheme.secondaryContainer
                else
                    MaterialTheme.colorScheme.primary
                        .tone(40.0),
                shape = RoundedCornerShape(5.dp)
            ).multiClickable(
                indication = ripple(
                    color = if (!highlighted)
                        MaterialTheme.colorScheme.secondaryContainer.hover()
                    else
                        MaterialTheme.colorScheme.primary
                            .tone(40.0)
                            .hover()
                ),
                onSecondaryClick = {
                    showFileMenu = showFileMenu.not()
                }
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
            if (downloaded != null) {
                Tooltip(
                    "Downloaded On: " +
                            Tools.dateAndTimeFormatted(downloaded!!.downloadedDate, false)
                ) {
                    DefaultIcon(
                        EvaIcons.Fill.Info,
                        Modifier.size(30.dp)
                            .padding(end = 8.dp)
                    ) {
                        Core.openDownloadedEpisodesWindow(episode.slug)
                    }
                }
            }
            if (!shiftHeld) {
                DefaultButton(
                    "Watch Online",
                    modifier = Modifier.width(100.dp)
                        .height(30.dp)
                        .padding(end = 4.dp)
                ) {
                    ApplicationState.bringMainToFront()
                    scope.launch {
                        VideoPlayerHandler.playEpisode(
                            episode,
                            temporaryQuality
                        )
                    }
                }
            }
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
    fun SeasonHeader(
        seasonData: SeasonData,
        expanded: Boolean,
        onHeaderClicked: () -> Unit,
        onHeaderRightClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .multiClickable(
                    true,
                    indication = ripple(
                        color = MaterialTheme.colorScheme.primaryContainer
                    ),
                    onPrimaryClick = onHeaderClicked,
                    onSecondaryClick = onHeaderRightClick
                )
                .background(MaterialTheme.colorScheme.primary)
                .height(seasonDataHeaderHeight)
                .padding(vertical = 1.dp)
        ) {
            Text(
                text = seasonData.seasonTitle + " (${seasonData.episodes.size})",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1.0f)
                    .align(Alignment.CenterVertically).padding(start = 4.dp),
                color = MaterialTheme.colorScheme.onPrimary
            )
            if (!singleEpisode) {
                var checked by mutableStateOf(selectedEpisodes.containsOne(seasonData.episodes))
                val selectedCount = selectedEpisodes.filter { episode ->
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
            DefaultIcon(
                EvaIcons.Fill.ChevronDown,
                iconColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.CenterVertically)
                    .rotate(expandAnimation.value)
                    .size(Constants.largeIconSize)
            )
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    private fun LazyListScope.section(
        seasonData: SeasonData,
        isExpanded: Boolean,
        onHeaderClick: () -> Unit
    ) {
        var updateRowsKey by mutableStateOf(0)
        if (!seasonData.searchMode) {
            stickyHeader(UUID.randomUUID().toString()) {
                var downloaded by remember {
                    mutableStateOf(
                        BoxHelper.isAnyDownloadedEpisode(seasonData.episodes)
                    )
                }
                var showFileMenu by remember {
                    mutableStateOf(false)
                }

                DefaultCursorDropdownMenu(
                    showFileMenu,
                    listOf(
                        DropdownOption(
                            "Remove All From Downloaded",
                            EvaIcons.Fill.Trash,
                            downloaded
                        ) {
                            if (seasonData.episodes.size > 250) {
                                DialogHelper.showConfirm(
                                    """
                                        Due to the large number of episodes, it's advised to be cautious.
                                        This can potentially lag and crash the program.
                                        Do you wish to continue?
                                    """.trimIndent(),
                                    "Mass Update Downloaded Warning"
                                ) {
                                    seasonData.episodes.forEach {
                                        BoxHelper.removeDownloadedEpisode(it)
                                    }
                                    downloaded = false
                                    if (updateRowsKey < 4000) {
                                        updateRowsKey++
                                    } else {
                                        updateRowsKey = 0
                                    }
                                }
                            } else {
                                seasonData.episodes.forEach {
                                    BoxHelper.removeDownloadedEpisode(it)
                                }
                                downloaded = false
                                if (updateRowsKey < 4000) {
                                    updateRowsKey++
                                } else {
                                    updateRowsKey = 0
                                }
                            }
                        },
                        DropdownOption(
                            "Mark All As Downloaded",
                            EvaIcons.Fill.Info,
                            !downloaded
                        ) {
                            if (seasonData.episodes.size > 250) {
                                DialogHelper.showConfirm(
                                    """
                                        Due to the large number of episodes, it's advised to be cautious.
                                        This can potentially lag and crash the program.
                                        Do you wish to continue?
                                    """.trimIndent(),
                                    "Mass Update Downloaded Warning"
                                ) {
                                    seasonData.episodes.forEach {
                                        BoxMaker.makeDownloadedEpisode(
                                            it.slug
                                        )
                                    }
                                    downloaded = true
                                    if (updateRowsKey < 4000) {
                                        updateRowsKey++
                                    } else {
                                        updateRowsKey = 0
                                    }
                                }
                            } else {
                                seasonData.episodes.forEach {
                                    BoxMaker.makeDownloadedEpisode(
                                        it.slug
                                    )
                                }
                                downloaded = true
                                if (updateRowsKey < 4000) {
                                    updateRowsKey++
                                } else {
                                    updateRowsKey = 0
                                }
                            }
                        }
                    )
                ) { showFileMenu = false }

                SeasonHeader(
                    seasonData = seasonData,
                    expanded = isExpanded,
                    onHeaderClicked = onHeaderClick,
                    onHeaderRightClick = {
                        showFileMenu = true
                    }
                )
            }
            if (isExpanded) {
                items(
                    seasonData.episodes,
                    key = { UUID.randomUUID().toString() }
                ) {
                    key(updateRowsKey) {
                        EpisodeRow(it)
                    }
                }
            }
        } else {
            items(
                seasonData.episodes,
                key = { UUID.randomUUID().toString() }
            ) {
                EpisodeRow(it)
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
        val result = SeriesUpdater.getNewEpisodes(series)
        val data = result.data
        return@withContext if (data != null) {
            for (e in data.newEpisodes) {
                if (!selectedEpisodes.contains(e)) {
                    selectedEpisodes.add(e)
                }
            }
            val updatedEpisodes = data.updatedEpisodes
            series.updateEpisodes(updatedEpisodes)
            downloadButtonEnabled.value = true
            checkForEpisodesButtonEnabled.value = true
            Resource.Success(data.newEpisodes.size)
        } else {
            downloadButtonEnabled.value = true
            checkForEpisodesButtonEnabled.value = true
            Resource.Error(result.message)
        }
    }

    @Composable
    private fun BottomBar(
        windowScope: AppWindowScope
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
                if (shiftHeld && (!movieMode && !singleEpisode)) {
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
                    if (highlightedEpisodes.isNotEmpty() && (!movieMode && !singleEpisode)) {
                        defaultButton(
                            "Clear Highlighted Episodes",
                            height = bottomBarButtonHeight,
                            width = 175.dp,
                            padding = PaddingValues(0.dp)
                        ) {
                            highlightedEpisodes.clear()
                        }
                        defaultButton(
                            "Select ${highlightedEpisodes.size} Highlighted Episode(s)",
                            height = bottomBarButtonHeight,
                            width = 200.dp,
                            padding = PaddingValues(0.dp),
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
                        TooltipIconButton(
                            if (favorited)
                                "Remove From Favorite"
                            else "Add To Favorite",
                            EvaIcons.Fill.Star,
                            iconColor = if (favorited)
                                Color.Yellow else MaterialTheme.colorScheme.onSurface
                        ) {
                            if (favorited) {
                                BoxHelper.removeSeriesFavorite(series.slug)
                                favorited = false
                            } else {
                                BoxMaker.makeFavorite(series.slug)
                                favorited = true
                            }
                        }
                        if (!movieMode) {
                            DefaultButton(
                                "Check For New Episodes",
                                height = bottomBarButtonHeight,
                                width = 150.dp,
                                padding = 0.dp,
                                enabled = checkForEpisodesButtonEnabled,
                            ) {
                                windowScope.showToast("Checking for new episodes...")
                                scope.launch {
                                    val result = checkForNewEpisodes()
                                    val data = result.data
                                    if (data != null) {
                                        windowScope.showToast(
                                            "Found $data new episode(s). They have been selected."
                                        )
                                        singleEpisode = data > 1
                                    } else {
                                        windowScope.showToast("No new episodes were found.")
                                        if (!result.message.isNullOrEmpty()) {
                                            FrogLog.error(result.message!!)
                                        }
                                    }
                                }
                            }
                        }
                        if (movieMode) {
                            DefaultButton(
                                "Download Movie",
                                height = bottomBarButtonHeight,
                                width = 175.dp,
                                padding = 0.dp,
                                enabled = downloadButtonEnabled,
                            ) {
                                if (startedDownloading) {
                                    return@DefaultButton
                                }
                                windowScope.showToast("Checking requirements...")
                                scope.launch(Dispatchers.Default) {
                                    startedDownloading = true
                                    if (!Core.child.canSoftStart()) {
                                        windowScope.showToast("Failed to start download. Check the console.")
                                        startedDownloading = false
                                        return@launch
                                    }
                                    BoxMaker.makeHistory(
                                        series.slug
                                    )
                                    val movieEpisode = Episode()
                                    movieEpisode.name = series.name
                                    movieEpisode.slug = series.slug
                                    movieEpisode.seriesSlug = series.slug
                                    movieEpisode.isMovie = true
                                    movieEpisode.lastUpdated = System.currentTimeMillis()
                                    if (Core.child.isRunning) {
                                        val added = Core.child.downloadThread.addToQueue(
                                            movieEpisode
                                        )
                                        if (added > 0) {
                                            ApplicationState.showToastForMain(
                                                "Added movie to current queue."
                                            )
                                            ApplicationState.bringMainToFront()
                                            windowScope.closeWindow()
                                        } else {
                                            windowScope.showToast(
                                                "Movie is already in queue."
                                            )
                                            startedDownloading = false
                                        }
                                        return@launch
                                    }
                                    Core.child.softStart()
                                    Core.child.downloadThread.addToQueue(
                                        movieEpisode,
                                        temporaryQuality = temporaryQuality
                                    )
                                    Core.child.launchStopJob()
                                    withContext(Dispatchers.Main) {
                                        ApplicationState.showToastForMain("Launched video downloader for movie.")
                                        ApplicationState.bringMainToFront()
                                        windowScope.closeWindow()
                                    }
                                }
                            }
                        } else {
                            DefaultButton(
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
                                if (startedDownloading) {
                                    return@DefaultButton
                                }
                                windowScope.showToast("Checking requirements...")
                                scope.launch(Dispatchers.Default) {
                                    startedDownloading = true
                                    if (!Core.child.canSoftStart()) {
                                        windowScope.showToast("Failed to start download. Check the console.")
                                        startedDownloading = false
                                        return@launch
                                    }
                                    if (series.episodes.isEmpty()) {
                                        windowScope.showToast("There's no episodes to download.")
                                        startedDownloading = false
                                        return@launch
                                    }
                                    if (!singleEpisode && selectedEpisodes.isEmpty()) {
                                        windowScope.showToast("You must select at least 1 episode.")
                                        startedDownloading = false
                                        return@launch
                                    }
                                    BoxMaker.makeHistory(series.slug)
                                    if (Core.child.isRunning) {
                                        val added = Core.child.downloadThread.addToQueue(
                                            if (singleEpisode)
                                                listOf(series.episodes.first())
                                            else
                                                selectedEpisodes.map { it },
                                            temporaryQuality
                                        )
                                        if (added > 0) {
                                            withContext(Dispatchers.Main) {
                                                ApplicationState.showToastForMain(
                                                    "Added $added episode(s) to current queue."
                                                )
                                                windowScope.closeWindow()
                                            }
                                        } else {
                                            windowScope.showToast(
                                                "No episodes have been added to current queue. They have already been added before."
                                            )
                                            startedDownloading = false
                                        }
                                        return@launch
                                    }
                                    if (singleEpisode) {
                                        selectedEpisodes.add(series.episodes.first())
                                    }
                                    Core.child.softStart()
                                    Core.child.downloadThread.addToQueue(
                                        selectedEpisodes.sortedWith(Tools.baseEpisodesComparator),
                                        temporaryQuality
                                    )
                                    Core.child.launchStopJob()
                                    ApplicationState.showToastForMain("Launched video downloader for ${selectedEpisodes.size} episode(s).")
                                    ApplicationState.bringMainToFront()
                                    windowScope.closeWindow()
                                }
                            }
                        }

                        if (!movieMode) {
                            var openQuality by remember { mutableStateOf(false) }
                            var qualityName by remember { mutableStateOf(temporaryQuality.tag) }

                            DefaultDropdown(
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
                                boxModifier = Modifier.width(140.dp)
                                    .height(bottomBarButtonHeight),
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