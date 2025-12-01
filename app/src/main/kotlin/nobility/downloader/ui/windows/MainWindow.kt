package nobility.downloader.ui.windows

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.fill.ColorPalette
import compose.icons.evaicons.fill.MoreVertical
import kotlinx.coroutines.launch
import nobility.downloader.Page
import nobility.downloader.Page.Companion.viewPage
import nobility.downloader.core.Core
import nobility.downloader.core.scraper.video_download.Functions
import nobility.downloader.ui.components.*
import nobility.downloader.ui.theme.CoreTheme
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.utils.Constants
import nobility.downloader.utils.Constants.mediumIconSize
import nobility.downloader.utils.Theme
import nobility.downloader.utils.tone
import kotlin.system.exitProcess

@Composable
fun MainWindow(scope: AppWindowScope) {
    UiWrapper {
        when (Core.currentPage) {
            Page.DOWNLOADER -> Core.downloaderView.Ui(scope)
            Page.DOWNLOADS -> Core.downloadsView.Ui(scope)
            Page.SETTINGS -> Core.settingsView.Ui(scope)
            Page.HISTORY -> Core.historyView.Ui(scope)
            Page.RECENT -> Core.recentView.Ui(scope)
            Page.ERROR_CONSOLE -> Core.errorConsoleView.Ui(scope)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UiWrapper(
    content: @Composable () -> Unit
) {
    var showFileMenu by remember {
        mutableStateOf(false)
    }
    val closeMenu = { showFileMenu = false }
    CoreTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    modifier = Modifier.height(Constants.topBarHeight),
                    title = {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = Core.currentPage.title,
                                modifier = Modifier.width(170.dp),
                                fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                                color = MaterialTheme.colorScheme.onPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Bold
                            )
                            TooltipIconButton(
                                "Theme Editor",
                                icon = EvaIcons.Fill.ColorPalette,
                                iconSize = mediumIconSize,
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                ThemeEditorWindow.open()
                            }
                            val coroutineScope = rememberCoroutineScope()
                            val listState = rememberLazyListState()
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.align(Alignment.Bottom)
                                    .draggable(
                                        state = rememberDraggableState {
                                            coroutineScope.launch {
                                                listState.scrollBy(-it)
                                            }
                                        },
                                        orientation = Orientation.Horizontal
                                    )
                                    .horizontalWheelScroll { scroll ->
                                        coroutineScope.launch {
                                            listState.scrollBy(scroll)
                                        }
                                    },
                                state = listState
                            ) {
                                items(
                                    Page.entries,
                                    key = { it.name }
                                ) { page ->
                                    when (page) {
                                        Page.DOWNLOADS -> {
                                            BadgedBox(
                                                badge = {
                                                    if (Core.child.downloadThread.downloadsInQueue.value > 0) {
                                                        Badge(
                                                            containerColor = Color.Red,
                                                            modifier = Modifier.offset(y = 2.dp, x = (-8).dp)
                                                        ) {
                                                            Text(
                                                                Core.child.downloadThread.downloadsInQueue.value.toString(),
                                                                overflow = TextOverflow.Clip,
                                                                color = Color.White,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 10.sp
                                                            )
                                                        }
                                                    }
                                                }
                                            ) {
                                                PageButton(page)
                                            }
                                        }
                                        Page.ERROR_CONSOLE -> {
                                            BadgedBox(
                                                badge = {
                                                    if (Core.errorConsole.consoleState.unreadErrors) {
                                                        Badge(
                                                            containerColor = Color.Red,
                                                            modifier = Modifier.offset(y = 2.dp, x = (-8).dp)
                                                        ) {}
                                                    }
                                                }
                                            ) {
                                                PageButton(page)
                                            }
                                        }
                                        else -> {
                                            PageButton(page)
                                        }
                                    }
                                }
                                item(key = {"database-button"}) {
                                    TabButton(
                                        "Video Database 📁"
                                    ) {
                                        Core.openWco()
                                    }
                                }
                            }
                        }
                    },
                    actions = {
                        OverflowRow(
                            modifier = Modifier.width(175.dp),
                            items = Core.currentPage.viewPage().menuOptions,
                            itemContent = { option ->
                                if (option.badge != null) {
                                    BadgedBox(
                                        badge = option.badge
                                    ) {
                                        TooltipIconButton(
                                            option.tooltip,
                                            icon = option.icon,
                                            iconSize = mediumIconSize,
                                            iconColor = option.contentColor?: MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            option.onClick()
                                        }
                                    }
                                } else {
                                    TooltipIconButton(
                                        option.tooltip,
                                        icon = option.icon,
                                        iconSize = mediumIconSize,
                                        iconColor = option.contentColor ?: MaterialTheme.colorScheme.onPrimary
                                    ) {
                                        option.onClick()
                                    }
                                }
                            }
                        ) { options ->
                            TooltipIconButton(
                                "Options",
                                EvaIcons.Fill.MoreVertical,
                                mediumIconSize,
                                onClick = { showFileMenu = !showFileMenu }
                            )
                            DefaultCursorDropdownMenu(
                                showFileMenu,
                                options.map {
                                    DropdownOption(
                                        it.tooltip,
                                        it.modifier,
                                        it.icon,
                                        it.visible,
                                        it.contentColor,
                                        it.onClick
                                    )
                                }
                            ) { closeMenu() }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Theme.colorScheme.primary
                            .tone(65.0)
                    )
                )
            },
            content = { paddingValues ->
                if (Core.child.shutdownExecuted) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .zIndex(10f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.wrapContentHeight()
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(10.dp)
                                ).border(
                                    1.dp,
                                    MaterialTheme.colorScheme.error,
                                    RoundedCornerShape(10.dp)
                                )
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(80.dp)
                                    .padding(top = 15.dp, bottom = 15.dp),
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                """
                                        Shutdown Executed.
                                        Please wait patiently while the program kills all running drivers.
                                        
                                        Drivers Killed: ${Core.child.shutdownProgressIndex}/${Core.child.shutdownProgressTotal}
                                    """.trimIndent(),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(10.dp)
                            )
                            defaultButton(
                                "Force Shutdown",
                                height = 40.dp,
                                width = 140.dp,
                                padding = PaddingValues(
                                    top = 10.dp,
                                    bottom = 10.dp
                                ),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                fontColor = MaterialTheme.colorScheme.onError
                            ) {
                                Functions.killChromeProcesses()
                                exitProcess(if (Core.child.triggeredUpdate) 100 else 2)
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier.padding(paddingValues)
                ) {
                    content()
                }
            }
        )
    }
}

