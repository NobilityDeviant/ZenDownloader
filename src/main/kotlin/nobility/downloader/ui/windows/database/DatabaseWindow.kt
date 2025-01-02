package nobility.downloader.ui.windows.database

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.fill.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.BoxHelper.Companion.update
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Series
import nobility.downloader.core.entities.data.SeriesIdentity
import nobility.downloader.core.scraper.data.ToDownload
import nobility.downloader.core.settings.Defaults
import nobility.downloader.ui.components.*
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.ui.windows.MovieEditorWindow
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.*
import java.util.regex.Pattern

/**
 * A window for showing the database.
 * Unlike JavaFX we have full control now.
 * With that said, I actually have to optimize and create a lot of stuff myself.
 * If you're having any issues please report them!
 * I want this to be as good as it possibly can be.
 * @author NobilityDev
 */
class DatabaseWindow {

    private var loading by mutableStateOf(false)
    private var resultText by mutableStateOf("")

    private val mSearchByGenre = MutableStateFlow(
        Defaults.DB_SEARCH_GENRE.boolean()
    )
    private val searchByGenre = mSearchByGenre.asStateFlow()

    private val mSearchByDesc = MutableStateFlow(
        Defaults.DB_SEARCH_DESC.boolean()
    )
    private val searchByDesc = mSearchByDesc.asStateFlow()

    private val mDatabaseType = MutableStateFlow(
        DatabaseType.typeForId(Defaults.DB_LAST_TYPE_USED.int())
    )
    private val databaseType = mDatabaseType.asStateFlow()

    private val mDatabaseSort = MutableStateFlow(
        DatabaseSort.sortForId(Defaults.DB_LAST_SORT_USED.int())
    )
    private val databaseSort = mDatabaseSort.asStateFlow()

    private val mSearchText = MutableStateFlow("")
    private val searchText = mSearchText.asStateFlow()

    private val series = combine(
        databaseType,
        databaseSort,
        searchText,
        searchByGenre,
        searchByDesc
    ) { type, sort, search, byGenre, byDesc ->
            if (search.isBlank()) {
                sortedSeries
            } else {
                sortedSeries.filter {
                    var foundGenre = false
                    if (byGenre) {
                        for (genre in it.genreNames) {
                            if (genre.equals(search, true)) {
                                foundGenre = true
                                break
                            }
                        }
                    }
                    it.name.contains(search, true) || (byDesc && it.description.contains(search, true)) || foundGenre
                }
            }
        }.stateIn(
            Core.child.taskScope,
            SharingStarted.WhileSubscribed(5000),
            sortedSeries
        )

    private val databaseByType: List<Series>
        get() {
            return when (databaseType.value) {
                DatabaseType.ANIME -> BoxHelper.shared.dubbedSeriesBox.all
                    .plus(BoxHelper.shared.subbedSeriesBox.all)

                DatabaseType.CARTOON -> BoxHelper.shared.cartoonSeriesBox.all
                DatabaseType.MOVIE -> BoxHelper.shared.moviesSeriesBox.all
                DatabaseType.MISC -> BoxHelper.shared.miscSeriesBox.all
            }
        }

    private val sortedSeries: List<Series>
        get() {
            return when (databaseSort.value) {
                DatabaseSort.NAME -> databaseByType.sortedBy { it.name }
                DatabaseSort.NAME_DESC -> databaseByType.sortedByDescending { it.name }
                DatabaseSort.EPISODES -> databaseByType.sortedBy { it.episodesSize }
                DatabaseSort.EPISODES_DESC -> databaseByType.sortedByDescending { it.episodesSize }
            }
        }

    @OptIn(ExperimentalFoundationApi::class)
    fun open(
        initialSearch: String = ""
    ) {
        mSearchText.value = initialSearch
        ApplicationState.newWindow(
            "Database"
        ) {
            val scope = rememberCoroutineScope()
            val seasonsListState = rememberLazyListState()
            Scaffold(
                modifier = Modifier.fillMaxSize(50f),
                bottomBar = {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(15.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val search by searchText.collectAsState()
                            defaultSettingsTextField(
                                search,
                                onValueChanged = {
                                    mSearchText.value = it
                                },
                                hint = randomSearchHint,
                                textStyle = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.fillMaxWidth(0.50f)
                                    .padding(10.dp).height(40.dp),
                                requestFocus = true
                            )
                            val searchByGenre by searchByGenre.collectAsState()
                            val searchByDesc by searchByDesc.collectAsState()
                            val genreTooltip = "Enables searching by genre. Search by genre is case sensitive."
                            tooltip(genreTooltip) {
                                defaultCheckbox(
                                    searchByGenre,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 10.dp)
                                ) {
                                    mSearchByGenre.value = searchByGenre.not()
                                    Defaults.DB_SEARCH_GENRE.update(mSearchByGenre.value)
                                }
                            }
                            tooltip(genreTooltip) {
                                Text(
                                    "Genre",
                                    fontSize = 14.sp,
                                    modifier = Modifier.onClick {
                                        mSearchByGenre.value = searchByGenre.not()
                                        Defaults.DB_SEARCH_GENRE.update(mSearchByGenre.value)
                                    }.padding(top = 10.dp, bottom = 15.dp)
                                )
                            }
                            val descTooltip = "Enables searching by description keywords."
                            tooltip(descTooltip) {
                                defaultCheckbox(
                                    searchByDesc,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 10.dp)
                                ) {
                                    mSearchByDesc.value = searchByDesc.not()
                                    Defaults.DB_SEARCH_DESC.update(mSearchByDesc.value)
                                }
                            }
                            tooltip(descTooltip) {
                                Text(
                                    "Description",
                                    fontSize = 14.sp,
                                    modifier = Modifier.onClick {
                                        mSearchByDesc.value = searchByDesc.not()
                                        Defaults.DB_SEARCH_DESC.update(mSearchByDesc.value)
                                    }.padding(top = 10.dp, bottom = 15.dp)
                                )
                            }
                        }
                        Text(
                            resultText,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val type by databaseType.collectAsState()
                            DatabaseType.entries.forEach {
                                defaultButton(
                                    it.name.capitalizeFirst(),
                                    height = 40.dp,
                                    width = 150.dp,
                                    padding = 10.dp,
                                    enabled = !loading,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (type == it)
                                            MaterialTheme.colorScheme.tertiary
                                        else MaterialTheme.colorScheme.primary,
                                        contentColor = if (type == it)
                                            MaterialTheme.colorScheme.onTertiary
                                        else MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    mDatabaseType.value = it
                                    Defaults.DB_LAST_TYPE_USED.update(mDatabaseType.value.id)
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                Column(
                    modifier = Modifier.padding(
                        bottom = padding.calculateBottomPadding()
                    ).fillMaxSize()
                ) {
                    val series by series.collectAsState()
                    //val series = zeries.collectAsState()


                    header()
                    fullBox {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(1.dp),
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
                                series,
                                key = { it.slug }
                            ) {
                                seriesRow(it, this@newWindow)
                            }
                        }
                        verticalScrollbar(seasonsListState)
                        LaunchedEffect(series.size) {
                            resultText = "Showing ${series.size} series results"
                        }
                        DisposableEffect(Unit) {
                            onDispose {
                                Defaults.DB_LAST_SCROLL_POS.update(
                                    seasonsListState.firstVisibleItemIndex
                                )
                            }
                        }
                        //val saveKey = rememberSaveable { true }
                        LaunchedEffect(Unit) {
                            seasonsListState.scrollToItem(
                                Defaults.DB_LAST_SCROLL_POS.int()
                            )
                        }
                    }
                }
                ApplicationState.addToastToWindow(this)
            }
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
                        mDatabaseSort.value = when (databaseSort.value) {
                            DatabaseSort.NAME -> {
                                DatabaseSort.NAME_DESC
                            }

                            DatabaseSort.NAME_DESC -> {
                                DatabaseSort.NAME
                            }

                            else -> {
                                DatabaseSort.NAME_DESC
                            }
                        }
                        //_dbSort.value = databaseSort
                        Defaults.DB_LAST_SORT_USED.update(databaseSort.value.id)
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
                    val dbSort by databaseSort.collectAsState()
                    if (dbSort == DatabaseSort.NAME || dbSort == DatabaseSort.NAME_DESC) {
                        Icon(
                            if (dbSort == DatabaseSort.NAME_DESC)
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
            divider()
            Text(
                text = "Description",
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.CenterVertically)
                    .weight(DESC_WEIGHT),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                textAlign = TextAlign.Center
            )
            divider()
            Text(
                text = "Genres",
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.CenterVertically)
                    .weight(GENRES_WEIGHT),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                textAlign = TextAlign.Center
            )
            divider()
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(EPISODES_WEIGHT)
                    .align(Alignment.CenterVertically).onClick {
                        mDatabaseSort.value = when (databaseSort.value) {
                            DatabaseSort.EPISODES -> {
                                DatabaseSort.EPISODES_DESC
                            }

                            DatabaseSort.EPISODES_DESC -> {
                                DatabaseSort.EPISODES
                            }

                            else -> {
                                DatabaseSort.EPISODES_DESC
                            }
                        }
                        Defaults.DB_LAST_SORT_USED.update(databaseSort.value.id)
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
                    val dbSort by databaseSort.collectAsState()
                    if (dbSort == DatabaseSort.EPISODES || dbSort == DatabaseSort.EPISODES_DESC) {
                        Icon(
                            if (dbSort == DatabaseSort.EPISODES_DESC)
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
            divider()
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
    private fun seriesRow(
        series: Series,
        windowScope: AppWindowScope
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
                    ToDownload(series)
                )
            }
            defaultDropdownItem(
                "Copy Name",
                EvaIcons.Fill.Copy
            ) {
                closeMenu()
                Tools.clipboardString = series.name
                windowScope.showToast("Copied Name")
            }
            if (series.description.isNotEmpty()) {
                defaultDropdownItem(
                    "Copy Description",
                    EvaIcons.Fill.Copy
                ) {
                    closeMenu()
                    Tools.clipboardString = series.description
                    windowScope.showToast("Copied Description")
                }
            }
            if (series.seriesIdentity == SeriesIdentity.MOVIE) {
                defaultDropdownItem(
                    "Edit Movie",
                    EvaIcons.Fill.Edit
                ) {
                    closeMenu()
                    MovieEditorWindow.open(series)
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
                text = series.name,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.CenterVertically)
                    .weight(NAME_WEIGHT)
            )
            divider()
            Text(
                text = series.description.ifEmpty { "No Description" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxSize(0.85f)
                    .align(Alignment.CenterVertically)
                    .weight(DESC_WEIGHT)
                    .background(
                        Color.Transparent
                    ).border(
                        1.dp,
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Transparent,
                                Color.Transparent,
                                Color.Transparent,
                                Color.Transparent,
                                Color.Transparent,
                                Color.Transparent,
                                Color.Transparent,
                                Color.Transparent,
                                Color.Transparent,
                                Color.Transparent,
                                Color.Transparent,
                                Color.Transparent,
                                MaterialTheme.colorScheme.primary
                            )
                        ),
                        RoundedCornerShape(2.dp)
                    ).verticalScroll(rememberScrollState(0))
                    .onClick(
                        matcher = PointerMatcher.mouse(PointerButton.Secondary)
                    ) {
                        showFileMenu = showFileMenu.not()
                    }
            )
            divider()
            linkifyGenres(
                series.genreNames.distinct(),
                textColor = MaterialTheme.colorScheme.onSecondaryContainer,
                linkColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.CenterVertically)
                    .weight(GENRES_WEIGHT)
            )
            divider()
            Text(
                text = series.episodesSize.toString(),
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.CenterVertically)
                    .weight(EPISODES_WEIGHT),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                textAlign = TextAlign.Center
            )
            divider()
            Image(
                ImageUtils.loadSeriesImageFromFileWithBackup(series),
                contentDescription = "Image For ${series.name}",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
                    .padding(10.dp)
                    .align(Alignment.CenterVertically)
                    .weight(IMAGE_WEIGHT)
                    .background(Color.Red)
                    .onClick {
                        DialogHelper.showLinkPrompt(
                            series.imageLink,
                            true
                        )
                    }.pointerHoverIcon(PointerIcon.Hand)
            )
        }
    }

    @Composable
    private fun divider() {
        VerticalDivider(
            modifier = Modifier.fillMaxHeight()
                .width(1.dp)
                .background(
                    MaterialTheme.colorScheme.onPrimaryContainer
                ),
            color = Color.Transparent
        )
    }

    @Composable
    private fun linkifyGenres(
        genreNames: List<String>,
        textColor: Color = Color.Unspecified,
        textAlign: TextAlign = TextAlign.Center,
        style: TextStyle = LocalTextStyle.current,
        linkColor: Color = Color.Red,
        modifier: Modifier = Modifier
    ) {
        val layoutResult = remember {
            mutableStateOf<TextLayoutResult?>(null)
        }
        val sb = StringBuilder()
        genreNames.forEachIndexed { index, s ->
            sb.append(s)
            if (index != genreNames.lastIndex) {
                sb.append(", ")
            }
        }
        val text = sb.toString()
        val genres = extractGenres(text)
        val annotatedString = buildAnnotatedString {
            append(text)
            genres.forEach {
                addStyle(
                    style = SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline
                    ),
                    start = it.start,
                    end = it.end
                )
                addStringAnnotation(
                    tag = GENRE_TAG,
                    annotation = it.text,
                    start = it.start,
                    end = it.end
                )
            }
        }
        androidx.compose.material.Text(
            text = annotatedString,
            color = textColor,
            style = style,
            textAlign = textAlign,
            onTextLayout = { layoutResult.value = it },
            modifier = modifier.pointerInput(Unit) {
                detectTapGestures { offsetPosition ->
                    layoutResult.value?.let {
                        val position = it.getOffsetForPosition(offsetPosition)
                        annotatedString.getStringAnnotations(position, position).firstOrNull()
                            ?.let { result ->
                                if (result.tag == GENRE_TAG) {
                                    val genre = BoxHelper.genreForName(result.item.trim())
                                    DialogHelper.showOptions(
                                        "Genre Options",
                                        "Genre: ${genre.name}\nWhat would you like to do?",
                                        size = DpSize(450.dp, 200.dp),
                                        buttonWidth = 110.dp,
                                        options = listOf(
                                            Option("Cancel"),
                                            Option("Search For Genre") {
                                                mSearchText.value = genre.name
                                            },
                                            Option("Open Genre Link") {
                                                if (genre.slug.isNotEmpty()) {
                                                    val link = genre.slug.slugToLink()
                                                    DialogHelper.showLinkPrompt(
                                                        link,
                                                        """
                                                            Genre: ${genre.name}
                                            
                                                            Do you want to open:
                                                            $link
                                                            in your default browser?
                                                        """.trimIndent()
                                                    )
                                                } else {
                                                    DialogHelper.showError(
                                                        "No slug found for this genre.",
                                                        size = DpSize(300.dp, 200.dp)
                                                    )
                                                }
                                            }
                                        ))
                                }
                            }
                    }
                }
            }
        )
    }

    private val genrePattern
        get() = Pattern.compile(
            "[^,]+"
        )

    private fun extractGenres(text: String): List<GenreInfo> {
        val matcher = genrePattern.matcher(text)
        var matchStart: Int
        var matchEnd: Int
        val genres = arrayListOf<GenreInfo>()

        while (matcher.find()) {
            matchStart = matcher.start()
            matchEnd = matcher.end()
            val genre = text.substring(matchStart, matchEnd)
            genres.add(
                GenreInfo(
                    genre,
                    matchStart,
                    matchEnd
                )
            )
        }
        return genres
    }

    data class GenreInfo(
        val text: String,
        val start: Int,
        val end: Int
    )

    private val randomSearchHint
        get() =
            BoxHelper.allSeries.map { it.name }.plus(
                BoxHelper.shared.wcoGenreBox.all.map { it.name }
            ).random().lines().firstOrNull()?: ""

    companion object {
        private const val GENRE_TAG = "GEN"
        private val spaceBetweenNameAndIcon = 1.dp
        private val rowHeight = 150.dp
        private const val NAME_WEIGHT = 3f
        private const val DESC_WEIGHT = 2.5f
        private const val EPISODES_WEIGHT = 1.3f
        private const val GENRES_WEIGHT = 2f
        private const val IMAGE_WEIGHT = 2.5f
    }

}