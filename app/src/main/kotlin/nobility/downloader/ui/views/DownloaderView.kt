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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CursorDropdownMenu
import androidx.compose.material.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerMoveFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.fill.ArrowLeft
import compose.icons.evaicons.fill.ArrowRight
import compose.icons.evaicons.fill.Close
import compose.icons.evaicons.fill.Refresh
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nobility.downloader.Page
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.BoxHelper.Companion.update
import nobility.downloader.core.Core
import nobility.downloader.core.Core.Companion.randomSeries
import nobility.downloader.core.Core.Companion.randomSeries2
import nobility.downloader.core.entities.Series
import nobility.downloader.core.entities.data.SeriesIdentity
import nobility.downloader.core.settings.Defaults
import nobility.downloader.ui.components.*
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.utils.Constants.mediumIconSize
import nobility.downloader.utils.Constants.randomSeriesRowHeight
import nobility.downloader.utils.Tools

class DownloaderView : ViewPage {

    override val page = Page.DOWNLOADER

    @OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
    @Composable
    override fun Ui(windowScope: AppWindowScope) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                val focusRequester = remember { FocusRequester() }
                DefaultTextField(
                    Core.currentUrl,
                    onValueChanged = {
                        Core.currentUrl = it
                        Defaults.LAST_DOWNLOAD.update(it)
                    },
                    hint = Core.currentUrlHint,
                    singleLine = true,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .fillMaxWidth().onKeyEvent {
                            if (it.key == Key.Enter) {
                                Core.child.start()
                                return@onKeyEvent true
                            }
                            return@onKeyEvent false
                        }.onFocusChanged { Core.currentUrlFocused = it.isFocused },
                    focusRequester = focusRequester,
                    requestFocus = true,
                    trailingIcon = {
                        if (Core.currentUrl.isNotEmpty()) {
                            DefaultIcon(
                                EvaIcons.Fill.Close,
                                Modifier.size(mediumIconSize)
                                    .pointerHoverIcon(PointerIcon.Hand),
                                iconColor = MaterialTheme.colorScheme.primary,
                                onClick = {
                                    Core.currentUrl = ""
                                    Defaults.LAST_DOWNLOAD.update("")
                                    focusRequester.requestFocus()
                                }
                            )
                        }
                    },
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
        ) { padding ->

            val popupOffset = remember { mutableStateOf(Offset.Zero) }

            BoxWithConstraints(
                Modifier.fillMaxSize()
                    .padding(
                        bottom = padding.calculateBottomPadding(),
                        top = padding.calculateTopPadding()
                    )
                    .pointerMoveFilter(
                        onMove = { offset ->
                            popupOffset.value = offset
                            false
                        }
                    )
            ) {

                val density = LocalDensity.current
                val maxHeight = maxHeight
                val boxWidthPx = with(density) { maxWidth.toPx() }
                val boxHeightPx = with(density) { maxHeight.toPx() }

                val popupSizeDp = DpSize(250.dp, 220.dp)
                val popupSizePx = with(density) {
                    popupSizeDp.toSize()
                }

                val extraOffsetDp = DpOffset(12.dp, 24.dp)
                val extraOffsetPx = with(density) {
                    Offset(extraOffsetDp.x.toPx(), extraOffsetDp.y.toPx())
                }

                val paddedOffsetPx = Offset(
                    x = popupOffset.value.x + extraOffsetPx.x,
                    y = popupOffset.value.y + extraOffsetPx.y
                )

                val clampedOffsetPx = Offset(
                    x = paddedOffsetPx.x.coerceIn(0f, maxOf(0f, boxWidthPx - popupSizePx.width)),
                    y = paddedOffsetPx.y.coerceIn(0f, maxOf(0f, boxHeightPx - popupSizePx.height))
                )

                val clampedDpOffset = with(density) {
                    DpOffset(clampedOffsetPx.x.toDp(), clampedOffsetPx.y.toDp())
                }

                val hoveredSeries = remember { mutableStateOf<Series?>(null) }

                hoveredSeries.value?.let { series ->
                    Box(
                        Modifier.size(popupSizeDp)
                            .zIndex(10f)
                            .offset(clampedDpOffset.x, clampedDpOffset.y)
                    ) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(8.dp)
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            DefaultImage(
                                series.imagePath,
                                modifier = Modifier.fillMaxWidth()
                                    .fillMaxHeight(0.8f),
                                contentScale = ContentScale.FillBounds
                            )
                            Text(
                                text = series.name,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                                    .padding(4.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (randomSeries.isNotEmpty() && randomSeries2.isNotEmpty()) {

                        val seriesStateList = rememberLazyListState()
                        val seriesStateList2 = rememberLazyListState()
                        val coroutineScope = rememberCoroutineScope()
                        val rowInteraction = remember { MutableInteractionSource() }
                        val isHovering by rowInteraction.collectIsHoveredAsState()

                        var showMenu by remember {
                            mutableStateOf(false)
                        }
                        val closeMenu = { showMenu = false }
                        var scrolled = remember { false }

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
                                closeMenu()
                                Core.reloadRandomSeries()
                            }
                            DefaultDropdownItem(
                                "Scroll To Start",
                                EvaIcons.Fill.ArrowLeft
                            ) {
                                closeMenu()
                                scrolled = true
                                coroutineScope.launch {
                                    seriesStateList.scrollToItem(0)
                                    seriesStateList2.scrollToItem(0)
                                }
                            }
                            DefaultDropdownItem(
                                "Scroll To End",
                                EvaIcons.Fill.ArrowRight
                            ) {
                                closeMenu()
                                scrolled = true
                                coroutineScope.launch {
                                    seriesStateList.scrollToItem(
                                        seriesStateList.layoutInfo.totalItemsCount
                                    )
                                    seriesStateList2.scrollToItem(
                                        seriesStateList2.layoutInfo.totalItemsCount
                                    )
                                }
                            }
                        }

                        LazyRow(
                            Modifier.fillMaxWidth()
                                .height(randomSeriesRowHeight)
                                .padding(top = 16.dp)
                                .draggable(
                                    rememberDraggableState {
                                        coroutineScope.launch {
                                            seriesStateList.scrollBy(-it)
                                            seriesStateList2.scrollBy(-it)
                                        }
                                    },
                                    orientation = Orientation.Horizontal
                                ).hoverable(rowInteraction)
                                .horizontalWheelScroll { scroll ->
                                    coroutineScope.launch {
                                        seriesStateList.scrollBy(scroll)
                                        seriesStateList2.scrollBy(scroll)
                                    }
                                },
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
                                                hoveredSeries.value = series
                                                false
                                            },
                                            onExit = {
                                                hoveredSeries.value = null
                                                false
                                            }
                                        )

                                ) {
                                    DefaultImage(
                                        series.imagePath,
                                        contentScale = ContentScale.FillBounds,
                                        onClick = {
                                            Core.openSeriesDetails(
                                                series.slug,
                                                windowScope
                                            )
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
                                ).hoverable(rowInteraction)
                                .padding(bottom = 16.dp)
                                .horizontalWheelScroll { scroll ->
                                    coroutineScope.launch {
                                        seriesStateList.scrollBy(scroll)
                                        seriesStateList2.scrollBy(scroll)
                                    }
                                },
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
                                            onMove = {
                                                hoveredSeries.value = series
                                                false
                                            },
                                            onExit = {
                                                hoveredSeries.value = null
                                                false
                                            }
                                        )

                                ) {
                                    DefaultImage(
                                        series.imagePath,
                                        contentScale = ContentScale.FillBounds,
                                        onClick = {
                                            Core.openSeriesDetails(
                                                series.slug,
                                                windowScope
                                            )
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
                                if (scrolled) {
                                    delay(5000)
                                    scrolled = false
                                    continue
                                }
                                launch {
                                    if (seriesStateList.canScrollForward) {
                                        seriesStateList.animateScrollBy(250f)
                                    } else if (seriesStateList.canScrollBackward) {
                                        seriesStateList.animateScrollToItem(0)
                                    }
                                }
                                launch {
                                    if (seriesStateList2.canScrollForward) {
                                        seriesStateList2.animateScrollBy(250f)
                                    } else if (seriesStateList2.canScrollBackward) {
                                        seriesStateList2.animateScrollToItem(0)
                                    }
                                }
                                delay(3500)
                            }
                        }
                    }
                    Row(
                        Modifier.align(Alignment.CenterHorizontally)
                            .padding(vertical = 16.dp)
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
                    Core.console.ConsoleTextField(
                        windowScope,
                        modifier = Modifier.fillMaxWidth()
                            .weight(1f, true)
                    )
                }
            }
        }
    }

    override fun onClose() {

    }
}