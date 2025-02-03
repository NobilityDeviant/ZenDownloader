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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.fill.*
import kotlinx.coroutines.launch
import nobility.downloader.Page
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.BoxHelper.Companion.update
import nobility.downloader.core.Core
import nobility.downloader.core.settings.Defaults
import nobility.downloader.ui.components.*
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.ui.theme.coreTheme
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.*
import nobility.downloader.utils.Constants.mediumIconSize

@Composable
fun mainWindow(scope: AppWindowScope) {
    uiWrapper(scope) {
        when (Core.currentPage) {
            Page.DOWNLOADER -> Core.downloaderView.ui(scope)
            Page.DOWNLOADS -> Core.downloadsView.ui(scope)
            Page.SETTINGS -> Core.settingsView.ui(scope)
            Page.HISTORY -> Core.historyView.ui(scope)
            Page.RECENT -> Core.recentView.ui(scope)
            Page.ERROR_CONSOLE -> Core.errorConsoleView.ui(scope)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun uiWrapper(
    windowScope: AppWindowScope,
    content: @Composable (PaddingValues) -> Unit
) {
    var showFileMenu by remember {
        mutableStateOf(false)
    }
    val scope = rememberCoroutineScope()
    val closeMenu = { showFileMenu = false }
    coreTheme {
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
                                    ),
                                state = listState
                            ) {
                                items(
                                    Page.entries,
                                    key = { it.name }
                                ) { page ->
                                    if (page == Page.DOWNLOADS) {
                                        BadgedBox(
                                            badge = {
                                                if (Core.child.downloadsInProgress.value > 0) {
                                                    Badge(
                                                        containerColor = Color.Red,
                                                        modifier = Modifier.offset(y = 2.dp, x = (-8).dp)
                                                    ) {
                                                        Text(
                                                            Core.child.downloadsInProgress.value.toString(),
                                                            overflow = TextOverflow.Clip,
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                }
                                            }
                                        ) {
                                            pageButton(page)
                                        }
                                    } else if (page == Page.ERROR_CONSOLE) {
                                        BadgedBox(
                                            badge = {
                                                if (Core.errorConsole.unreadErrors) {
                                                    Badge(
                                                        containerColor = Color.Red,
                                                        modifier = Modifier.offset(y = 2.dp, x = (-8).dp)
                                                    ) {}
                                                }
                                            }
                                        ) {
                                            pageButton(page)
                                        }
                                    } else {
                                        pageButton(page)
                                    }
                                }
                                item(key = {"database-button"}) {
                                    tabButton("Video Database") {
                                        Core.openWco()
                                    }
                                }
                            }
                        }
                    },
                    actions = {
                        if (Core.currentPage == Page.DOWNLOADS) {
                            tooltipIconButton(
                                "Open Download Folder",
                                icon = EvaIcons.Fill.Folder
                            ) {
                                Tools.openFile(
                                    Defaults.SAVE_FOLDER.string()
                                )
                            }
                        } else if (Core.currentPage == Page.SETTINGS) {
                            tooltipIconButton(
                                if (!Core.darkMode.value) "Dark Mode" else "Light Mode",
                                if (!Core.darkMode.value) EvaIcons.Fill.Moon else EvaIcons.Fill.Sun,
                                mediumIconSize,
                                onClick = {
                                    Core.darkMode.value = Core.darkMode.value.not()
                                    Defaults.DARK_MODE.update(
                                        Defaults.DARK_MODE.boolean().not()
                                    )
                                },
                                spacePosition = SpacePosition.START,
                                space = 10.dp
                            )
                        }

                        var options = mutableStateListOf<@Composable () -> Unit>()

                        when (Core.currentPage) {
                            Page.DOWNLOADER -> {
                                options.add {
                                    defaultDropdownItem(
                                        "Check For Updates",
                                        EvaIcons.Fill.CloudDownload
                                    ) {
                                        closeMenu()
                                        Core.openUpdate()
                                    }
                                }
                                options.add {
                                    defaultDropdownItem(
                                        "How To Use",
                                        EvaIcons.Fill.Book
                                    ) {
                                        closeMenu()
                                        DialogHelper.showMessage(
                                            "How To Use",
                                            HowToUse.text,
                                            DpSize(400.dp, 400.dp)
                                        )
                                    }
                                }
                                options.add {
                                    defaultDropdownItem(
                                        "Key Combinations",
                                        EvaIcons.Fill.Keypad
                                    ) {
                                        closeMenu()
                                        DialogHelper.showMessage(
                                            "Key Combinations",
                                            KeyEvents.keyGuide,
                                            DpSize(400.dp, 400.dp)
                                        )
                                    }
                                }
                                options.add {
                                    defaultDropdownItem(
                                        "Donate",
                                        EvaIcons.Fill.Gift
                                    ) {
                                        DialogHelper.showLinkPrompt(
                                            "https://buymeacoffee.com/nobilitydeviant",
                                            """
                                                If you feel like this program has helped you, I'd appreciate it!
                                                
                                                Do you want to open:
                                                https://buymeacoffee.com/nobilitydeviant
                                                in your default browser?
                                            """.trimIndent()
                                        )
                                    }
                                }
                                options.add {
                                    defaultDropdownItem(
                                        "About",
                                        EvaIcons.Fill.Info
                                    ) {
                                        closeMenu()
                                        DialogHelper.showMessage(
                                            "About",
                                            """
                                           This is an improved version of my old WcoDownloader created entirely with Jetpack Compose.
                                                                
                                           This free program is used to download videos from ${Core.wcoUrl}.
                                                                
                                           That's all :)
                                                                
                                           Other anime sites are locked down hard and are really tough to scrape from.
                                           This program is designed for wcofun only, but it is possible to expand if other sites are vulnerable.
                                                                
                                           Creator: NobilityDev
                                                                
                                           Github: 
                                           ${AppInfo.GITHUB_URL}
                                                                
                                        """.trimIndent(),
                                            size = DpSize(400.dp, 400.dp)
                                        )
                                    }
                                }
                            }

                            Page.DOWNLOADS -> {
                                if (Core.child.isRunning) {
                                    options.add {
                                        defaultDropdownItem(
                                            "Stop Downloads",
                                            EvaIcons.Fill.Close
                                        ) {
                                            closeMenu()
                                            Core.child.stop()
                                            windowScope.showToast("All downloads have been stopped.")
                                        }
                                    }
                                }
                                options.add {
                                    defaultDropdownItem(
                                        "Clear All Downloads",
                                        EvaIcons.Fill.Trash
                                    ) {
                                        closeMenu()
                                        if (Core.child.downloadList.isEmpty()) {
                                            return@defaultDropdownItem
                                        }
                                        if (!Core.child.isRunning) {
                                            DialogHelper.showConfirm(
                                                """
                                               Are you sure you want to clear all downloads?
                                               This is just going to delete the entire download list. 
                                               No files will be deleted.
                                               This action is irreversible unless you save a backup of the database folder first.
                                            """.trimIndent(),
                                                "Clear Downloads"
                                            ) {
                                                val size = Core.child.downloadList.size
                                                Core.child.downloadList.clear()
                                                BoxHelper.shared.downloadBox.removeAll()
                                                DialogHelper.showMessageSmall("Success", "Deleted $size downloads.")
                                            }
                                        } else {
                                            DialogHelper.showError("You can't clear downloads while things are downloading.")
                                        }
                                    }
                                }
                                options.add {
                                    defaultDropdownItem(
                                        "Clear Downloads & Delete Incomplete Files",
                                        EvaIcons.Fill.Trash
                                    ) {
                                        closeMenu()
                                        if (Core.child.downloadList.isEmpty()) {
                                            return@defaultDropdownItem
                                        }
                                        if (!Core.child.isRunning) {
                                            DialogHelper.showConfirm(
                                                """
                                               Are you sure you want to clear all downloads?
                                               This is going to delete the entire download list. 
                                               All found incomplete files will be deleted as well.
                                               Please note that this may potentially delete videos that are completed.
                                               It shouldn't, but that's why there's a second option.
                                               This action is irreversible unless you save a backup of the database folder first.
                                            """.trimIndent(),
                                                "Clear All Downloads"
                                            ) {
                                                val size = Core.child.downloadList.size
                                                Core.child.downloadList.forEach { download ->
                                                    val file = download.downloadFile()
                                                    if (file != null && !download.isComplete) {
                                                        file.delete()
                                                    }
                                                }
                                                Core.child.downloadList.clear()
                                                BoxHelper.shared.downloadBox.removeAll()
                                                DialogHelper.showMessageSmall("Success", "Deleted $size downloads.")
                                            }
                                        } else {
                                            DialogHelper.showError("You can't clear downloads while things are downloading.")
                                        }
                                    }
                                }
                            }

                            Page.SETTINGS -> {
                                options.add {
                                    defaultDropdownItem(
                                        "Open Database Folder",
                                        EvaIcons.Fill.Folder
                                    ) {
                                        closeMenu()
                                        Tools.openFile(Constants.databasePath)
                                    }
                                }
                            }

                            Page.RECENT -> {
                                options.add {
                                    defaultDropdownItem(
                                        "Download Recent Data",
                                        EvaIcons.Fill.Download
                                    ) {
                                        closeMenu()
                                        scope.launch {
                                            Core.recentView.reloadRecentData()
                                        }
                                    }
                                }
                            }

                            else -> {}
                        }

                        if (options.isNotEmpty()) {
                            tooltipIconButton(
                                "Options",
                                EvaIcons.Fill.MoreVertical,
                                mediumIconSize - 2.dp,
                                onClick = { showFileMenu = !showFileMenu },
                                spacePosition = SpacePosition.START,
                                space = 10.dp
                            )
                            DropdownMenu(
                                expanded = showFileMenu,
                                onDismissRequest = { closeMenu() }
                            ) {
                                options.forEach { o ->
                                    o.invoke()
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                )
            },
            content = {
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
                                        
                                        Drivers Killed: ${Core.child.shutdownProgress.first}/${Core.child.shutdownProgress.second}
                                    """.trimIndent(),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier.padding(top = it.calculateTopPadding())
                ) {
                    content(it)
                }
                ApplicationState.addToastToWindow(windowScope)
            }
        )
    }
}

