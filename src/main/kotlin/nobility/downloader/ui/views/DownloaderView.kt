package nobility.downloader.ui.views

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.CursorDropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerMoveFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.fill.Refresh
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nobility.downloader.Page
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.Core
import nobility.downloader.core.Core.Companion.randomSeries
import nobility.downloader.core.Core.Companion.randomSeries2
import nobility.downloader.core.entities.Series
import nobility.downloader.core.entities.data.SeriesIdentity
import nobility.downloader.core.settings.Defaults
import nobility.downloader.ui.components.DefaultButton
import nobility.downloader.ui.components.DefaultDropdownItem
import nobility.downloader.ui.components.DefaultImage
import nobility.downloader.ui.components.DefaultTextField
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.utils.Constants.randomSeriesRowHeight
import nobility.downloader.utils.Tools

class DownloaderView : ViewPage {

    override val page = Page.DOWNLOADER

    @OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
    @Composable
    override fun ui(windowScope: AppWindowScope) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                Column(
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    DefaultTextField(
                        Core.currentUrl,
                        onValueChanged = {
                            Core.currentUrl = it
                        },
                        hint = Core.currentUrlHint,
                        singleLine = true,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                            .padding(start = 5.dp, end = 5.dp)
                            .fillMaxWidth().onKeyEvent {
                                if (it.key == Key.Enter) {
                                    Core.child.start()
                                    return@onKeyEvent true
                                }
                                return@onKeyEvent false
                            }.onFocusChanged { Core.currentUrlFocused = it.isFocused },
                        contextMenuItems = {
                            val items = mutableListOf<ContextMenuItem>()
                            if (Tools.clipboardString.isNotEmpty()) {
                                items.add(ContextMenuItem("Paste & Start") {
                                    Core.currentUrl = Tools.clipboardString
                                    Core.child.start()
                                })
                            }
                            if (Core.currentUrl.isNotEmpty()) {
                                items.add(ContextMenuItem("Clear") {
                                    Core.currentUrl = ""
                                })
                            }
                            items
                        }
                    )
                }
            }
        ) { padding ->

            val containerPosition = remember {
                mutableStateOf(Offset.Zero)
            }

            Column(
                modifier = Modifier.fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState(0))
                    .onGloballyPositioned {
                        val pos = it.localToWindow(Offset.Zero)
                        containerPosition.value = pos
                    }
            ) {
                if (randomSeries.isNotEmpty() && randomSeries2.isNotEmpty()) {

                    val hoveredItem = remember { mutableStateOf<Series?>(null) }
                    val popupOffset = remember { mutableStateOf(IntOffset.Zero) }

                    val rowInteraction = remember { MutableInteractionSource() }
                    val isHovering by rowInteraction.collectIsHoveredAsState()

                    var showMenu by remember {
                        mutableStateOf(false)
                    }
                    val closeMenu = { showMenu = false }
                    //todo add favorite option later
                    //todo experiment with a hover box for random series
                    CursorDropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { closeMenu() },
                        modifier = Modifier.background(
                            MaterialTheme.colorScheme.background
                        )
                    ) {
                        DefaultDropdownItem(
                            "Reload Random Series",
                            EvaIcons.Fill.Refresh
                        ) {
                            Core.reloadRandomSeries()
                            closeMenu()
                        }
                    }

                    /*hoveredItem.value?.let { item ->
                        Popup(
                            alignment = Alignment.TopStart,
                            offset = popupOffset.value,
                            onDismissRequest = {}
                        ) {
                            Column(
                                Modifier.size(300.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surface,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(8.dp)
                                    )
                            ) {

                                Text(
                                    text = "(${popupOffset.value.x}, ${popupOffset.value.y})",
                                    color = Color.Red
                                )

                            }
                        }
                    }*/

                    val seriesStateList = rememberLazyListState()
                    val seriesStateList2 = rememberLazyListState()
                    val coroutineScope = rememberCoroutineScope()

                    LazyRow(
                        Modifier.fillMaxWidth()
                            .height(randomSeriesRowHeight)
                            .padding(top = 8.dp)
                            .draggable(
                                rememberDraggableState {
                                    coroutineScope.launch {
                                        seriesStateList.scrollBy(-it)
                                        seriesStateList2.scrollBy(-it)
                                    }
                                },
                                orientation = Orientation.Horizontal
                            ).hoverable(rowInteraction),
                        state = seriesStateList
                    ) {
                        items(
                            randomSeries,
                            key = { it.name + it.id }
                        ) { series ->
                            val interaction = remember { MutableInteractionSource() }
                            val hovered by interaction.collectIsHoveredAsState()
                            val clicked by interaction.collectIsPressedAsState()
                            val size = if (hovered && !clicked)
                                125.dp
                            else
                                randomSeriesRowHeight
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(
                                    animateDpAsState(
                                        size,
                                        spring(
                                            0.7f,
                                            Spring.StiffnessMediumLow
                                        )
                                    ).value
                                ).background(
                                    Color.Transparent,
                                    RectangleShape
                                ).border(
                                    1.dp,
                                    color = if (series.seriesIdentity == SeriesIdentity.CARTOON)
                                        MaterialTheme.colorScheme.onSurface
                                    else
                                        MaterialTheme.colorScheme.primary,
                                    RectangleShape
                                ).hoverable(interaction)
                                    .pointerMoveFilter(
                                        onMove = { offset ->
                                            popupOffset.value = IntOffset(
                                                (containerPosition.value.x + offset.x + 16).toInt(),
                                                (containerPosition.value.y + offset.y - 100).toInt()
                                            )
                                            hoveredItem.value = series
                                            false
                                        },
                                        onExit = {
                                            hoveredItem.value = null
                                            false
                                        }
                                    )

                            ) {
                                DefaultImage(
                                    series.imagePath,
                                    contentScale = ContentScale.FillBounds,
                                    onClick = {
                                        Core.openDownloadConfirm(series.asToDownload)
                                    },
                                    onRightClick = {
                                        showMenu = true
                                    }
                                )
                            }
                        }
                    }

                    LazyRow(
                        Modifier.fillMaxWidth()
                            .height(randomSeriesRowHeight)
                            .draggable(
                                rememberDraggableState {
                                    coroutineScope.launch {
                                        seriesStateList.scrollBy(-it)
                                        seriesStateList2.scrollBy(-it)
                                    }
                                },
                                orientation = Orientation.Horizontal
                            ).hoverable(rowInteraction),
                        state = seriesStateList2
                    ) {
                        items(
                            randomSeries2,
                            key = { it.name + it.id }
                        ) { series ->
                            val interaction = remember { MutableInteractionSource() }
                            val hovered by interaction.collectIsHoveredAsState()
                            val clicked by interaction.collectIsPressedAsState()
                            val size = if (hovered && !clicked)
                                125.dp
                            else
                                randomSeriesRowHeight
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(
                                    animateDpAsState(
                                        size,
                                        spring(
                                            0.7f,
                                            Spring.StiffnessMediumLow
                                        )
                                    ).value
                                ).background(
                                    Color.Transparent,
                                    RectangleShape
                                ).border(
                                    1.dp,
                                    color = if (series.seriesIdentity == SeriesIdentity.CARTOON)
                                        MaterialTheme.colorScheme.onSurface
                                    else
                                        MaterialTheme.colorScheme.primary,
                                    RectangleShape
                                ).hoverable(interaction)
                                    .pointerMoveFilter(
                                        onMove = { offset ->
                                            popupOffset.value = IntOffset(
                                                (containerPosition.value.x + offset.x + 16).toInt(),
                                                (containerPosition.value.y + offset.y - 100).toInt()
                                            )
                                            hoveredItem.value = series
                                            false
                                        },
                                        onExit = {
                                            hoveredItem.value = null
                                            false
                                        }
                                    )

                            ) {
                                DefaultImage(
                                    series.imagePath,
                                    contentScale = ContentScale.FillBounds,
                                    onClick = {
                                        Core.openDownloadConfirm(series.asToDownload)
                                    },
                                    onRightClick = {
                                        showMenu = true
                                    }
                                )
                            }
                        }
                    }

                    LaunchedEffect(Unit) {
                        delay(5000)
                        while (isActive) {
                            if (!Defaults.AUTO_SCROLL_RANDOM_SERIES.boolean()) {
                                delay(5000)
                                continue
                            }
                            if (isHovering) {
                                delay(5000)
                                continue
                            }
                            launch {
                                if (seriesStateList.canScrollForward) {
                                    seriesStateList.animateScrollBy(200f)
                                } else if (seriesStateList.canScrollBackward) {
                                    seriesStateList.animateScrollToItem(0)
                                }
                            }
                            launch {
                                if (seriesStateList2.canScrollForward) {
                                    seriesStateList2.animateScrollBy(200f)
                                } else if (seriesStateList2.canScrollBackward) {
                                    seriesStateList2.animateScrollToItem(0)
                                }
                            }
                            delay(3500)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.align(Alignment.CenterHorizontally)
                        .padding(bottom = 4.dp)
                        .fillMaxWidth(0.5f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DefaultButton(
                        "Start",
                        Modifier.weight(1f)
                            .height(60.dp),
                        fontSize = 16.sp,
                        enabled = Core.startButtonEnabled
                    ) {
                        Core.child.start()
                    }
                    Spacer(Modifier.width(8.dp))
                    DefaultButton(
                        "Stop",
                        Modifier.weight(1f)
                            .height(60.dp),
                        fontSize = 16.sp,
                        enabled = Core.stopButtonEnabled
                    ) {
                        Core.child.stop()
                        Core.child.forceStopped = true
                    }
                }
                Core.console.textField(windowScope)
            }
        }
    }

    override fun onClose() {

    }
}