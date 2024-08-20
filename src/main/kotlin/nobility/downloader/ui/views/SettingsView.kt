package nobility.downloader.ui.views

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkrockstudios.libraries.mpfilepicker.DirectoryPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.BoxHelper.Companion.intString
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.BoxHelper.Companion.update
import nobility.downloader.core.Core
import nobility.downloader.core.settings.Defaults
import nobility.downloader.core.settings.Quality
import nobility.downloader.ui.components.*
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.utils.Constants.maxThreads
import nobility.downloader.utils.Constants.maxTimeout
import nobility.downloader.utils.Constants.minThreads
import nobility.downloader.utils.Constants.minTimeout
import nobility.downloader.utils.FrogLog
import nobility.downloader.utils.Tools
import nobility.downloader.utils.fileExists
import nobility.downloader.utils.tone
import org.jsoup.Jsoup
import java.util.regex.Matcher
import java.util.regex.Pattern

class SettingsView {

    private var threads by mutableStateOf(
        Defaults.DOWNLOAD_THREADS.intString()
    )
    private var timeout by mutableStateOf(
        Defaults.TIMEOUT.intString()
    )
    private var saveFolder by mutableStateOf(
        Defaults.SAVE_FOLDER.string()
    )
    private var proxy by mutableStateOf(
        Defaults.PROXY.string()
    )
    private var wcoDomain by mutableStateOf(
        Defaults.WCO_DOMAIN.string()
    )
    private var wcoExtension by mutableStateOf(
        Defaults.WCO_EXTENSION.string()
    )

    private var quality by mutableStateOf(Quality.LOW.tag)

    private var proxyEnabled = mutableStateOf(false)
    private var debugMessages = mutableStateOf(
        Defaults.SHOW_DEBUG_MESSAGES.boolean()
    )
    private var bypassDiskSpace = mutableStateOf(
        Defaults.BYPASS_DISK_SPACE.boolean()
    )
    private var showTooltips = mutableStateOf(
        Defaults.SHOW_TOOLTIPS.boolean()
    )

    private var consoleOnTop = mutableStateOf(
        Defaults.CONSOLE_ON_TOP.boolean()
    )

    private var headlessMode = mutableStateOf(
        Defaults.HEADLESS_MODE.boolean()
    )

    private var separateSeasons = mutableStateOf(
        Defaults.SEPARATE_SEASONS.boolean()
    )

    private var autoScrollConsoles = mutableStateOf(
        Defaults.AUTO_SCROLL_CONSOLES.boolean()
    )

    private var saveButtonEnabled = mutableStateOf(false)
    private lateinit var scope: AppWindowScope

    @Composable
    fun settingsUi(scope: AppWindowScope) {
        this.scope = scope
        Scaffold(
            bottomBar = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(15.dp)
                ) {
                    Row(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        defaultButton(
                            "Reset Settings",
                            width = 150.dp,
                            height = 40.dp
                        ) {
                            DialogHelper.showConfirm(
                                "Are you sure you want to reset your settings to the default ones?",
                                "Reset Settings"
                            ) {
                                BoxHelper.resetSettings()
                                updateValues()
                                scope.showToast("Settings have been reset.")
                            }
                        }
                        defaultButton(
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
            val scrollState = rememberScrollState(0)
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier.padding(
                        bottom = it.calculateBottomPadding(),
                        end = 12.dp
                    ).fillMaxWidth().verticalScroll(scrollState)
                ) {
                    fieldRow(Defaults.DOWNLOAD_THREADS)
                    fieldRow(Defaults.TIMEOUT)
                    fieldRow(Defaults.SAVE_FOLDER)
                    fieldDropdown(Defaults.QUALITY)
                    fieldRow(Defaults.PROXY)
                    fieldCheckbox(Defaults.SEPARATE_SEASONS)
                    fieldCheckbox(Defaults.SHOW_TOOLTIPS)
                    fieldCheckbox(Defaults.SHOW_DEBUG_MESSAGES)
                    fieldCheckbox(Defaults.BYPASS_DISK_SPACE)
                    fieldCheckbox(Defaults.CONSOLE_ON_TOP)
                    fieldCheckbox(Defaults.HEADLESS_MODE)
                    fieldCheckbox(Defaults.AUTO_SCROLL_CONSOLES)
                    fieldWcoDomain()
                    defaultButton(
                        "Open Database Folder",
                        width = 125.dp,
                        height = 35.dp
                    ) {
                        Tools.openFolder(BoxHelper.databasePath)
                    }
                    Text(
                        "Error Console",
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                            .padding(top = 5.dp, bottom = 5.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp
                    )
                    Core.errorConsole.textField()
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd)
                        .background(MaterialTheme.colorScheme.surface.tone(20.0))
                        .fillMaxHeight()
                        .padding(top = 3.dp, bottom = 3.dp),
                    style = ScrollbarStyle(
                        minimalHeight = 16.dp,
                        thickness = 10.dp,
                        shape = RoundedCornerShape(10.dp),
                        hoverDurationMillis = 300,
                        unhoverColor = MaterialTheme.colorScheme.surface.tone(50.0).copy(alpha = 0.70f),
                        hoverColor = MaterialTheme.colorScheme.surface.tone(50.0).copy(alpha = 0.90f)
                    ),
                    adapter = rememberScrollbarAdapter(
                        scrollState = scrollState
                    )
                )
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun fieldRow(
        setting: Defaults
    ) {
        val title = when (setting) {
            Defaults.DOWNLOAD_THREADS -> "Download Threads"
            Defaults.TIMEOUT -> "Network Timeout"
            Defaults.SAVE_FOLDER -> "Download Folder"
            Defaults.PROXY -> "Proxy"
            else -> ""
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
            when (setting) {
                Defaults.DOWNLOAD_THREADS -> {
                    defaultSettingsTextField(
                        threads,
                        { text ->
                            if (text.isNotEmpty()) {
                                threads = textToDigits(FieldType.THREADS, text)
                                updateSaveButton()
                            } else {
                                if (threads.isNotEmpty()) {
                                    threads = ""
                                }
                            }
                        },
                        numbersOnly = true,
                    )
                }

                Defaults.TIMEOUT -> {
                    defaultSettingsTextField(
                        timeout,
                        { text ->
                            if (text.isNotEmpty()) {
                                timeout = textToDigits(FieldType.TIMEOUT, text)
                                updateSaveButton()
                            } else {
                                if (timeout.isNotEmpty()) {
                                    timeout = ""
                                }
                            }
                        },
                        numbersOnly = true,
                    )
                }

                Defaults.SAVE_FOLDER -> {
                    defaultSettingsTextField(
                        saveFolder,
                        { text ->
                            if (text.isNotEmpty()) {
                                saveFolder = text
                                updateSaveButton()
                            } else {
                                if (saveFolder.isNotEmpty()) {
                                    saveFolder = ""
                                }
                            }
                        },
                        modifier = Modifier.height(30.dp).width(180.dp),
                    )
                    var showFilePicker by remember { mutableStateOf(false) }
                    DirectoryPicker(
                        show = showFilePicker,
                        initialDirectory = saveFolder
                    ) {
                        if (it != null) {
                            saveFolder = it
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

                Defaults.PROXY -> {
                    defaultSettingsTextField(
                        proxy,
                        { text ->
                            if (text.isNotEmpty()) {
                                proxy = text.trim()
                                updateSaveButton()
                            } else {
                                if (proxy.isNotEmpty()) {
                                    proxy = ""
                                }
                            }
                        },
                        enabled = proxyEnabled,
                        modifier = Modifier.height(30.dp).width(180.dp),
                    )
                    defaultButton(
                        "Test Proxy",
                        enabled = proxyEnabled,
                        modifier = Modifier.height(30.dp)
                    ) {
                        if (!isValidProxy()) {
                            return@defaultButton
                        }
                        val timeout = this@SettingsView.timeout.toInt()
                        scope.showToast("Testing proxy with timeout: $timeout")
                        Core.child.taskScope.launch(Dispatchers.IO) {
                            val website = "https://google.com"
                            try {
                                val split = proxy.split(":")
                                val response = Jsoup.connect(website)
                                    .proxy(split[0], split[1].toInt())
                                    .followRedirects(true)
                                    .timeout(timeout * 1000)
                                    .execute()
                                withContext(Dispatchers.Main) {
                                    if (response.statusCode() == 200) {
                                        scope.showToast("Proxy successfully connected to $website")
                                    } else {
                                        scope.showToast("Proxy failed to connect with status code: ${response.statusCode()}")
                                    }
                                }
                            } catch (e: Exception) {
                                DialogHelper.showError(
                                    "Failed to connect to $website with proxy.", e
                                )
                            }
                        }
                    }
                    defaultCheckbox(
                        proxyEnabled,
                        modifier = Modifier.height(30.dp)
                    ) {
                        proxyEnabled.value = it
                        updateSaveButton()
                    }
                    Text(
                        "Enable Proxy",
                        fontSize = 12.sp,
                        modifier = Modifier.onClick {
                            proxyEnabled.value = proxyEnabled.value.not()
                            updateSaveButton()
                        }
                    )
                }

                else -> {}
            }
        }
    }

    @Composable
    private fun fieldDropdown(
        setting: Defaults
    ) {
        val title = when (setting) {
            Defaults.QUALITY -> "Video Download Quality"
            else -> ""
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
                        quality = it.tag
                        updateSaveButton()
                    }
                }
                tooltip(setting.description) {
                    defaultDropdown(
                        quality,
                        expanded,
                        options,
                        onTextClick = { expanded = true }
                    ) { expanded = false }
                }
            }
        }
    }

    @Composable
    private fun fieldCheckbox(
        setting: Defaults
    ) {
        val title = when (setting) {
            Defaults.SHOW_DEBUG_MESSAGES -> "Show Debug Messages"
            Defaults.BYPASS_DISK_SPACE -> "Bypass Disk Space Check"
            Defaults.SHOW_TOOLTIPS -> "Show Tooltips"
            Defaults.CONSOLE_ON_TOP -> "Popout Window Always On Top"
            Defaults.HEADLESS_MODE -> "Headless Mode"
            Defaults.SEPARATE_SEASONS -> "Separate Seasons Into Folders"
            Defaults.AUTO_SCROLL_CONSOLES -> "Auto Scroll Consoles"
            else -> "Not Implemented"
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
            when (setting) {
                Defaults.SHOW_DEBUG_MESSAGES -> {
                    tooltip(setting.description) {
                        defaultCheckbox(
                            debugMessages,
                            modifier = Modifier.height(30.dp)
                        ) {
                            debugMessages.value = it
                            updateSaveButton()
                        }
                    }
                }
                Defaults.BYPASS_DISK_SPACE -> {
                    tooltip(setting.description) {
                        defaultCheckbox(
                            bypassDiskSpace,
                            modifier = Modifier.height(30.dp)
                        ) {
                            bypassDiskSpace.value = it
                            updateSaveButton()
                        }
                    }
                }
                Defaults.SHOW_TOOLTIPS -> {
                    tooltip(setting.description) {
                        defaultCheckbox(
                            showTooltips,
                            modifier = Modifier.height(30.dp)
                        ) {
                            showTooltips.value = it
                            updateSaveButton()
                        }
                    }
                }
                Defaults.CONSOLE_ON_TOP -> {
                    tooltip(setting.description) {
                        defaultCheckbox(
                            consoleOnTop,
                            modifier = Modifier.height(30.dp)
                        ) {
                            consoleOnTop.value = it
                            updateSaveButton()
                        }
                    }
                }
                Defaults.HEADLESS_MODE -> {
                    tooltip(setting.description) {
                        defaultCheckbox(
                            headlessMode,
                            modifier = Modifier.height(30.dp)
                        ) {
                            headlessMode.value = it
                            updateSaveButton()
                        }
                    }
                }
                Defaults.SEPARATE_SEASONS -> {
                    tooltip(setting.description) {
                        defaultCheckbox(
                            separateSeasons,
                            modifier = Modifier.height(30.dp)
                        ) {
                            separateSeasons.value = it
                            updateSaveButton()
                        }
                    }
                }

                Defaults.AUTO_SCROLL_CONSOLES -> {
                    tooltip(setting.description) {
                        defaultCheckbox(
                            autoScrollConsoles,
                            modifier = Modifier.height(30.dp)
                        ) {
                            autoScrollConsoles.value = it
                            updateSaveButton()
                        }
                    }
                }
                else -> {}
            }
        }
    }

    @Composable
    private fun fieldWcoDomain() {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(10.dp)
        ) {
            val tooltipText = """
                    Wcofun changes their domain a lot. This is used for the slug system to keep
                    all wcofun links updated.
                    Only use this option if you're having trouble connecting to wcofun.
                    These values auto update everytime the app starts.
                """.trimIndent()
            tooltip(tooltipText) {
                Text(
                    "Wcofun Domain:",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            tooltip(tooltipText) {
                Text(
                    "https://",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            defaultSettingsTextField(
                wcoDomain,
                {
                    wcoDomain = it
                    updateSaveButton()
                },
                modifier = Modifier.width(100.dp).height(30.dp)
            )
            Text(
                ".",
                style = MaterialTheme.typography.labelSmall
            )
            defaultSettingsTextField(
                wcoExtension,
                {
                    wcoExtension = it
                    updateSaveButton()
                },
                modifier = Modifier.width(40.dp).height(30.dp)
            )
            tooltip("""
                Executes the auto update function that happens everytime the app starts.
                The auto update function checks the links found in the text file and follows them to the latest redirect.
            """.trimIndent()) {
                defaultButton(
                    "Auto Update",
                    height = 30.dp,
                    width = 80.dp
                ) {
                    scope.showToast("[TODO]")
                }
            }
        }
    }

    private enum class FieldType {
        THREADS, TIMEOUT;
    }

    private fun textToDigits(
        type: FieldType,
        text: String
    ): String {
        var mText = text.filter { it.isDigit() }
        val num = mText.toIntOrNull()
        if (num != null) {
            if (type == FieldType.THREADS) {
                if (num < minThreads) {
                    mText = minThreads.toString()
                } else if (num > maxThreads) {
                    mText = maxThreads.toString()
                }
            } else if (type == FieldType.TIMEOUT) {
                if (num < minTimeout) {
                    if (mText.length > 1) {
                        mText = minTimeout.toString()
                    }
                } else if (num > maxTimeout) {
                    mText = maxTimeout.toString()
                }
            }
        }
        return mText
    }

    private fun updateSaveButton() {
        saveButtonEnabled.value = settingsChanged()
    }

    fun updateValues() {
        Defaults.entries.forEach {
            when (it) {
                Defaults.DOWNLOAD_THREADS -> {
                    threads = it.intString()
                }

                Defaults.TIMEOUT -> {
                    timeout = it.intString()
                }

                Defaults.SAVE_FOLDER -> {
                    saveFolder = it.string()
                }

                Defaults.PROXY -> {
                    proxy = it.string()
                }

                Defaults.ENABLE_PROXY -> {
                    proxyEnabled.value = it.boolean()
                }

                Defaults.QUALITY -> {
                    quality = it.string()
                }

                Defaults.SHOW_DEBUG_MESSAGES -> {
                    debugMessages.value = it.boolean()
                }

                Defaults.BYPASS_DISK_SPACE -> {
                    bypassDiskSpace.value = it.boolean()
                }

                Defaults.WCO_DOMAIN -> {
                    wcoDomain = it.string()
                }

                Defaults.WCO_EXTENSION -> {
                    wcoExtension = it.string()
                }

                Defaults.SHOW_TOOLTIPS -> {
                    showTooltips.value = it.boolean()
                }

                Defaults.CONSOLE_ON_TOP -> {
                    consoleOnTop.value = it.boolean()
                }

                Defaults.HEADLESS_MODE -> {
                    headlessMode.value = it.boolean()
                }

                Defaults.SEPARATE_SEASONS -> {
                    separateSeasons.value = it.boolean()
                }

                Defaults.AUTO_SCROLL_CONSOLES -> {
                    autoScrollConsoles.value = it.boolean()
                }
                else -> {}
            }
        }
        saveButtonEnabled.value = false
    }

    private fun isValidProxy(): Boolean {
        if (proxy.isEmpty()) {
            scope.showToast("Please enter a proxy first.")
            return false
        }
        val split = try {
            proxy.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        } catch (e: Exception) {
            scope.showToast("Failed to parse proxy.")
            return false
        }
        val ip: String
        val port: Int?
        try {
            if (split.size > 2) {
                scope.showToast("Kotlin doesn't support Username & Password Authentication.")
                return false
            } else if (split.size < 2) {
                scope.showToast("Please enter a valid proxy.")
                return false
            }
            ip = split[0]
            val regex = "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$"
            val pattern: Pattern = Pattern.compile(regex)
            val matcher: Matcher = pattern.matcher(ip)
            if (!matcher.matches()) {
                scope.showToast("The proxy ip isn't valid.")
                return false
            }
            port = split[1].toIntOrNull()
            if (port != null) {
                if (port < 1 || port > 65535) {
                    scope.showToast("The proxy port can only be 1-65535.")
                    return false
                }
            } else {
                scope.showToast("The proxy port needs to be numbers only.")
                return false
            }
        } catch (e: Exception) {
            scope.showToast("Something wen't wrong checking the proxy format. Look at the console for more info.")
            FrogLog.logError(
                "Failed to check the proxy in settings." +
                        "\nGo to Settings and scroll to the bottom for the entire exception.",
                e
            )
            return false
        }
        return true
    }

    fun saveSettings(): Boolean {
        if (threads.isEmpty()) {
            threads = Defaults.DOWNLOAD_THREADS.intString()
        }
        val threadsValue = threads.toIntOrNull()
        if (threadsValue == null || threadsValue < minThreads || threadsValue > maxThreads) {
            scope.showToast("You must enter a Download Threads value of $minThreads-$maxThreads.")
            return false
        }
        if (timeout.isEmpty()) {
            timeout = Defaults.SAVE_FOLDER.string()
        }
        val timeoutValue = timeout.toIntOrNull()
        if (timeoutValue == null || timeoutValue < minTimeout || timeoutValue > maxTimeout) {
            scope.showToast("You must enter a Network Timeout value of $minTimeout-$maxTimeout.")
            return false
        }
        if (saveFolder.isEmpty()) {
            saveFolder = Defaults.SAVE_FOLDER.string()
        }
        if (!saveFolder.fileExists()) {
            scope.showToast("The download folder doesn't exist.")
            return false
        }
        val proxyEnabledValue = proxyEnabled.value
        if (proxy.isEmpty() && proxyEnabledValue) {
            proxyEnabled.value = false
        } else if (proxy.isNotEmpty() && proxyEnabledValue) {
            if (!isValidProxy()) {
                return false
            }
        }
        if (wcoExtension.isNotEmpty()) {
            val acceptedDomains = listOf(
                "org", "net", "tv", "com", "io", "to",
            )
            wcoExtension = wcoExtension.replace("[^a-zA-Z]".toRegex(), "").lowercase()
            if (!acceptedDomains.contains(wcoExtension)) {
                DialogHelper.showError(
                    "Your domain extension $wcoExtension isn't allowed. Please use any of these:" +
                            "\n$acceptedDomains"
                )
                return false
            }
        }
        Defaults.DOWNLOAD_THREADS.update(threadsValue)
        Defaults.TIMEOUT.update(timeoutValue)
        Defaults.SAVE_FOLDER.update(saveFolder)
        Core.child.refreshDownloadsProgress()
        Defaults.PROXY.update(proxy)
        Defaults.ENABLE_PROXY.update(proxyEnabledValue)
        Defaults.QUALITY.update(quality)
        Defaults.SHOW_DEBUG_MESSAGES.update(debugMessages.value)
        Defaults.BYPASS_DISK_SPACE.update(bypassDiskSpace.value)
        Defaults.WCO_DOMAIN.update(wcoDomain)
        Defaults.WCO_EXTENSION.update(wcoExtension)
        Defaults.SHOW_TOOLTIPS.update(showTooltips.value)
        Defaults.CONSOLE_ON_TOP.update(consoleOnTop.value)
        Defaults.HEADLESS_MODE.update(headlessMode.value)
        Defaults.SEPARATE_SEASONS.update(separateSeasons.value)
        Defaults.AUTO_SCROLL_CONSOLES.update(autoScrollConsoles.value)
        updateValues()
        scope.showToast("Settings successfully saved.")
        return true
    }

    fun settingsChanged(): Boolean {
        return Defaults.TIMEOUT.intString() != timeout
                || Defaults.DOWNLOAD_THREADS.intString() != threads
                || Defaults.SAVE_FOLDER.string() != saveFolder
                || Defaults.PROXY.string() != proxy
                || Defaults.ENABLE_PROXY.boolean() != proxyEnabled.value
                || Defaults.QUALITY.string() != quality
                || Defaults.SHOW_DEBUG_MESSAGES.boolean() != debugMessages.value
                || Defaults.BYPASS_DISK_SPACE.boolean() != bypassDiskSpace.value
                || Defaults.WCO_DOMAIN.string() != wcoDomain
                || Defaults.WCO_EXTENSION.string() != wcoExtension
                || Defaults.SHOW_TOOLTIPS.boolean() != showTooltips.value
                || Defaults.CONSOLE_ON_TOP.boolean() != consoleOnTop.value
                || Defaults.HEADLESS_MODE.boolean() != headlessMode.value
                || Defaults.SEPARATE_SEASONS.boolean() != separateSeasons.value
                || Defaults.AUTO_SCROLL_CONSOLES.boolean() != autoScrollConsoles.value
    }

}