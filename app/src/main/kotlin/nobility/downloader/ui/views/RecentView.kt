package nobility.downloader.ui.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.fill.Info
import compose.icons.evaicons.fill.Trash
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
import nobility.downloader.core.settings.Save
import nobility.downloader.ui.components.*
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.utils.FrogLog
import nobility.downloader.utils.Tools

class RecentView: ViewPage {

    override val page = Page.RECENT

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
                    FrogLog.message(
                        "Found and added $added new recent data."
                    )
                } else {
                    FrogLog.error(
                        "Found no uncached recent data."
                    )
                }
            } else {
                FrogLog.error(
                    "Found no recent data."
                )
            }
            lastUpdated = Defaults.WCO_RECENT_LAST_UPDATED.long()
        } else {
            FrogLog.error(
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
                                    "Last Updated: ${Tools.dateAndTimeFormatted(lastUpdated, false)}",
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

                val lazyListState = rememberLazyListState()
                val fastScrolling by rememberScrollSpeed(lazyListState)

                LazyTable(
                    listOf(
                        ColumnItem(
                            "Name",
                            1f,
                            sortSelector = { it.name },
                            weightSaveKey = Save.R_N_WEIGHT
                        ) { _, recentData ->
                            Text(
                                text = recentData.name,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 22.sp
                            )
                        },
                        ColumnItem(
                            "Date Found",
                            0.2f,
                            true to true,
                            sortSelector = { it.dateFound },
                            weightSaveKey = Save.R_D_WEIGHT
                        ) { _, recentData ->
                            Text(
                                text = Tools.dateAndTimeFormatted(recentData.dateFound),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                textAlign = TextAlign.Center
                            )
                        },
                        ColumnItem(
                            "Image",
                            0.29f,
                            weightSaveKey = Save.R_I_WEIGHT
                        ) { _, recentData ->
                            val imagePath = remember {
                                BoxHelper.seriesImagesPath +
                                        Tools.titleForImages(recentData.name)
                            }
                            DefaultImage(
                                imagePath,
                                recentData.imageLink,
                                fastScrolling = fastScrolling,
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    ),
                    recentData,
                    scope = scope,
                    lazyListState = lazyListState,
                    key = { it.name + it.id },
                    modifier = Modifier.padding(
                        bottom = padding.calculateBottomPadding()
                    ).fillMaxSize(),
                    rowHeight = 120.dp,
                    sortSaveKey = Save.R_SORT
                ) { _, recentData ->
                    listOf(
                        DropdownOption(
                            "Download ${type(recentData)}",
                            EvaIcons.Fill.Info
                        ) {
                            Core.openSeriesDetails(
                                recentData.link,
                                windowScope
                            )
                        }
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

    private fun type(data: RecentData): String {
        return if (data.isSeries) "Series" else "Episode"
    }

    fun clear() {
        recentData.clear()
        Defaults.WCO_RECENT_LAST_UPDATED.update(0L)
        lastUpdated = 0
    }

    override val menuOptions: List<OverflowOption>
        get() = listOf(
            OverflowOption(
                EvaIcons.Fill.Trash,
                "Clear Recent Data",
            ) {
                DialogHelper.showConfirm(
                    "Are you sure you want to remove all the recent data?",
                    size = DpSize(300.dp, 200.dp),
                ) {
                    Core.taskScope.launch {
                        BoxHelper.shared.wcoRecentBox.removeAll()
                        Core.recentView.clear()
                    }
                }
            }
        )
}