package nobility.downloader.ui.windows.database

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.Outline
import compose.icons.evaicons.fill.*
import compose.icons.evaicons.outline.Star
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.BoxHelper.Companion.update
import nobility.downloader.core.BoxMaker
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Series
import nobility.downloader.core.entities.data.SeriesIdentity
import nobility.downloader.core.settings.Defaults
import nobility.downloader.core.settings.Save
import nobility.downloader.ui.components.*
import nobility.downloader.ui.windows.MovieEditorWindow
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.Constants.mediumIconSize
import nobility.downloader.utils.Tools
import nobility.downloader.utils.hover
import nobility.downloader.utils.linkToSlug
import java.util.regex.Pattern

/**
 * A window for showing the video database.
 * Mostly everything has been handcrafted and I couldn't be happier.
 * @author NobilityDev
 */
class DatabaseWindow {

    private lateinit var windowScope: AppWindowScope
    private var loading by mutableStateOf(false)
    private var resultText by mutableStateOf("")

    private val mShowFavorites = MutableStateFlow(false)
    private val showFavorites = mShowFavorites.asStateFlow()

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

    private val searchText = Core.databaseSearchText.asStateFlow()

    @Suppress("TYPE_INTERSECTION_AS_REIFIED_WARNING")
    private val series = combine(
        databaseType,
        searchText,
        searchByGenre,
        searchByDesc,
        showFavorites
    ) { seriesData ->
        //val type = it[0] as DatabaseType
        val search = seriesData[1] as String
        val byGenre = seriesData[2] as Boolean
        val byDesc = seriesData[3] as Boolean
        val favorite = seriesData[4] as Boolean
        if (search.isBlank()) {
            if (favorite) {
                databaseByType.filter {
                    BoxHelper.isSeriesFavorited(it)
                }
            } else {
                databaseByType
            }
        } else {
            databaseByType.filter {
                var foundGenre = false
                if (byGenre) {
                    for (genre in it.filteredGenres) {
                        if (genre.equals(search, true)) {
                            foundGenre = true
                            break
                        }
                    }
                }
                if (favorite) {
                    BoxHelper.isSeriesFavorited(it)
                } else {
                    true
                } && it.name.contains(search, true)
                        || it.slug == search.linkToSlug()
                        || (byDesc && it.description.contains(search, true))
                        || foundGenre
            }
        }
    }.stateIn(
        Core.taskScope,
        SharingStarted.WhileSubscribed(5000),
        databaseByType
    )

    private val databaseByType: List<Series>
        get() {
            return when (databaseType.value) {
                DatabaseType.ALL -> BoxHelper.allSeries
                DatabaseType.DUBBED -> BoxHelper.dubbed
                DatabaseType.SUBBED -> BoxHelper.subbed
                DatabaseType.CARTOON -> BoxHelper.cartoons
                DatabaseType.MOVIE -> BoxHelper.movies
                DatabaseType.MISC -> BoxHelper.misc
            }
        }

    @OptIn(ExperimentalFoundationApi::class)
    fun open(
        initialSearch: String = ""
    ) {
        Core.databaseSearchText.value = initialSearch
        ApplicationState.newWindow(
            "Database",
            maximized = true
        ) {
            windowScope = this@newWindow
            val seasonsListState = rememberLazyListState()
            val fastScrolling by rememberScrollSpeed(seasonsListState)
            var forceUpdateName by mutableStateOf(0)

            Scaffold(
                modifier = Modifier.fillMaxSize(50f),
                bottomBar = {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .wrapContentHeight(Alignment.Bottom).padding(bottom = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val favorite by showFavorites.collectAsState()
                            TooltipIconButton(
                                if (favorite)
                                    "Show Only Favorites On"
                                else "Show Only Favorites Off",
                                EvaIcons.Fill.Star,
                                iconColor = if (favorite)
                                    Color.Yellow else MaterialTheme.colorScheme.onSurface
                            ) {
                                mShowFavorites.value = showFavorites.value.not()
                            }
                            TooltipIconButton(
                                "Genres",
                                EvaIcons.Fill.BookOpen,
                                iconColor = MaterialTheme.colorScheme.primary
                            ) {
                                openGenresWindow()
                            }
                            val focusRequester = remember { FocusRequester() }
                            val search by searchText.collectAsState()
                            DefaultSettingsTextField(
                                search,
                                onValueChanged = {
                                    Core.databaseSearchText.value = it
                                },
                                hint = randomSearchHint,
                                textStyle = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.fillMaxWidth(0.50f)
                                    .padding(end = 10.dp)
                                    .height(40.dp),
                                requestFocus = true,
                                focusRequester = focusRequester,
                                trailingIcon = {
                                    if (search.isNotEmpty()) {
                                        DefaultIcon(
                                            EvaIcons.Fill.Close,
                                            Modifier.size(mediumIconSize)
                                                .pointerHoverIcon(PointerIcon.Hand),
                                            iconColor = MaterialTheme.colorScheme.primary,
                                            onClick = {
                                                Core.databaseSearchText.value = ""
                                                focusRequester.requestFocus()
                                            }
                                        )
                                    }
                                }
                            )
                            val searchByGenre by searchByGenre.collectAsState()
                            val searchByDesc by searchByDesc.collectAsState()
                            val genreTooltip = "Enables searching by genre. Search by genre is case sensitive."
                            Tooltip(genreTooltip) {
                                DefaultCheckbox(
                                    searchByGenre,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                        .pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    mSearchByGenre.value = searchByGenre.not()
                                    Defaults.DB_SEARCH_GENRE.update(mSearchByGenre.value)
                                }
                            }
                            Tooltip(genreTooltip) {
                                Text(
                                    "Genre",
                                    fontSize = 14.sp,
                                    modifier = Modifier.onClick {
                                        mSearchByGenre.value = searchByGenre.not()
                                        Defaults.DB_SEARCH_GENRE.update(mSearchByGenre.value)
                                    }.padding(top = 10.dp, bottom = 15.dp)
                                        .pointerHoverIcon(PointerIcon.Hand)
                                )
                            }
                            val descTooltip = "Enables searching by description keywords."
                            Tooltip(descTooltip) {
                                DefaultCheckbox(
                                    searchByDesc,
                                    modifier = Modifier
                                        .padding(vertical = 10.dp)
                                        .pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    mSearchByDesc.value = searchByDesc.not()
                                    Defaults.DB_SEARCH_DESC.update(mSearchByDesc.value)
                                }
                            }
                            Tooltip(descTooltip) {
                                Text(
                                    "Description",
                                    fontSize = 14.sp,
                                    modifier = Modifier.onClick {
                                        mSearchByDesc.value = searchByDesc.not()
                                        Defaults.DB_SEARCH_DESC.update(mSearchByDesc.value)
                                    }.padding(top = 10.dp, bottom = 15.dp)
                                        .pointerHoverIcon(PointerIcon.Hand)
                                )
                            }
                        }
                        Text(
                            resultText,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(4.dp)
                        )
                        val type by databaseType.collectAsState()
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 10.dp)
                        ) {
                            items(
                                DatabaseType.entries,
                                key = { it.title }
                            ) {
                                DefaultButton(
                                    it.title,
                                    Modifier.size(110.dp, 35.dp)
                                        .padding(horizontal = 2.dp)
                                        .pointerHoverIcon(PointerIcon.Hand)
                                    ,
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
                    val type by databaseType.collectAsState()

                    FullBox {

                        LazyTable(
                            listOf(
                                ColumnItem(
                                    "Series Name",
                                    3f,
                                    weightSaveKey = Save.DB_N_WEIGHT,
                                    defaultSort = true to false,
                                    sortSelector = { it.name }
                                ) { _, series ->
                                    key(forceUpdateName) {
                                        val favorited = BoxHelper.isSeriesFavorited(series)
                                        Box(
                                            Modifier.fillMaxSize()
                                        ) {
                                            if (favorited) {
                                                DefaultIcon(
                                                    EvaIcons.Fill.Star,
                                                    iconColor = Color.Yellow,
                                                    modifier = Modifier
                                                        .padding(4.dp)
                                                        .align(Alignment.TopStart)
                                                )
                                            }
                                            Text(
                                                text = series.name,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                                modifier = Modifier
                                                    .padding(4.dp)
                                                    .align(Alignment.CenterStart)
                                            )
                                        }
                                    }
                                },
                                ColumnItem(
                                    "Description",
                                    2.5f,
                                    weightSaveKey = Save.DB_D_WEIGHT
                                ) { _, series ->
                                    Text(
                                        text = series.description.ifEmpty { "No Description" },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxSize(0.85f)
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
                                            ).verticalScroll(rememberScrollState())
                                    )
                                },
                                ColumnItem(
                                    "Genres",
                                    2f,
                                    weightSaveKey = Save.DB_G_WEIGHT
                                ) { _, series ->
                                    LinkifyGenres(
                                        series.filteredGenres,
                                        textColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        linkColor = MaterialTheme.colorScheme.primary
                                    )
                                },
                                ColumnItem(
                                    if (type != DatabaseType.MOVIE) "Episodes" else "",
                                    1.1f,
                                    weightSaveKey = Save.DB_E_WEIGHT,
                                    sortSelector = { it.episodesSize }
                                ) { _, series ->
                                    if (type != DatabaseType.MOVIE) {
                                        Text(
                                            text = if (series.seriesIdentity != SeriesIdentity.MOVIE)
                                                series.episodesSize.toString() else "Movie",
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                },
                                ColumnItem(
                                    "Image",
                                    2.5f,
                                    weightSaveKey = Save.DB_I_WEIGHT
                                ) { _, series ->
                                    DefaultImage(
                                        series.imagePath,
                                        series.imageLink,
                                        fastScrolling = fastScrolling,
                                        contentScale = ContentScale.FillBounds,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            ),
                            series,
                            lazyListState = seasonsListState,
                            key = { it.slug + it.id },
                            rowHeight = 150.dp,
                            sortSaveKey = Save.DB_SORT
                        ) { _, series ->

                            val favorited = BoxHelper.isSeriesFavorited(series)

                            listOf(
                                DropdownOption(
                                    "Series Details",
                                    EvaIcons.Fill.Info
                                ) {
                                    Core.openSeriesDetails(
                                        series.slug,
                                        windowScope
                                    )
                                },
                                DropdownOption(
                                    if (favorited)
                                        "Remove From Favorite" else "Add To Favorite",
                                    if (favorited)
                                        EvaIcons.Fill.Star else EvaIcons.Outline.Star,
                                    contentColor = if (favorited)
                                        Color.Yellow else LocalContentColor.current
                                ) {
                                    if (favorited) {
                                        BoxHelper.removeSeriesFavorite(series.slug)
                                    } else {
                                        BoxMaker.makeFavorite(series.slug)
                                    }
                                    forceUpdateName++
                                },
                                DropdownOption(
                                    "Copy Name",
                                    EvaIcons.Fill.Copy
                                ) {
                                    Tools.clipboardString = series.name
                                    windowScope.showToast("Copied Name")
                                },
                                DropdownOption(
                                    "Copy Description",
                                    EvaIcons.Fill.Copy,
                                    visible = series.description.isNotEmpty()
                                ) {
                                    Tools.clipboardString = series.description
                                    windowScope.showToast("Copied Description")
                                },
                                DropdownOption(
                                    "Edit Movie",
                                    EvaIcons.Fill.Edit,
                                    visible = series.seriesIdentity == SeriesIdentity.MOVIE
                                ) {
                                    MovieEditorWindow.open(series)
                                }
                            )
                        }
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
                        LaunchedEffect(Unit) {
                            seasonsListState.scrollToItem(
                                Defaults.DB_LAST_SCROLL_POS.int()
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun LinkifyGenres(
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
        Text(
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
                                    Core.databaseSearchText.value = genre.name
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
            ).random().lines().firstOrNull() ?: ""

    @OptIn(ExperimentalFoundationApi::class)
    private fun openGenresWindow() {
        ApplicationState.newWindow(
            "Genres",
            size = DpSize(300.dp, 300.dp),
            alwaysOnTop = true
        ) {
            FullBox {
                val scope = rememberCoroutineScope()
                val scrollState = rememberLazyListState()
                Column(
                    modifier = Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Click a genre to search for it",
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(2.dp)
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                            .padding(end = verticalScrollbarEndPadding)
                            .draggable(
                                state = rememberDraggableState {
                                    scope.launch {
                                        scrollState.scrollBy(-it)
                                    }
                                },
                                orientation = Orientation.Vertical,
                            ),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        state = scrollState
                    ) {
                        items(
                            BoxHelper.shared.wcoGenreBox.all
                                .distinctBy { it.name }
                                .sortedBy { it.capitalName },
                            key = { it.name }
                        ) { genre ->
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .height(35.dp)
                                    .multiClickable(
                                        indication = ripple(
                                            color = MaterialTheme.colorScheme
                                                .secondaryContainer.hover()
                                        ),
                                        onSecondaryClick = {
                                            Core.databaseSearchText.value = genre.name
                                        }
                                    ) {
                                        Core.databaseSearchText.value = genre.name
                                    }
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(5.dp)
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        genre.capitalName,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
                VerticalScrollbar(scrollState)
            }
        }
    }

    companion object {
        private const val GENRE_TAG = "GEN"
    }

}