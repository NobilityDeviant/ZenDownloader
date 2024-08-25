package nobility.downloader.ui.windows

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.fill.*
import nobility.downloader.Page
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.BoxHelper.Companion.update
import nobility.downloader.core.Core
import nobility.downloader.core.settings.Defaults
import nobility.downloader.ui.components.SpacePosition
import nobility.downloader.ui.components.defaultDropdownItem
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.ui.components.tooltip
import nobility.downloader.ui.components.tooltipIconButton
import nobility.downloader.ui.views.downloaderUi
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.*
import nobility.downloader.utils.Constants.mediumIconSize

@Composable
fun mainWindow(scope: AppWindowScope) {
    uiWrapper(scope) {
        when (Core.currentPage) {
            Page.HOME -> downloaderUi()
            Page.DOWNLOADS -> Core.downloads.downloadsUi(scope)
            Page.SETTINGS -> Core.settings.settingsUi(scope)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun uiWrapper(
    scope: AppWindowScope,
    content: @Composable (PaddingValues) -> Unit
) {
    var showFileMenu by remember {
        mutableStateOf(false)
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                modifier = Modifier.height(Constants.topbarHeight),
                title = {
                    Text(
                        text = Core.currentPage.title,
                        modifier = Modifier.padding(top = 8.dp),
                        fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    var mainIconText by remember { mutableStateOf("") }
                    mainIconText = if (Core.currentPage != Page.HOME) {
                        "Home [ESC]"
                    } else {
                        "Settings [CTRL + S]"
                    }
                    tooltipIconButton(
                        tooltipText = mainIconText,
                        icon = if (Core.currentPage == Page.HOME)
                            Icons.Filled.Settings else EvaIcons.Fill.ArrowBack
                    ) {
                        if (Core.currentPage == Page.HOME) {
                            Core.openSettings()
                        } else {
                            if (Core.currentPage == Page.SETTINGS) {
                                if (Core.settings.settingsChanged()) {
                                    DialogHelper.showConfirm(
                                        "You have unsaved settings. Would you like to save them?",
                                        "Save Settings",
                                        size = DpSize(300.dp, 200.dp),
                                        onConfirmTitle = "Save",
                                        onDeny = {
                                            Core.currentPage = Page.HOME
                                        }
                                    ) {
                                        if (Core.settings.saveSettings()) {
                                            Core.currentPage = Page.HOME
                                        }
                                    }
                                } else {
                                    Core.currentPage = Page.HOME
                                }
                            } else {
                                Core.currentPage = Page.HOME
                            }
                        }
                    }
                },
                actions = {
                    if (Core.currentPage == Page.HOME) {
                        tooltip("Downloads [CTRL + D]") {
                            BadgedBox(
                                badge = {
                                    if (Core.child.downloadsInProgress.value > 0) {
                                        Badge(
                                            containerColor = Color.Red,
                                            modifier = Modifier.offset(y = 6.dp, x = (-1).dp)
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
                                },
                                modifier = Modifier.size(mediumIconSize)
                            ) {
                                IconButton(onClick = { Core.currentPage = Page.DOWNLOADS }) {
                                    Icon(
                                        EvaIcons.Fill.Download,
                                        "",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(mediumIconSize)
                                    )
                                }
                            }
                        }
                    } else if (Core.currentPage == Page.DOWNLOADS) {
                        tooltipIconButton(
                            "Open Download Folder",
                            icon = EvaIcons.Fill.Folder
                        ) {
                            Tools.openFolder(
                                Defaults.SAVE_FOLDER.string()
                            )
                        }
                    }
                    if (Core.currentPage != Page.SETTINGS) {
                        tooltipIconButton(
                            "Options",
                            EvaIcons.Fill.MoreVertical,
                            mediumIconSize - 2.dp,
                            onClick = { showFileMenu = !showFileMenu },
                            spacePosition = SpacePosition.START,
                            space = 10.dp
                        )
                        val closeMenu = { showFileMenu = false }
                        DropdownMenu(
                            expanded = showFileMenu,
                            onDismissRequest = { closeMenu() }
                        ) {
                            if (Core.currentPage == Page.HOME) {
                                defaultDropdownItem(
                                    "Open Download History",
                                    EvaIcons.Fill.Archive
                                ) {
                                    closeMenu()
                                    scope.showToast("HI")
                                    Core.openHistory()
                                }
                                defaultDropdownItem(
                                    "Open Recent Series & Episodes",
                                    EvaIcons.Fill.ExternalLink
                                ) {
                                    closeMenu()
                                    Core.openRecents()
                                }
                                defaultDropdownItem(
                                    "Open Video Database",
                                    EvaIcons.Fill.Video
                                ) {
                                    closeMenu()
                                    Core.openWco()
                                }
                                defaultDropdownItem(
                                    "Check For Updates",
                                    EvaIcons.Fill.CloudDownload
                                ) {
                                    closeMenu()
                                    Core.openUpdate()
                                }
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
                                defaultDropdownItem(
                                    "Key Combinations",
                                    EvaIcons.Fill.Keypad
                                ) {
                                    closeMenu()
                                    DialogHelper.showMessage(
                                        "Key Combinations",
                                        keyGuide,
                                        DpSize(400.dp, 400.dp)
                                    )
                                }
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
                            } else if (Core.currentPage == Page.DOWNLOADS) {
                                if (Core.child.isRunning) {
                                    defaultDropdownItem(
                                        "Stop Downloads",
                                        EvaIcons.Fill.Close
                                    ) {
                                        closeMenu()
                                        Core.child.stop()
                                        scope.showToast("All downloads have been stopped.")
                                    }
                                }
                                defaultDropdownItem(
                                    "Open Download History",
                                    EvaIcons.Fill.Archive
                                ) {
                                    closeMenu()
                                    Core.openHistory()
                                }
                                defaultDropdownItem(
                                    "Clear Downloads",
                                    EvaIcons.Fill.Trash,
                                    enabled = Core.child.downloadList.isNotEmpty()
                                ) {
                                    closeMenu()
                                    if (!Core.child.isRunning) {
                                        DialogHelper.showConfirm(
                                            """
                                                Are you sure you want to clear all downloads?
                                                This is just going to clear the downloaf list. No files will be deleted.
                                                This action is irreversible unless you save a backup of the database folder.
                                            """.trimIndent(),
                                            "Clear Downloads"
                                        ) {
                                            val size = Core.child.downloadList.size
                                            Core.child.downloadList.clear()
                                            DialogHelper.showMessage("Success", "Cleared $size downloads")
                                        }
                                    } else {
                                        DialogHelper.showError("You can't clear downloads while things are downloading.")
                                    }
                                }
                            }
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        content = {
            Column(
                modifier = Modifier.padding(top = it.calculateTopPadding())
            ) {
                content(it)
            }
            ApplicationState.addToastToWindow(scope)
        }
    )
}

