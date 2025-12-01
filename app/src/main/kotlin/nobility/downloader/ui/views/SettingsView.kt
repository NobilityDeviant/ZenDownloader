package nobility.downloader.ui.views

import AppInfo
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkrockstudios.libraries.mpfilepicker.DirectoryPicker
import com.darkrockstudios.libraries.mpfilepicker.FilePicker
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.fill.*
import nobility.downloader.Page
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.BoxHelper.Companion.long
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.BoxHelper.Companion.update
import nobility.downloader.core.Core
import nobility.downloader.core.scraper.player.PlayVideoWithMpv
import nobility.downloader.core.settings.Defaults
import nobility.downloader.core.settings.Quality
import nobility.downloader.core.settings.VideoPlayer
import nobility.downloader.ui.components.*
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.ui.components.dialog.DialogHelper.smallWindowSize
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.Constants.bottomBarHeight
import nobility.downloader.utils.Constants.maxThreads
import nobility.downloader.utils.Constants.maxTimeout
import nobility.downloader.utils.Constants.minThreads
import nobility.downloader.utils.Constants.minTimeout
import nobility.downloader.utils.FrogLog
import nobility.downloader.utils.Tools
import nobility.downloader.utils.fileExists
import nobility.downloader.utils.normalizeEnumName
import java.io.File


class SettingsView : ViewPage {

    override val page = Page.SETTINGS

    private var saveButtonEnabled = mutableStateOf(false)
    private lateinit var windowScope: AppWindowScope
    private val focusModifier
        get() = Modifier.onFocusChanged {
            Core.settingsFieldFocused = it.isFocused
        }
    private val stringOptions = mutableStateMapOf<Defaults, MutableState<String>>()
    private val booleanOptions = mutableStateMapOf<Defaults, MutableState<Boolean>>()
    private val intOptions = mutableStateMapOf<Defaults, MutableState<Int>>()
    private val longOptions = mutableStateMapOf<Defaults, MutableState<Long>>()

    init {
        Defaults.settings.forEach {
            when (it.value) {
                is String -> {
                    stringOptions.put(
                        it, mutableStateOf(it.string())
                    )
                }

                is Boolean -> {
                    booleanOptions.put(
                        it, mutableStateOf(it.boolean())
                    )
                }

                is Int -> {
                    intOptions.put(
                        it, mutableStateOf(it.int())
                    )
                }

                is Long -> {
                    longOptions.put(
                        it, mutableStateOf(it.long())
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
    @Composable
    override fun Ui(windowScope: AppWindowScope) {
        this.windowScope = windowScope
        val focusManager = LocalFocusManager.current
        Scaffold(
            bottomBar = {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .height(bottomBarHeight)
                        .onClick {
                            focusManager.clearFocus(true)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.padding(10.dp)
                            .onClick {
                                focusManager.clearFocus(true)
                            },
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        defaultButton(
                            "Update Series Images",
                            width = 150.dp,
                            height = 40.dp
                        ) {
                            Core.openImageUpdater()
                        }
                        defaultButton(
                            "Update Options",
                            width = 150.dp,
                            height = 40.dp
                        ) {
                            openUpdateOptionsWindow()
                        }
                        DefaultButton(
                            "Save Settings",
                            enabled = saveButtonEnabled,
                            width = 150.dp,
                            height = 40.dp
                        ) {
                            saveSettings()
                        }
                    }
                }
            }
        ) {
            val scrollState = rememberScrollState()
            FullBox {
                Column(
                    modifier = Modifier.padding(
                        bottom = it.calculateBottomPadding(),
                        end = verticalScrollbarEndPadding
                    ).fillMaxWidth()
                        //fix scroll up on click
                        .pointerInput(Unit) {
                            detectTapGestures {
                                focusManager.clearFocus(true)
                            }
                        }
                        .verticalScroll(scrollState)
                ) {
                    FieldRow(Defaults.SAVE_FOLDER)
                    FieldRow(Defaults.CHROME_BROWSER_PATH)
                    FieldRow(Defaults.CHROME_DRIVER_PATH)
                    FieldRow(Defaults.WCO_PREMIUM_USERNAME)
                    FieldRow(Defaults.WCO_PREMIUM_PASSWORD)
                    FieldRow(Defaults.EPISODE_ORGANIZERS)
                    //FieldRow(Defaults.VLC_PATH) todo fails without headers? I will try more later
                    FieldDropdown(Defaults.QUALITY)
                    FieldDropdown(Defaults.VIDEO_PLAYER)
                    FlowRow(
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        maxItemsInEachRow = 10
                    ) {
                        Defaults.intFields.forEach { field ->
                            FieldRowInt(field)
                        }
                    }
                    FlowRow(
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        maxItemsInEachRow = 10
                    ) {
                        Defaults.checkBoxes.forEach { cb ->
                            FieldCheckbox(cb)
                        }
                    }
                }
                VerticalScrollbar(scrollState)
            }
        }
    }

    @Suppress("SameParameterValue")
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun FieldRow(
        setting: Defaults,
        fullWidth: Boolean = true
    ) {
        val option = stringOptions[setting] ?: return
        val title = setting.alternativeName.ifEmpty {
            setting.name.normalizeEnumName()
        }
        val fieldModifier = Modifier.height(40.dp)
            .then(if (fullWidth) Modifier.width(400.dp) else Modifier)
            .then(focusModifier)
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(10.dp)
        ) {
            Tooltip(setting.description) {
                Text(
                    "$title:",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            when (setting) {
                Defaults.WCO_PREMIUM_PASSWORD -> {
                    val showOption = booleanOptions[Defaults.SHOW_WCO_PREMIUM_PASSWORD]
                    if (showOption != null) {
                        DefaultSettingsTextField(
                            option.value,
                            { text ->
                                option.value = text
                                updateSaveButton()
                            },
                            passwordMode = !showOption.value,
                            modifier = fieldModifier,
                            textStyle = MaterialTheme.typography.labelLarge
                        )
                        DefaultCheckbox(
                            showOption.value,
                            modifier = Modifier.height(30.dp)
                                .pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            showOption.value = it
                            updateSaveButton()
                        }
                        Text(
                            "Show Password",
                            fontSize = 12.sp,
                            modifier = Modifier
                                .onClick {
                                    showOption.value = showOption.value.not()
                                    updateSaveButton()
                                }
                                .pointerHoverIcon(PointerIcon.Hand)
                        )
                    }
                }

                Defaults.SAVE_FOLDER -> {
                    DefaultSettingsTextField(
                        option.value,
                        { text ->
                            option.value = text
                            updateSaveButton()
                        },
                        modifier = fieldModifier,
                        textStyle = MaterialTheme.typography.labelLarge
                    )
                    var showFilePicker by remember { mutableStateOf(false) }
                    DirectoryPicker(
                        show = showFilePicker,
                        initialDirectory = option.value.ifEmpty { Defaults.SAVE_FOLDER.value.toString() },
                        title = "Choose Save Folder"
                    ) {
                        if (it != null) {
                            option.value = it
                            updateSaveButton()
                        }
                        showFilePicker = false
                    }
                    DefaultButton(
                        "Set Folder",
                        Modifier.height(30.dp)
                            .width(80.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        showFilePicker = true
                    }
                }

                Defaults.VLC_PATH -> {
                    DefaultSettingsTextField(
                        option.value,
                        { text ->
                            option.value = text
                            updateSaveButton()
                        },
                        modifier = fieldModifier,
                        textStyle = MaterialTheme.typography.labelLarge
                    )
                    var showFilePicker by remember { mutableStateOf(false) }
                    FilePicker(
                        show = showFilePicker,
                        initialDirectory = option.value,
                        title = "Choose VLC Executable File"
                    ) {
                        if (it != null) {
                            option.value = it.path
                            updateSaveButton()
                        }
                        showFilePicker = false
                    }
                    DefaultButton(
                        "Set File",
                        Modifier.height(30.dp)
                            .width(80.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        showFilePicker = true
                    }
                }

                Defaults.MPV_PATH -> {
                    DefaultSettingsTextField(
                        option.value,
                        { text ->
                            option.value = text
                            updateSaveButton()
                        },
                        modifier = fieldModifier,
                        textStyle = MaterialTheme.typography.labelLarge
                    )
                    var showFilePicker by remember { mutableStateOf(false) }
                    FilePicker(
                        show = showFilePicker,
                        initialDirectory = option.value,
                        title = "Choose MPV Executable File"
                    ) {
                        if (it != null) {
                            option.value = it.path
                            updateSaveButton()
                        }
                        showFilePicker = false
                    }
                    DefaultButton(
                        "Set File",
                        Modifier.height(30.dp)
                            .width(80.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        showFilePicker = true
                    }
                }

                Defaults.CHROME_BROWSER_PATH -> {
                    DefaultSettingsTextField(
                        option.value,
                        { text ->
                            option.value = text
                            updateSaveButton()
                        },
                        modifier = fieldModifier,
                        textStyle = MaterialTheme.typography.labelLarge
                    )
                    var showFilePicker by remember { mutableStateOf(false) }
                    FilePicker(
                        show = showFilePicker,
                        initialDirectory = Defaults.SAVE_FOLDER.value.toString(),
                        title = "Choose Chrome Browser File"
                    ) {
                        if (it != null) {
                            option.value = it.path
                            updateSaveButton()
                        }
                        showFilePicker = false
                    }
                    DefaultButton(
                        "Set File",
                        Modifier.height(30.dp)
                            .width(80.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        showFilePicker = true
                    }
                }

                Defaults.CHROME_DRIVER_PATH -> {
                    DefaultSettingsTextField(
                        option.value,
                        { text ->
                            option.value = text
                            updateSaveButton()
                        },
                        modifier = fieldModifier,
                        textStyle = MaterialTheme.typography.labelLarge
                    )
                    var showFilePicker by remember { mutableStateOf(false) }
                    FilePicker(
                        show = showFilePicker,
                        initialDirectory = Defaults.SAVE_FOLDER.value.toString(),
                        title = "Choose Chrome Driver FIle"
                    ) {
                        if (it != null) {
                            option.value = it.path
                            updateSaveButton()
                        }
                        showFilePicker = false
                    }
                    DefaultButton(
                        "Set File",
                        Modifier.height(30.dp)
                            .width(80.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        showFilePicker = true
                    }
                }

                else -> {
                    DefaultSettingsTextField(
                        option.value,
                        {
                            option.value = it
                            updateSaveButton()
                        },
                        modifier = fieldModifier,
                        textStyle = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }

    @Suppress("SameParameterValue")
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun FieldRowInt(
        setting: Defaults
    ) {
        val option = intOptions[setting] ?: return
        val title = setting.alternativeName.ifEmpty {
            setting.name.normalizeEnumName()
        }
        val modifier = Modifier
            .height(30.dp)
            .width(80.dp)
            .then(focusModifier)
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp)
        ) {
            Tooltip(setting.description) {
                Text(
                    "$title:",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            val type = when (setting) {
                Defaults.DOWNLOAD_THREADS -> {
                    FieldType.THREADS
                }

                Defaults.TIMEOUT -> {
                    FieldType.TIMEOUT
                }

                else -> {
                    FieldType.DEFAULT
                }
            }
            DefaultSettingsTextField(
                if (option.value <= -1) "" else option.value.toString(),
                { text ->
                    option.value = textToDigits(
                        type,
                        text
                    )
                    updateSaveButton()
                },
                numbersOnly = true,
                textStyle = MaterialTheme.typography.labelMedium,
                modifier = modifier
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DefaultIcon(
                    EvaIcons.Fill.ArrowUp,
                    Modifier.size(
                        24.dp,
                        16.dp
                    ).pointerHoverIcon(PointerIcon.Hand),
                    onClick = {
                        option.value = fixedValue(
                            type,
                            option.value + 1
                        )
                        updateSaveButton()
                    }
                )

                DefaultIcon(
                    EvaIcons.Fill.ArrowDown,
                    Modifier.size(
                        24.dp,
                        16.dp
                    ).pointerHoverIcon(PointerIcon.Hand),
                    onClick = {
                        option.value = fixedValue(
                            type,
                            option.value - 1
                        )
                        updateSaveButton()
                    }
                )
            }
        }
    }

    @Composable
    private fun FieldDropdown(
        setting: Defaults
    ) {
        val option = stringOptions[setting] ?: return
        val title = setting.alternativeName.ifEmpty {
            setting.name.normalizeEnumName()
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp)
        ) {
            Tooltip(setting.description) {
                Text(
                    "$title:",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (setting == Defaults.QUALITY) {
                var expanded by remember { mutableStateOf(false) }
                val options = Quality.entries.map {
                    DropdownOption(it.tag) {
                        expanded = false
                        option.value = it.tag
                        updateSaveButton()
                    }
                }
                Tooltip(setting.description) {
                    DefaultDropdown(
                        option.value,
                        expanded,
                        options,
                        onTextClick = { expanded = true }
                    ) { expanded = false }
                }
            } else if (setting == Defaults.VIDEO_PLAYER) {
                var expanded by remember { mutableStateOf(false) }
                val options = VideoPlayer.entries.map {
                    DropdownOption(it.name) {
                        expanded = false
                        option.value = it.name
                        updateSaveButton()
                    }
                }
                Tooltip(setting.description) {
                    DefaultDropdown(
                        option.value,
                        expanded,
                        options,
                        onTextClick = { expanded = true }
                    ) { expanded = false }
                }
                if (option.value == VideoPlayer.FFPLAY.name) {
                    DefaultButton(
                        "Check For FFplay"
                    ) {
                        val downloaded = Tools.ffSetFound()
                        windowScope.showToast("FFplay Downloaded: $downloaded")
                        if (!downloaded) {
                            Core.openFfsetDownloaderWindow()
                        }
                    }
                } else if (option.value == VideoPlayer.MPV.name) {
                    DefaultButton(
                        "Check For Mpv Install"
                    ) {
                        val customPath = stringOptions[Defaults.MPV_PATH]?.value
                        val installed = if (!customPath.isNullOrEmpty()) {
                            PlayVideoWithMpv.isMpvInstalled(customPath)
                        } else {
                            PlayVideoWithMpv.isMpvInstalled()
                        }
                        windowScope.showToast("Mpv Installed: $installed")
                        if (!installed) {
                            DialogHelper.showLinkPrompt(
                                AppInfo.MPV_GUIDE_URL,
                                """
                                    Mpv wasn't found or executable. 
                                    Would you like to visit the installation guide?
                                """.trimIndent()
                            )
                        }
                    }
                }
            }
        }
        if (option.value == VideoPlayer.MPV.name) {
            FieldRow(Defaults.MPV_PATH)
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun FieldCheckbox(
        setting: Defaults
    ) {
        val title = setting.alternativeName.ifEmpty {
            setting.name.normalizeEnumName()
        }
        val option = booleanOptions[setting] ?: return
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(10.dp)
        ) {
            Tooltip(setting.description) {
                Text(
                    "$title:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .onClick {
                            option.value = option.value.not()
                            updateSaveButton()
                        }
                        .pointerHoverIcon(PointerIcon.Hand)
                )
            }
            Tooltip(setting.description) {
                DefaultCheckbox(
                    option.value,
                    modifier = Modifier.height(30.dp)
                        .pointerHoverIcon(PointerIcon.Hand)
                ) {
                    option.value = it
                    updateSaveButton()
                }
            }
        }
    }

    private enum class FieldType {
        THREADS, TIMEOUT, DEFAULT;
    }

    private fun fixedValue(
        type: FieldType,
        value: Int
    ): Int {
        if (type == FieldType.THREADS) {
            if (value < minThreads) {
                return minThreads
            } else if (value > maxThreads) {
                return maxThreads
            }
        } else if (type == FieldType.TIMEOUT) {
            if (value < minTimeout) {
                return minTimeout
            } else if (value > maxTimeout) {
                return maxTimeout
            }
        } else if (type == FieldType.DEFAULT) {
            if (value < 1) {
                return 1
            } else if (value > 100) {
                return 100
            }
        }
        return value
    }

    private fun textToDigits(
        type: FieldType,
        text: String
    ): Int {
        if (text.isEmpty()) {
            return -1
        }
        val filtered = text.filter { it.isDigit() }
        if (filtered.isEmpty()) {
            return -1
        }
        var num = filtered.toInt()
        if (type == FieldType.THREADS) {
            if (num < minThreads) {
                num = minThreads
            } else if (num > maxThreads) {
                num = maxThreads
            }
        } else if (type == FieldType.TIMEOUT) {
            if (num < minTimeout) {
                num = minTimeout
            } else if (num > maxTimeout) {
                num = maxTimeout
            }
        } else if (type == FieldType.DEFAULT) {
            if (num < 1) {
                num = 1
            } else if (num > 100) {
                num = 100
            }
        }
        return num
    }

    fun saveSettings(): Boolean {
        val retryOptions = intOptions.filter { Defaults.intFields.contains(it.key) }
        retryOptions.forEach {
            if (it.value.value <= 0) {
                it.value.value = 1
            }
            if (it.value.value > 100) {
                it.value.value = 100
            }
        }
        val threads = intOptions[Defaults.DOWNLOAD_THREADS]
        if (threads != null) {
            if (threads.value <= 0) {
                threads.value = Defaults.DOWNLOAD_THREADS.int()
            }
            if (threads.value < minThreads || threads.value > maxThreads) {
                windowScope.showToast("You must enter a Download Threads value of $minThreads-$maxThreads.")
                return false
            }
        }
        val timeout = intOptions[Defaults.TIMEOUT]
        if (timeout != null) {
            if (timeout.value <= 0) {
                timeout.value = Defaults.TIMEOUT.int()
            } else if (timeout.value < minTimeout || timeout.value > maxTimeout) {
                windowScope.showToast("You must enter a Network Timeout value of $minTimeout-$maxTimeout.")
                return false
            }
        }
        val saveFolder = stringOptions[Defaults.SAVE_FOLDER]
        if (saveFolder != null) {
            if (saveFolder.value.isEmpty()) {
                saveFolder.value = Defaults.SAVE_FOLDER.string()
            }
            val file = File(saveFolder.value)
            if (!file.exists() && !file.mkdirs()) {
                windowScope.showToast("The download folder doesn't exist and couldn't be created.")
                return false
            }
            if (!Tools.isFolderWritable(saveFolder.value)) {
                FrogLog.error(
                    """
                            Your download folder isn't writable and couldn't be set to writable.
                            If download files fail to create, either choose another folder, fix any write permissions or launch this program with Admin/SU Permissions.
                        """.trimIndent()
                )
            }
        }
        val chromePath = stringOptions[Defaults.CHROME_BROWSER_PATH]
        if (chromePath != null && chromePath.value.isNotEmpty()) {
            if (!chromePath.value.fileExists()) {
                windowScope.showToast("The chrome browser path doesn't exist.")
                return false
            }
        }
        val chromeDriverPath = stringOptions[Defaults.CHROME_DRIVER_PATH]
        if (chromeDriverPath != null && chromeDriverPath.value.isNotEmpty()) {
            if (!chromeDriverPath.value.fileExists()) {
                windowScope.showToast("The chrome driver path doesn't exist.")
                return false
            }
        }
        if (chromePath != null && chromeDriverPath != null) {
            if (chromePath.value.isNotEmpty() && chromeDriverPath.value.isEmpty()) {
                windowScope.showToast("Both Chrome Browser and Chrome Driver paths need to be set.")
                return false
            }
            if (chromeDriverPath.value.isNotEmpty() && chromePath.value.isEmpty()) {
                windowScope.showToast("Both Chrome Browser and Chrome Driver paths need to be set.")
                return false
            }
        }
        val wcoUsername = stringOptions[Defaults.WCO_PREMIUM_USERNAME]
        val wcoPassword = stringOptions[Defaults.WCO_PREMIUM_PASSWORD]
        if (wcoUsername != null && wcoPassword != null) {
            if (wcoPassword.value.isNotEmpty() && wcoUsername.value.isEmpty()) {
                windowScope.showToast("You need a Wco Premium Username to set a Wco Premium Password.")
                return false
            }
        }
        stringOptions.keys.forEach {
            val option = stringOptions[it]
            if (option != null) {
                it.update(option.value)
            }
        }
        booleanOptions.keys.forEach {
            val option = booleanOptions[it]
            if (option != null) {
                it.update(option.value)
            }
        }
        intOptions.keys.forEach {
            val option = intOptions[it]
            if (option != null) {
                it.update(option.value)
            }
        }
        longOptions.keys.forEach {
            val option = longOptions[it]
            if (option != null) {
                it.update(option.value)
            }
        }
        Core.child.refreshDownloadsProgress()
        Core.reloadRandomSeries()
        updateValues()
        windowScope.showToast("Settings successfully saved.")
        return true
    }

    private fun updateSaveButton() {
        saveButtonEnabled.value = settingsChanged()
    }

    fun updateValues() {
        Defaults.entries.forEach {
            when (it.value) {
                is String -> {
                    stringOptions[it]?.value = it.string()
                }

                is Boolean -> {
                    booleanOptions[it]?.value = it.boolean()
                }

                is Int -> {
                    intOptions[it]?.value = it.int()
                }

                is Long -> {
                    longOptions[it]?.value = it.long()
                }
            }
        }
        saveButtonEnabled.value = false
    }

    fun settingsChanged(): Boolean {
        val strings = stringOptions.filter { it.value.value != it.key.string() }
        val booleans = booleanOptions.filter { it.value.value != it.key.boolean() }
        val ints = intOptions.filter { it.value.value != it.key.int() }
        val longs = longOptions.filter { it.value.value != it.key.long() }
        return strings.isNotEmpty() || booleans.isNotEmpty()
                || ints.isNotEmpty() || longs.isNotEmpty()
    }

    @OptIn(ExperimentalLayoutApi::class)
    private fun openUpdateOptionsWindow() {
        ApplicationState.newWindow(
            "Update Options",
            undecorated = false,
            transparent = false,
            alwaysOnTop = true,
            size = DpSize(500.dp, 350.dp)
        ) {
            val scrollState = rememberScrollState()
            FullBox {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.fillMaxSize()
                        .padding(end = verticalScrollbarEndPadding)
                        .background(
                            color = MaterialTheme.colorScheme.background
                        ).verticalScroll(scrollState)
                ) {
                    FlowRow(
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        maxItemsInEachRow = 3,
                        modifier = Modifier.padding(5.dp)
                    ) {
                        Defaults.updateCheckBoxes.forEach {
                            FieldCheckbox(it)
                        }
                    }
                    Row(
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(5.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val updateOptions = booleanOptions.filter {
                            Defaults.updateCheckBoxes.contains(it.key)
                        }
                        if (updateOptions.any { it.value.value }) {
                            defaultButton(
                                "Enable All Updates",
                                width = 150.dp,
                                height = 35.dp
                            ) {
                                booleanOptions.forEach {
                                    if (Defaults.updateCheckBoxes.contains(it.key)) {
                                        it.value.value = false
                                    }
                                }
                                updateSaveButton()
                            }
                        } else {
                            defaultButton(
                                "Disable All Updates",
                                width = 150.dp,
                                height = 35.dp
                            ) {
                                booleanOptions.forEach {
                                    if (Defaults.updateCheckBoxes.contains(it.key)) {
                                        it.value.value = true
                                    }
                                }
                                updateSaveButton()
                            }
                        }
                        defaultButton(
                            "Close",
                            width = 150.dp,
                            height = 35.dp
                        ) {
                            closeWindow()
                        }
                    }
                }
                VerticalScrollbar(scrollState)
            }
        }
    }

    override val menuOptions: List<OverflowOption>
        get() = listOf(
            OverflowOption(
                EvaIcons.Fill.QuestionMarkCircle,
                "Hover over each settings title to see it's description.",
            ) {},
            OverflowOption(
                EvaIcons.Fill.Folder,
                "Open Database Folder"
            ) {
                Tools.openFile(AppInfo.databasePath)
            },
            OverflowOption(
                EvaIcons.Fill.Refresh,
                "Reset Settings",
            ) {
                DialogHelper.showConfirm(
                    "Are you sure you want to reset your settings to the default ones?",
                    "Reset Settings",
                    size = smallWindowSize
                ) {
                    BoxHelper.resetSettings()
                    Core.settingsView.updateValues()
                    windowScope.showToast("Settings have been reset.")
                }
            }

        )
}