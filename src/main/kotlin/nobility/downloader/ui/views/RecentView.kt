package nobility.downloader.ui.views

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.sp
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.fill.Info
import kotlinx.coroutines.launch
import nobility.downloader.Page
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.BoxHelper.Companion.long
import nobility.downloader.core.BoxHelper.Companion.update
import nobility.downloader.core.BoxMaker
import nobility.downloader.core.Core
import nobility.downloader.core.entities.RecentData
import nobility.downloader.core.scraper.RecentScraper
import nobility.downloader.core.settings.Defaults
import nobility.downloader.ui.components.*
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.utils.FrogLog
import nobility.downloader.utils.Tools
import nobility.downloader.utils.hover

class RecentView: ViewPage {

    override val page = Page.RECENT

    private var sort: MutableState<HeaderSort?> = mutableStateOf(null)
    private val recentData = BoxHelper.shared.wcoRecentBox.all.toMutableStateList()
    private var loading by mutableStateOf(false)
    private var lastUpdated by mutableStateOf(Defaults.WCO_RECENT_LAST_UPDATED.long())

    suspend fun reloadRecentData() {
        if (loading) {
            return
        }
        Defaults.WCO_RECENT_LAST_UPDATED.update(0L)
        lastUpdated = 0
        loading = true
        val result = RecentScraper.run()
        val data = result.data
        var added = 0
        if (data != null) {
            if (data.isNotEmpty()) {
                data.forEach { recent ->
                    if (!recentData.any { it.matches(recent) }) {
                        recentData.add(recent)
                        BoxMaker.makeRecent(
                            recent.imagePath,
                            recent.imageLink,
                            recent.name,
                            recent.link,
                            recent.isSeries,
                            recent.dateFound
                        )
                        added++
                    }
                }
                if (added > 0) {
                    FrogLog.writeMessage(
                        "Found and added $added new recent data."
                    )
                } else {
                    FrogLog.logError(
                        "Found no uncached recent data."
                    )
                }
            } else {
                FrogLog.logError(
                    "Found no recent data."
                )
            }
            lastUpdated = Defaults.WCO_RECENT_LAST_UPDATED.long()
        } else {
            FrogLog.logError(
                "Failed to load recent data.",
                result.message
            )
        }
        loading = false
    }

    @Composable
    override fun Ui(windowScope: AppWindowScope) {
        val scope = rememberCoroutineScope()
        Scaffold(
            modifier = Modifier.fillMaxSize(50f),
            bottomBar = {
                if (!loading) {
                    Column(
                        modifier = Modifier
                            .height(50.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (lastUpdated <= 0) {
                            defaultButton(
                                "Update Recent Series",
                                height = 35.dp,
                                width = 170.dp,
                                padding = PaddingValues(0.dp)
                            ) {
                                scope.launch {
                                    reloadRecentData()
                                }
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                defaultButton(
                                    "Update Recent Series",
                                    height = 35.dp,
                                    width = 170.dp,
                                    padding = PaddingValues(0.dp)
                                ) {
                                    scope.launch {
                                        reloadRecentData()
                                    }
                                }

                                Text(
                                    "Last Updated: ${Tools.dateFormatted(lastUpdated, false)}",
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
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
                SortedLazyColumn<RecentData>(
                    listOf(
                        HeaderItem(
                            "Name",
                            NAME_WEIGHT
                        ) { it.name },
                        HeaderItem(
                            "Date Found",
                            DATE_WEIGHT,
                            true
                        ) { it.dateFound },
                        HeaderItem(
                            "Image",
                            IMAGE_WEIGHT
                        )
                    ),
                    sort,
                    recentData,
                    key = { it.name + it.id },
                    modifier = Modifier.padding(
                        bottom = padding.calculateBottomPadding()
                    ).fillMaxSize()
                ) { _, item ->
                    RecentDataRow(
                        item,
                        windowScope
                    )
                }
            }
        }
        LaunchedEffect(Unit) {
            if (recentData.isEmpty()) {
                reloadRecentData()
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun RecentDataRow(
        recentData: RecentData,
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
            DefaultDropdownItem(
                "Download ${type(recentData)}",
                EvaIcons.Fill.Info
            ) {
                closeMenu()
                Core.openSeriesDetails(
                    recentData.link,
                    windowScope
                )
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
            Text(
                text = recentData.name,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 22.sp,
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.CenterVertically)
                    .weight(NAME_WEIGHT)
            )
            Divider()
            Text(
                text = Tools.dateFormatted(recentData.dateFound),
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.CenterVertically)
                    .weight(DATE_WEIGHT),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                textAlign = TextAlign.Center
            )
            Divider()
            val imagePath = BoxHelper.seriesImagesPath + Tools.titleForImages(recentData.name)
            DefaultImage(
                imagePath,
                recentData.imageLink,
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

    private fun type(data: RecentData): String {
        return if (data.isSeries) "Series" else "Episode"
    }

    fun clear() {
        recentData.clear()
        Defaults.WCO_RECENT_LAST_UPDATED.update(0L)
        lastUpdated = 0
    }

    override fun onClose() {}

    companion object {
        private val rowHeight = 130.dp
        private const val NAME_WEIGHT = 1f
        private const val IMAGE_WEIGHT = 0.35f
        private const val DATE_WEIGHT = 0.2f
    }
}