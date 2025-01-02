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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nobility.downloader.Page
import nobility.downloader.core.Core
import nobility.downloader.core.Core.Companion.randomSeries
import nobility.downloader.core.Core.Companion.randomSeries2
import nobility.downloader.core.entities.data.SeriesIdentity
import nobility.downloader.ui.components.defaultButton
import nobility.downloader.ui.components.defaultTextField
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.utils.AppInfo
import nobility.downloader.utils.Constants
import nobility.downloader.utils.Constants.randomSeriesRowHeight
import nobility.downloader.utils.ImageUtils
import nobility.downloader.utils.Tools

class DownloaderView: ViewPage {

    override val page = Page.DOWNLOADER

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun ui(windowScope: AppWindowScope) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                Column(
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    defaultTextField(
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
            val rowInteraction = remember { MutableInteractionSource() }
            val isHovering by rowInteraction.collectIsHoveredAsState()

            Column(
                modifier = Modifier.fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState(0))
            ) {
                if (randomSeries.isNotEmpty() && randomSeries2.isNotEmpty()) {
                    val seriesStateList = rememberScrollState()
                    val seriesStateList2 = rememberScrollState()
                    val coroutineScope = rememberCoroutineScope()
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                        modifier = Modifier.fillMaxWidth()
                            .height(randomSeriesRowHeight)
                            .padding(top = 10.dp)
                            .draggable(
                                state = rememberDraggableState {
                                    coroutineScope.launch {
                                        seriesStateList.scrollBy(-it)
                                        seriesStateList2.scrollBy(-it)
                                    }
                                },
                                orientation = Orientation.Horizontal
                            ).hoverable(rowInteraction)
                            .horizontalScroll(seriesStateList)
                    ) {
                        randomSeries.forEach {
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
                                        spring(0.7f, Spring.StiffnessMediumLow)
                                    ).value
                                ).background(
                                    Color.Transparent,
                                    RectangleShape
                                ).border(
                                    1.dp,
                                    color = if (it.seriesIdentity == SeriesIdentity.CARTOON)
                                        MaterialTheme.colorScheme.onSurface
                                    else
                                        MaterialTheme.colorScheme.primary,
                                    RectangleShape
                                ).hoverable(interaction)
                            ) {
                                val bitmap by remember {
                                    mutableStateOf(ImageUtils.loadImageFromFilePath(it.imagePath))
                                }
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = it.name,
                                    contentScale = ContentScale.FillBounds,
                                    modifier = Modifier.fillMaxSize()
                                        .onClick {
                                            Core.openDownloadConfirm(it.asToDownload)
                                        }.pointerHoverIcon(PointerIcon.Hand)
                                )
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                        modifier = Modifier.fillMaxWidth()
                            .height(randomSeriesRowHeight)
                            .padding(top = 1.dp, bottom = 10.dp)
                            .draggable(
                                state = rememberDraggableState {
                                    coroutineScope.launch {
                                        seriesStateList.scrollBy(-it)
                                        seriesStateList2.scrollBy(-it)
                                    }
                                },
                                orientation = Orientation.Horizontal
                            ).hoverable(rowInteraction)
                            .horizontalScroll(seriesStateList2)
                    ) {
                        randomSeries2.forEach {
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
                                        spring(0.7f, Spring.StiffnessMediumLow)
                                    ).value
                                ).background(
                                    Color.Transparent,
                                    RectangleShape
                                ).border(
                                    1.dp,
                                    color = if (it.seriesIdentity == SeriesIdentity.CARTOON)
                                        MaterialTheme.colorScheme.onSurface
                                    else
                                        MaterialTheme.colorScheme.primary,
                                    RectangleShape
                                ).hoverable(interaction)
                            ) {
                                val bitmap by remember {
                                    mutableStateOf(ImageUtils.loadImageFromFilePath(it.imagePath))
                                }
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = it.name,
                                    contentScale = ContentScale.FillBounds,
                                    modifier = Modifier.fillMaxSize()
                                        .onClick {
                                            Core.openDownloadConfirm(it.asToDownload)
                                        }.pointerHoverIcon(PointerIcon.Hand)
                                )
                            }
                        }
                    }
                    @Suppress("KotlinConstantConditions")
                    if (AppInfo.AUTO_SCROLL_RANDOM_SERIES) {
                        LaunchedEffect(Unit) {
                            delay(5000)
                            while (isActive) {
                                if (isHovering) {
                                    delay(5000)
                                    continue
                                }
                                if (seriesStateList.canScrollForward) {
                                    seriesStateList.animateScrollBy(150f)
                                } else if (seriesStateList.canScrollBackward) {
                                    seriesStateList.animateScrollTo(0)
                                }
                                delay(3000)
                            }
                        }
                        LaunchedEffect(Unit) {
                            delay(6000)
                            while (isActive) {
                                if (isHovering) {
                                    delay(5000)
                                    continue
                                }
                                if (seriesStateList2.canScrollForward) {
                                    seriesStateList2.animateScrollBy(150f)
                                } else if (seriesStateList2.canScrollBackward) {
                                    seriesStateList2.animateScrollTo(0)
                                }
                                delay(4000)
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .padding(bottom = 4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.align(Alignment.Center)
                            .offset(y = Constants.mediumIconSize)
                    ) {
                        val buttonWidth = 150.dp
                        val buttonHeight = 50.dp
                        defaultButton(
                            "Start",
                            width = Dp.Unspecified,
                            height = Dp.Unspecified,
                            modifier = Modifier.defaultMinSize(
                                minWidth = buttonWidth,
                                minHeight = buttonHeight
                            ),
                            fontSize = 13.sp,
                            enabled = Core.startButtonEnabled
                        ) {
                            Core.child.start()
                        }
                        defaultButton(
                            "Stop",
                            width = Dp.Unspecified,
                            height = Dp.Unspecified,
                            modifier = Modifier.defaultMinSize(
                                minWidth = buttonWidth,
                                minHeight = buttonHeight
                            ),
                            fontSize = 13.sp,
                            enabled = Core.stopButtonEnabled
                        ) {
                            Core.child.stop()
                        }
                    }
                }
                Core.console.textField(windowScope)
            }
        }
    }

    override fun onClose() {

    }

}