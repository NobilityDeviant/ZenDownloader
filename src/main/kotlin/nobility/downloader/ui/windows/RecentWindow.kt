package nobility.downloader.ui.windows

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
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Episode
import nobility.downloader.core.entities.Series
import nobility.downloader.core.scraper.RecentScraper
import nobility.downloader.core.scraper.data.RecentResult
import nobility.downloader.core.scraper.data.ToDownload
import nobility.downloader.ui.components.defaultDropdownItem
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.ImageUtils
import nobility.downloader.utils.Tools
import nobility.downloader.utils.light
import nobility.downloader.utils.linkToSlug

class RecentWindow {

    private var sort by mutableStateOf(Sort.NAME)
    private val recentData = mutableStateListOf<RecentResult.Data>()
    private var loading by mutableStateOf(false)

    private val sortedRecentData: List<RecentResult.Data>
        get() {
            return when (sort) {
                Sort.NAME -> recentData.sortedBy { it.name }
                Sort.NAME_DESC -> recentData.sortedByDescending { it.name }
            }
        }

    private suspend fun loadRecentData() {
        loading = true
        val result = RecentScraper.run()
        val resultData = result.data
        if (resultData != null) {
            recentData.addAll(resultData.data)
        }
        loading = false
    }

    fun open() {
        ApplicationState.newWindow(
            "Recent Series & Episodes",
            maximized = true,
            onClose = {
                recentData.clear()
                true
            }
        ) {
            val scope = rememberCoroutineScope()
            val seasonsListState = rememberLazyListState()
            Scaffold(
                modifier = Modifier.fillMaxSize(50f),
                /*bottomBar = {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(15.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            defaultButton(
                                "Update?",
                                height = 40.dp,
                                width = 150.dp,
                                padding = 10.dp,
                                enabled = updateButtonEnabled
                            ) {

                            }
                        }
                    }
                }*/
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
                    modifier = Modifier.padding(bottom = padding.calculateBottomPadding())
                        .fillMaxSize()
                ) {
                    header()
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 5.dp, bottom = 5.dp)
                            .fillMaxSize().draggable(
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
                            sortedRecentData,
                            key = { it.name }
                        ) {
                            recentDataRow(
                                it,
                                this@newWindow
                            )
                        }
                    }
                }
            }
            ApplicationState.addToastToWindow(this)
            val saveKey = rememberSaveable { true }
            LaunchedEffect(saveKey) {
                showToast("Loading recent data...")
                delay(1000)
                loadRecentData()
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
            ).height(40.dp).fillMaxWidth(),
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

                        }
                    }
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spaceBetweenNameAndIcon),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Name",
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
    private fun recentDataRow(
        recentData: RecentResult.Data,
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
                "Download ${type(recentData)}",
                EvaIcons.Fill.Info
            ) {
                closeMenu()
                val link = recentData.link
                if (link.isNotEmpty()) {
                    var series: Series? = null
                    var episode: Episode? = null
                    if (recentData.isSeries) {
                        series = BoxHelper.seriesForSlug(link.linkToSlug())
                    } else {
                        val pair = BoxHelper.seriesForEpisodeSlug(link.linkToSlug())
                        if (pair != null) {
                            series = pair.first
                            episode = pair.second
                        }
                    }
                    if (series != null) {
                        Core.openDownloadConfirm(
                            ToDownload(series, episode)
                        )
                    } else {
                        if (!Core.child.isRunning) {
                            windowScope.showToast(
                                """
                                    Failed to find local data.
                                    Scraping data for link.
                                """.trimIndent()
                            )
                            Core.currentUrl = link
                            Core.child.start()
                        } else {
                            windowScope.showToast(
                                """
                                    Failed to find local data.
                                    Unable to scrape data for link because the scraper is running.
                                """.trimIndent()
                            )
                        }
                    }
                } else {
                    windowScope.showToast("There is no link for this ${type(recentData)}.")
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
                            .secondaryContainer.light()
                    )
                ) { showFileMenu = showFileMenu.not() }
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(5.dp)
                ).height(rowHeight).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = recentData.name,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.CenterVertically)
                    .weight(NAME_WEIGHT)
            )
            divider()
            val imagePath = BoxHelper.seriesImagesPath + Tools.titleForImages(recentData.name)
            Image(
                bitmap = ImageUtils.loadImageFromFileWithBackup(
                    imagePath,
                    recentData.imagePath
                ),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.size(300.dp, 235.dp)
                    .padding(10.dp)
                    .align(Alignment.CenterVertically)
                    .weight(IMAGE_WEIGHT)
                    .onClick {
                        DialogHelper.showLinkPrompt(
                            recentData.imageLink,
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

    private fun type(data: RecentResult.Data): String {
        return if (data.isSeries) "Series" else "Episode"
    }

    private enum class Sort {
        NAME,
        NAME_DESC
    }

    companion object {
        private val spaceBetweenNameAndIcon = 1.dp
        private val rowHeight = 130.dp
        private const val NAME_WEIGHT = 5f
        private const val IMAGE_WEIGHT = 1f
    }
}