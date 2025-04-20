package nobility.downloader.ui.views

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkrockstudios.libraries.mpfilepicker.DirectoryPicker
import com.darkrockstudios.libraries.mpfilepicker.FilePicker
import nobility.downloader.Page
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.BoxHelper.Companion.long
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.BoxHelper.Companion.update
import nobility.downloader.core.Core
import nobility.downloader.core.settings.Defaults
import nobility.downloader.core.settings.Quality
import nobility.downloader.ui.components.*
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.Constants.bottomBarHeight
import nobility.downloader.utils.Constants.maxThreads
import nobility.downloader.utils.Constants.maxTimeout
import nobility.downloader.utils.Constants.minThreads
import nobility.downloader.utils.Constants.minTimeout
import nobility.downloader.utils.fileExists
import nobility.downloader.utils.normalizeEnumName


class SettingsView : ViewPage {

    override val page = Page.SETTINGS

    private var saveButtonEnabled = mutableStateOf(false)
    private lateinit var windowScope: AppWindowScope
    private val focusModifier get() = Modifier.onFocusChanged {
        Core.settingsFieldFocused = it.isFocused
    }
    private val stringOptions = mutableStateMapOf<Defaults, MutableState<String>>()
    private val booleanOptions = mutableStateMapOf<Defaults, MutableState<Boolean>>()
    private val intOptions = mutableStateMapOf<Defaults, MutableState<Int>>()
    private val longOptions = mutableStateMapOf<Defaults, MutableState<Long>>()

    init {
        Defaults.settings.forEach {
            if (it.value is String) {
                stringOptions.put(
                    it, mutableStateOf(it.string())
                )
            } else if (it.value is Boolean) {
                booleanOptions.put(
                    it, mutableStateOf(it.boolean())
                )
            } else if (it.value is Int) {
                intOptions.put(
                    it, mutableStateOf(it.int())
                )
            } else if (it.value is Long) {
                longOptions.put(
                    it, mutableStateOf(it.long())
                )
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
    @Composable
    override fun ui(windowScope: AppWindowScope) {
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
                    ).fillMaxWidth().verticalScroll(scrollState)
                        .onClick {
                            focusManager.clearFocus(true)
                        }
                ) {
                    fieldRow(Defaults.SAVE_FOLDER, true)
                    fieldRow(Defaults.CHROME_BROWSER_PATH, true)
                    fieldRow(Defaults.CHROME_DRIVER_PATH, true)
                    fieldRow(Defaults.WCO_PREMIUM_USERNAME, true)
                    fieldRow(Defaults.WCO_PREMIUM_PASSWORD, true)
                    fieldDropdown(Defaults.QUALITY)
                    FlowRow(
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        maxItemsInEachRow = 4
                    ) {
                        Defaults.intFields.forEach {
                            fieldRowInt(it)
                        }
                    }
                    FlowRow(
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        maxItemsInEachRow = 4
                    ) {
                        Defaults.checkBoxes.forEach {
                            fieldCheckbox(it)
                        }
                    }
                }
                verticalScrollbar(scrollState)
            }
        }
    }

    @Suppress("SameParameterValue")
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun fieldRow(
        setting: Defaults,
        fullWidth: Boolean
    ) {
        val option = stringOptions[setting] ?: return
        val title = setting.alternativeName.ifEmpty {
            setting.name.normalizeEnumName()
        }
        val modifier = Modifier.height(30.dp)
            .then(if (fullWidth) Modifier.width(300.dp) else Modifier)
            .then(focusModifier)
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(10.dp)
        ) {
            tooltip(setting.description) {
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
                            modifier = modifier
                        )
                        DefaultCheckbox(
                            showOption.value,
                            modifier = Modifier.height(30.dp)
                        ) {
                            showOption.value = it
                            updateSaveButton()
                        }
                        Text(
                            "Show Password",
                            fontSize = 12.sp,
                            modifier = Modifier.onClick {
                                showOption.value = showOption.value.not()
                                updateSaveButton()
                            }
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
                        modifier = modifier
                    )
                    var showFilePicker by remember { mutableStateOf(false) }
                    DirectoryPicker(
                        show = showFilePicker,
                        initialDirectory = option.value,
                        title = "Choose Save Folder"
                    ) {
                        if (it != null) {
                            option.value = it
                            updateSaveButton()
                        }
                        showFilePicker = false
                    }
                    defaultButton(
                        "Set Folder",
                        height = 30.dp,
                        width = 80.dp
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
                        modifier = modifier
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
                    defaultButton(
                        "Set File",
                        height = 30.dp,
                        width = 80.dp
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
                        modifier = modifier
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
                    defaultButton(
                        "Set File",
                        height = 30.dp,
                        width = 80.dp
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
                        modifier = modifier
                    )
                }
            }
        }
    }

    @Suppress("SameParameterValue")
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun fieldRowInt(
        setting: Defaults
    ) {
        val option = intOptions[setting] ?: return
        val title = setting.alternativeName.ifEmpty {
            setting.name.normalizeEnumName()
        }
        val modifier = Modifier.height(30.dp).width(60.dp)
            .then(focusModifier)
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(10.dp)
        ) {
            tooltip(setting.description) {
                Text(
                    "$title:",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            when (setting) {
                Defaults.DOWNLOAD_THREADS -> {
                    DefaultSettingsTextField(
                        if (option.value == -1) "" else option.value.toString(),
                        { text ->
                            option.value = textToDigits(FieldType.THREADS, text)
                            updateSaveButton()
                        },
                        numbersOnly = true,
                        textStyle = MaterialTheme.typography.labelMedium,
                        modifier = modifier
                    )
                }

                Defaults.TIMEOUT -> {
                    DefaultSettingsTextField(
                        if (option.value == -1) "" else option.value.toString(),
                        { text ->
                            option.value = textToDigits(FieldType.TIMEOUT, text)
                            updateSaveButton()
                        },
                        numbersOnly = true,
                        textStyle = MaterialTheme.typography.labelMedium,
                        modifier = modifier
                    )
                }

                else -> {
                    DefaultSettingsTextField(
                        if (option.value == -1) "" else option.value.toString(),
                        { text ->
                            option.value = textToDigits(FieldType.RETRY, text)
                            updateSaveButton()
                        },
                        numbersOnly = true,
                        textStyle = MaterialTheme.typography.labelMedium,
                        modifier = modifier
                    )
                }
            }
        }
    }

    @Suppress("SameParameterValue")
    @Composable
    private fun fieldDropdown(
        setting: Defaults
    ) {
        val option = stringOptions[setting] ?: return
        val title = setting.alternativeName.ifEmpty {
            setting.name.normalizeEnumName()
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(10.dp)
        ) {
            tooltip(setting.description) {
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
                tooltip(setting.description) {
                    DefaultDropdown(
                        option.value,
                        expanded,
                        options,
                        onTextClick = { expanded = true }
                    ) { expanded = false }
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun fieldCheckbox(
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
            tooltip(setting.description) {
                Text(
                    "$title:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.onClick {
                        option.value = option.value.not()
                        updateSaveButton()
                    }
                )
            }
            tooltip(setting.description) {
                DefaultCheckbox(
                    option.value,
                    modifier = Modifier.height(30.dp)
                ) {
                    option.value = it
                    updateSaveButton()
                }
            }
        }
    }

    private enum class FieldType {
        THREADS, TIMEOUT, RETRY;
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
        } else if (type == FieldType.RETRY) {
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
            if (!saveFolder.value.fileExists()) {
                windowScope.showToast("The download folder doesn't exist.")
                return false
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
            if (it.value is String) {
                stringOptions[it]?.value = it.string()
            } else if (it.value is Boolean) {
                booleanOptions[it]?.value = it.boolean()
            } else if (it.value is Int) {
                intOptions[it]?.value = it.int()
            } else if (it.value is Long) {
                longOptions[it]?.value = it.long()
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
                            fieldCheckbox(it)
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
                verticalScrollbar(scrollState)
            }
        }
    }


    override fun onClose() {}

}