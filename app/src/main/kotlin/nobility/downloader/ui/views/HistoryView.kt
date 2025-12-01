package nobility.downloader.ui.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.Outline
import compose.icons.evaicons.fill.Info
import compose.icons.evaicons.fill.Search
import compose.icons.evaicons.fill.Star
import compose.icons.evaicons.fill.Trash
import compose.icons.evaicons.outline.Star
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import nobility.downloader.Page
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.BoxMaker
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Series
import nobility.downloader.core.entities.SeriesHistory
import nobility.downloader.core.settings.Save
import nobility.downloader.ui.components.*
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.ui.windows.EpisodeUpdaterWindow
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.utils.Constants.bottomBarHeight
import nobility.downloader.utils.Tools

class HistoryView : ViewPage {

    override val page = Page.HISTORY

    private val downloadScope = CoroutineScope(Dispatchers.IO)
    private var loading by mutableStateOf(false)

    data class SeriesData(
        val series: Series,
        val history: SeriesHistory
    )

    private val seriesDatas = mutableStateListOf<SeriesData>()

    private fun loadHistoryData() {
        BoxHelper.shared.wcoBoxStore.callInReadTx {
            BoxHelper.shared.historyBox.all.forEach {
                val series = BoxHelper.seriesForSlug(it.seriesSlug)
                if (series != null) {
                    val ignored = BoxHelper.isSeriesIgnored(series)
                    if (!ignored) {
                        seriesDatas.add(
                            SeriesData(
                                series,
                                it
                            )
                        )
                    }
                }
            }
        }
        BoxHelper.shared.wcoBoxStore.closeThreadResources()
    }

    override fun onClose() {
        seriesDatas.clear()
        downloadScope.cancel()
    }

    @Composable
    override fun Ui(windowScope: AppWindowScope) {
        Scaffold(
            modifier = Modifier.fillMaxSize(50f),
            bottomBar = {
                Column(
                    modifier = Modifier.fillMaxWidth().height(bottomBarHeight)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                            .padding(8.dp)
                    ) {
                        DefaultButton(
                            "Clear History",
                            modifier = Modifier.height(40.dp)
                                .width(200.dp)
                        ) {
                            DialogHelper.showConfirm(
                                "Are you sure you want to delete your download history?",
                                onConfirmTitle = "Clear History"
                            ) {
                                if (seriesDatas.isEmpty()) {
                                    windowScope.showToast("There's no history to clear.")
                                    return@showConfirm
                                }
                                BoxHelper.shared.historyBox.removeAll()
                                seriesDatas.clear()
                                windowScope.showToast("History successfully cleared.")
                            }
                        }
                        DefaultButton(
                            "Check For New Episodes",
                            modifier = Modifier.height(40.dp)
                                .width(200.dp)
                        ) {
                            if (seriesDatas.isEmpty()) {
                                windowScope.showToast("There's no history to check.")
                                return@DefaultButton
                            }
                            if (!Core.child.canSoftStart()) {
                                windowScope.showToast("Failed to check for new episodes. Check the console.")
                                return@DefaultButton
                            }
                            val window = EpisodeUpdaterWindow(
                                seriesDatas.sortedByDescending { it.history.dateAdded }
                                    .map { it.series },
                                "History"
                            )
                            window.open()
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
                var forceUpdateName by mutableStateOf(0)

                LazyTable(
                    listOf(
                        ColumnItem(
                            "Name",
                            5f,
                            weightSaveKey = Save.H_N_WEIGHT,
                            contentAlignment = Alignment.CenterStart,
                            sortSelector = { it.series.name }
                        ) { _, seriesData ->
                            key(forceUpdateName) {
                                Box(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    val favorited = BoxHelper
                                        .isSeriesFavorited(seriesData.series)

                                    if (favorited) {
                                        DefaultIcon(
                                            EvaIcons.Fill.Star,
                                            iconColor = Color.Yellow,
                                            modifier = Modifier
                                                .padding(start = 4.dp, top = 8.dp)
                                                .align(Alignment.TopStart)
                                        )
                                    }
                                    Text(
                                        text = seriesData.series.name,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .align(Alignment.CenterStart)
                                    )
                                }
                            }
                        },
                        ColumnItem(
                            "Date Added",
                            1.1f,
                            true to true,
                            weightSaveKey = Save.H_D_WEIGHT,
                            sortSelector = { it.history.dateAdded }
                        ) { _, seriesData ->
                            Text(
                                text = Tools.dateAndTimeFormatted(seriesData.history.dateAdded),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                textAlign = TextAlign.Center
                            )
                        },
                        ColumnItem(
                            "Episodes",
                            1f,
                            weightSaveKey = Save.H_E_WEIGHT,
                            sortSelector = { it.series.episodesSize }
                        ) { _, seriesData ->
                            Text(
                                text = seriesData.series.episodesSize.toString(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                textAlign = TextAlign.Center
                            )
                        },
                        ColumnItem(
                            "Image",
                            1.9f,
                            weightSaveKey = Save.H_I_WEIGHT
                        ) { _, seriesData ->
                            DefaultImage(
                                seriesData.series.imagePath,
                                seriesData.series.imageLink,
                                fastScrolling = fastScrolling,
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    ),
                    seriesDatas,
                    lazyListState = lazyListState,
                    key = { it.series.slug + it.series.id },
                    modifier = Modifier.padding(
                        bottom = padding.calculateBottomPadding()
                    ).fillMaxSize(),
                    rowHeight = 120.dp,
                    sortSaveKey = Save.H_SORT,
                ) { _, seriesData ->

                    val favorited = BoxHelper.isSeriesFavorited(seriesData.series)

                    listOf(
                        DropdownOption(
                            "Series Details",
                            EvaIcons.Fill.Info
                        ) {
                            Core.openSeriesDetails(
                                seriesData.series.slug,
                                windowScope
                            )
                        },
                        DropdownOption(
                            "Check For New Episodes",
                            EvaIcons.Fill.Search
                        ) {
                            if (!Core.child.canSoftStart()) {
                                windowScope.showToast(
                                    "Failed to check for new episodes. Check the console."
                                )
                                return@DropdownOption
                            }
                            val window = EpisodeUpdaterWindow(
                                seriesData.series,
                                "History: ${seriesData.series.name}"
                            )
                            window.open()
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
                                BoxHelper.removeSeriesFavorite(seriesData.series.slug)
                            } else {
                                BoxMaker.makeFavorite(seriesData.series.slug)
                            }
                            forceUpdateName++
                        },
                        DropdownOption(
                            "Remove From History",
                            EvaIcons.Fill.Trash
                        ) {
                            BoxHelper.shared.historyBox.remove(seriesData.history)
                            seriesDatas.remove(seriesData)
                        }
                    )
                }
            }
        }
        LaunchedEffect(Unit) {
            loading = true
            loadHistoryData()
            loading = false
        }
    }
}