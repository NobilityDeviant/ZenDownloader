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
import kotlinx.coroutines.launch
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.BoxHelper.Companion.update
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Series
import nobility.downloader.core.entities.data.SeriesIdentity
import nobility.downloader.core.scraper.data.ToDownload
import nobility.downloader.core.settings.Defaults
import nobility.downloader.ui.components.defaultButton
import nobility.downloader.ui.components.defaultCheckbox
import nobility.downloader.ui.components.defaultDropdownItem
import nobility.downloader.ui.components.defaultSettingsTextField
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.ui.windows.MovieEditor
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.*
import java.util.regex.Pattern

/**
 * An experimental window for showing the database.
 * Unlike JavaFX we have full control now.
 * With that said, I actually have to optimize and create a lot of stuff myself.
 * If you're having any issues please report them!
 * I want this to be as good as it possibly can be.
 * @author NobilityDev
 */
class DatabaseWindow {

    private var databaseSort by mutableStateOf(
        DatabaseSort.sortForId(Defaults.DB_LAST_SORT_USED.int())
    )
    private var loading by mutableStateOf(false)
    private var type by mutableStateOf(
        DatabaseType.typeForId(Defaults.DB_LAST_TYPE_USED.int())
    )
    private var searchText by mutableStateOf("")
    private var searchByGenre = mutableStateOf(true)
    private var resultText by mutableStateOf("")

    private val sortedSeries: List<Series>
        get() {
            return when (databaseSort) {
                DatabaseSort.NAME -> databaseByType.sortedBy { it.name }
                DatabaseSort.NAME_DESC -> databaseByType.sortedByDescending { it.name }
                DatabaseSort.EPISODES -> databaseByType.sortedBy { it.episodesSize }
                DatabaseSort.EPISODES_DESC -> databaseByType.sortedByDescending { it.episodesSize }
            }
        }

    private val filteredSeries: List<Series>
        get() = sortedSeries.filter {
            if (searchText.isNotEmpty()) {
                var foundGenre = false
                if (searchByGenre.value) {
                    for (genre in it.genreNames) {
                        if (genre.equals(searchText, true)) {
                            foundGenre = true
                            break
                        }
                    }
                }
                return@filter it.name.contains(searchText, true) || foundGenre
            }
            true
        }

    private val databaseByType: List<Series>
        get() {
            return when (type) {
                DatabaseType.ANIME -> BoxHelper.shared.dubbedSeriesBox.all
                    .plus(BoxHelper.shared.subbedSeriesBox.all)

                DatabaseType.CARTOON -> BoxHelper.shared.cartoonSeriesBox.all
                DatabaseType.MOVIE -> BoxHelper.shared.moviesSeriesBox.all
                DatabaseType.MISC -> BoxHelper.shared.miscSeriesBox.all
            }
        }


    @OptIn(ExperimentalFoundationApi::class)
    fun open() {
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
                            defaultSettingsTextField(
                                searchText,
                                onValueChanged = {
                                    searchText = it
                                },
                                hint = randomSearchHint,
                                textStyle = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.fillMaxWidth(0.50f)
                                    .padding(10.dp).height(40.dp),
                                requestFocus = true
                            )
                            defaultCheckbox(
                                searchByGenre,
                                modifier = Modifier.padding(top = 10.dp, bottom = 10.dp)
                            ) {
                                searchByGenre.value = searchByGenre.value.not()
                            }
                            Text(
                                "Search By Genre",
                                fontSize = 14.sp,
                                modifier = Modifier.onClick {
                                    searchByGenre.value = searchByGenre.value.not()
                                }.padding(top = 10.dp, bottom = 15.dp)
                            )
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
                                    type = it
                                    Defaults.DB_LAST_TYPE_USED.update(type.id)
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
                    header()
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                            modifier = Modifier.padding(
                                top = 5.dp,
                                bottom = 5.dp,
                                end = 12.dp
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
                                filteredSeries,
                                key = { it.slug }
                            ) {
                                seriesRow(it, this@newWindow)
                            }
                        }
                        VerticalScrollbar(
                            modifier = Modifier.align(Alignment.CenterEnd)
                                .background(MaterialTheme.colorScheme.surface.tone(20.0))
                                .fillMaxHeight()
                                .padding(top = 3.dp, bottom = 3.dp),
                            style = ScrollbarStyle(
                                minimalHeight = 16.dp,
                                thickness = 10.dp,
                                shape = RoundedCornerShape(10.dp),
                                hoverDurationMillis = 300,
                                unhoverColor = MaterialTheme.colorScheme.surface.tone(50.0).copy(alpha = 0.70f),
                                hoverColor = MaterialTheme.colorScheme.surface.tone(50.0).copy(alpha = 0.90f)
                            ),
                            adapter = rememberScrollbarAdapter(
                                scrollState = seasonsListState
                            )
                        )
                        LaunchedEffect(filteredSeries.size) {
                            resultText = "Showing ${filteredSeries.size} series results"
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
            ).height(40.dp).fillMaxWidth().padding(end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(NAME_WEIGHT)
                    .align(Alignment.CenterVertically).onClick {
                        databaseSort = when (databaseSort) {
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
                        Defaults.DB_LAST_SORT_USED.update(databaseSort.id)
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
                    if (databaseSort == DatabaseSort.NAME || databaseSort == DatabaseSort.NAME_DESC) {
                        Icon(
                            if (databaseSort == DatabaseSort.NAME_DESC)
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
                text = "Description",
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.CenterVertically)
                    .weight(DESC_WEIGHT),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                textAlign = TextAlign.Center
            )
            divider(true)
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
            divider(true)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(EPISODES_WEIGHT)
                    .align(Alignment.CenterVertically).onClick {
                        databaseSort = when (databaseSort) {
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
                        Defaults.DB_LAST_SORT_USED.update(databaseSort.id)
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
                    if (databaseSort == DatabaseSort.EPISODES || databaseSort == DatabaseSort.EPISODES_DESC) {
                        Icon(
                            if (databaseSort == DatabaseSort.EPISODES_DESC)
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
                    MovieEditor.open(series)
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
    private fun divider(
        header: Boolean = false
    ) {
        VerticalDivider(
            modifier = Modifier.fillMaxHeight()
                .width(1.dp)
                .background(
                    MaterialTheme.colorScheme.onPrimaryContainer
                    //if (header)
                      //  MaterialTheme.colorScheme.onPrimaryContainer
                    //else
                      //  MaterialTheme.colorScheme.onSurfaceVariant
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
                                                searchText = genre.name
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
            listOf(
                "To Love Ru",
                "Chobits",
                "Girls Bravo"
            ).plus(
                BoxHelper.shared.wcoGenreBox.all.map { it.name }
            ).random()

    companion object {
        private const val GENRE_TAG = "GEN"
        private val spaceBetweenNameAndIcon = 1.dp
        private val rowHeight = 150.dp
        private const val NAME_WEIGHT = 3f
        private const val DESC_WEIGHT = 3f
        private const val EPISODES_WEIGHT = 0.5f
        private const val GENRES_WEIGHT = 2f
        private const val IMAGE_WEIGHT = 2.5f
    }

}