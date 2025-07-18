package nobility.downloader.core.driver.undetected_chrome

import com.alibaba.fastjson.JSONObject
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.driver.undetected_chrome.SysUtil.getString
import nobility.downloader.core.driver.undetected_chrome.SysUtil.isLinux
import nobility.downloader.core.driver.undetected_chrome.SysUtil.isMacOs
import nobility.downloader.core.driver.undetected_chrome.SysUtil.path
import nobility.downloader.core.settings.Defaults
import nobility.downloader.utils.AppInfo
import nobility.downloader.utils.FrogLog
import nobility.downloader.utils.fileExists
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.regex.Pattern

class ChromeDriverBuilder {

    private var keepUserDataDir = false
    private var userDataDir = ""
    private var binaryLocation = ""
    private var args = mutableListOf<String>()

    private fun build(
        options: ChromeOptions = ChromeOptions(),
        driverExecutablePath: String,
        binaryLocation: String = "",
        headless: Boolean = false,
        prefs: Map<String, Any>? = null
    ): ChromeDriver {
        //step 0, load origin args for options
        loadChromeOptionsArgs(options)

        //step 1，patcher replace cdc mark
        buildPatcher(driverExecutablePath)

        var chromeOptions = options

        //step 2, set host and port
        chromeOptions = setHostAndPort(chromeOptions)

        //step3, set user data dir
        chromeOptions = setUserDataDir(chromeOptions)

        //step4, set language
        chromeOptions = setLanguage(chromeOptions)

        //step5, set binaryLocation
        chromeOptions = setBinaryLocation(chromeOptions, binaryLocation)

        //step6, suppressWelcome
        chromeOptions = suppressWelcome(chromeOptions)

        //step7, set headless arguments
        chromeOptions = setHeadless(chromeOptions, headless)

        //step8, set logLevel
        chromeOptions = setLogLevel(chromeOptions)

        //step9 ,merge prefs
        if (prefs != null) {
            handlePrefs(userDataDir, prefs)
        }

        //step10, fix exit type
        chromeOptions = fixExitType(chromeOptions)

        //step11, start process
        val browser = createBrowserProcess(chromeOptions)

        //step12, make undetectedChrome chrome driver
        val undetectedChromeDriver = UndetectedChromeDriver(
            chromeOptions,
            headless,
            keepUserDataDir,
            userDataDir,
            browser
        )
        FrogLog.debug(
            "Built UndetectedChromeDriver with driver: ${System.getProperty("webdriver.chrome.driver")}"
        )
        return undetectedChromeDriver
    }

    @Suppress("UNCHECKED_CAST")
    fun build(
        options: ChromeOptions,
        driverExecutablePath: String,
        binaryLocation: String = ""
    ): ChromeDriver {
        var headless = false
        try {
            val argsField = options.javaClass.superclass.getDeclaredField("args")
            argsField.isAccessible = true
            val args = argsField[options] as List<String>
            if (args.contains("--headless")
                || args.contains("--headless=new")
                || args.contains("--headless=chrome")
            ) {
                headless = true
            }
        } catch (e: Exception) {
            FrogLog.error(
                "Failed to add headless to the driver.",
                e
            )
        }

        var prefs: Map<String, Any>? = null
        try {
            val argsField = options.javaClass.superclass
                .getDeclaredField("experimentalOptions")
            argsField.isAccessible = true
            val args = argsField[options] as MutableMap<String, Any>
            if (args.containsKey("prefs")) {
                prefs = HashMap(args["prefs"] as Map<String, Any>)
                args.remove("prefs")
            }
        } catch (e: Exception) {
            FrogLog.error(
                "Failed to set preferences for the driver.",
                e
            )
        }

        return build(
            options,
            driverExecutablePath,
            binaryLocation,
            headless,
            prefs
        )
    }

    private fun buildPatcher(driverExecutablePath: String) {
        val patcher = Patcher(driverExecutablePath)
        try {
            patcher.auto()
        } catch (_: Exception) {
            throw RuntimeException("CDC patcher failed.")
        }
    }

    /**
     * step2：find a free port and set host in options
     *
     * @param chromeOptions
     * @throws RuntimeException
     */
    @Suppress("UNCHECKED_CAST")
    private fun setHostAndPort(chromeOptions: ChromeOptions): ChromeOptions {
        //debug host and port
        var debugHost: String? = null
        var debugPort = -1
        args.forEach { arg ->
            if (arg.contains("--remote-debugging-host")) {
                try {
                    debugHost = arg.split("=".toRegex())
                        .dropLastWhile { it.isEmpty() }
                        .toTypedArray()[1]
                } catch (_: Exception) {
                }
            }
            if (arg.contains("--remote-debugging-port")) {
                try {
                    debugPort = arg.split("=".toRegex())
                        .dropLastWhile { it.isEmpty() }
                        .toTypedArray()[1].toInt()
                } catch (_: Exception) {
                }
            }
        }
        if (debugHost == null) {
            debugHost = "127.0.0.1"
            chromeOptions.addArguments("--remote-debugging-host=$debugHost")
        }
        if (debugPort == -1) {
            debugPort = findFreePort()
        }
        if (debugPort == -1) {
            throw RuntimeException("Free port not found for chrome debugger.")
        } else {
            chromeOptions.addArguments("--remote-debugging-port=$debugPort")
        }

        try {
            val experimentalOptions = chromeOptions.javaClass
                .superclass.getDeclaredField("experimentalOptions")
            experimentalOptions.isAccessible = true
            val experimentals = experimentalOptions[chromeOptions] as Map<String, Any?>
            if (experimentals["debuggerAddress"] != null) {
                return chromeOptions
            }
        } catch (_: Exception) {
        }
        chromeOptions.addArguments("--remote-allow-origins=*")
        chromeOptions.setExperimentalOption("debuggerAddress", "$debugHost:$debugPort")
        return chromeOptions
    }

    /**
     * step3: set user data dir arg for chromeOptions
     *
     * @param chromeOptions
     * @return
     * @throws RuntimeException
     */
    @Throws(RuntimeException::class)
    private fun setUserDataDir(chromeOptions: ChromeOptions): ChromeOptions {
        //find user data dir in chromeOptions
        val dirArg = args.find { it.contains("--user-data-dir") }
        if (dirArg != null) {
            try {
                userDataDir = dirArg.split("=".toRegex())
                    .dropLastWhile { it.isEmpty() }
                    .toTypedArray()[1]
            } catch (_: Exception) {
            }
        }
        if (userDataDir.isEmpty()) {
            //no user data dir in it
            keepUserDataDir = false
            try {
                userDataDir = AppInfo.databasePath + "udc_temp" +
                        File.separator +
                        "udc_${System.currentTimeMillis()}" +
                        File.separator
            } catch (_: Exception) {
                throw RuntimeException("Failed to create temp user data directory.")
            }
            //add into options
            chromeOptions.addArguments("--user-data-dir=$userDataDir")
        } else {
            keepUserDataDir = true
        }
        return chromeOptions
    }

    /**
     * step4: set browser language
     *
     * @param chromeOptions
     * @return
     * @throws RuntimeException
     */
    private fun setLanguage(chromeOptions: ChromeOptions): ChromeOptions {
        for (arg in args) {
            if (arg.contains("--lang=")) {
                return chromeOptions
            }
        }
        //no argument lang
        val language = Locale.getDefault().language.replace("_", "-")
        chromeOptions.addArguments("--lang=$language")
        return chromeOptions
    }

    /**
     * step5: find and set chrome BinaryLocation
     *
     * @param chromeOptions
     * @return
     */
    private fun setBinaryLocation(
        chromeOptions: ChromeOptions,
        binaryLocation: String
    ): ChromeOptions {
        var mBinaryLocation = binaryLocation
        if (mBinaryLocation.isEmpty() || !mBinaryLocation.fileExists()) {
            mBinaryLocation = chromePath()
            if (mBinaryLocation.isEmpty()) {
                FrogLog.error(
                    """
                        Chrome isn't installed. Install it and restart the app.
                        For some help visit: 
                        https://github.com/NobilityDeviant/ZenDownloader/#download--install
                    """.trimIndent(),
                    important = true
                )
                throw RuntimeException("Failed to find chrome binary.")
            }
            chromeOptions.setBinary(mBinaryLocation)
        } else {
            chromeOptions.setBinary(mBinaryLocation)
        }
        this.binaryLocation = mBinaryLocation
        return chromeOptions
    }

    /**
     * step 6: suppressWelcome
     *
     * @param chromeOptions
     * @param suppressWelcome
     * @return
     */
    private fun suppressWelcome(
        chromeOptions: ChromeOptions,
    ): ChromeOptions {
        if (!args.contains("--no-default-browser-check")) {
            chromeOptions.addArguments("--no-default-browser-check")
        }
        if (!args.contains("--no-first-run")) {
            chromeOptions.addArguments("--no-first-run")
        }
        return chromeOptions
    }

    /**
     * step7, set headless arg
     *
     * @param chromeOptions
     * @param headless
     * @return
     */
    private fun setHeadless(
        chromeOptions: ChromeOptions,
        headless: Boolean
    ): ChromeOptions {
        if (headless) {
            if (!args.contains("--headless=new")
                || !args.contains("--headless=chrome")
                || !args.contains("--headless=old")
            ) {
                //we consider that the chromedriver version is greater than 108.x.x.x
                chromeOptions.addArguments("--headless=new")
                //chromeOptions.addArguments("--headless=old")
            }
            var hasWindowSize = false
            for (arg in args) {
                if (arg.contains("--window-size=")) {
                    hasWindowSize = true
                    break
                }
            }
            if (!hasWindowSize) {
                chromeOptions.addArguments("--window-size=1920,1080")
            }
            if (!args.contains("--start-maximized")) {
                chromeOptions.addArguments("--start-maximized")
            }
            if (!args.contains("--no-sandbox")) {
                chromeOptions.addArguments("--no-sandbox")
            }
        }
        return chromeOptions
    }

    /**
     * step8, set log level
     *
     * @param chromeOptions
     * @return
     */
    private fun setLogLevel(chromeOptions: ChromeOptions): ChromeOptions {
        for (arg in args) {
            if (arg.contains("--log-level=")) {
                return chromeOptions
            }
        }
        chromeOptions.addArguments("--log-level=0")
        return chromeOptions
    }

    /**
     * step9, add prefs into user dir
     *
     * @param userDataDir T
     * @param prefs p
     */
    @Throws(RuntimeException::class)
    private fun handlePrefs(userDataDir: String?, prefs: Map<String, Any>) {
        val defaultPath = userDataDir + File.separator + "Default"
        val defaultFile = File(defaultPath)
        if (!defaultFile.exists() && !defaultFile.mkdirs()) {
            throw RuntimeException("Failed to create Default path folders.")
        }
        var newPrefs: MutableMap<String, Any>

        val prefsFilePath = defaultPath + File.separator + "Preferences"
        val prefsFile = File(prefsFilePath)
        if (prefsFile.exists()) {
            try {
                prefsFile.bufferedReader(StandardCharsets.ISO_8859_1).use { br ->
                    var line: String?
                    val sb = StringBuilder()
                    while ((br.readLine().also { line = it }) != null) {
                        sb.append(line)
                        sb.appendLine()
                    }
                    newPrefs = JSONObject.parseObject(
                        sb.toString()
                    ).innerMap
                }
            } catch (_: Exception) {
                throw RuntimeException("Default preferences directory not found.")
            }

            try {
                prefs.entries.forEach { pref ->
                    undotMerge(
                        pref.key,
                        pref.value,
                        newPrefs
                    )
                }
            } catch (_: Exception) {
                throw RuntimeException("Failed to merge preferences.")
            }

            try {
                prefsFile.bufferedWriter(StandardCharsets.ISO_8859_1).use { bw ->
                    bw.write(JSONObject.toJSONString(newPrefs))
                    bw.flush()
                }
            } catch (_: Exception) {
                throw RuntimeException("Failed to write preferences to file.")
            }
        }
    }

    /**
     * step10, fix exit type
     *
     * @param chromeOptions f
     * @return o
     */
    private fun fixExitType(chromeOptions: ChromeOptions): ChromeOptions {
        try {
            val filePath = userDataDir + File.separator +
                    "Default" + File.separator + "Preferences"
            val file = File(filePath)
            val jsonStr = StringBuilder()
            file.bufferedReader(StandardCharsets.ISO_8859_1).use { reader ->
                var line: String?
                while ((reader.readLine().also { line = it }) != null) {
                    jsonStr.append(line)
                    jsonStr.appendLine()
                }
            }
            var json = jsonStr.toString()
            val pattern = Pattern.compile("(?<=exit_type\"\":)(.*?)(?=,)")
            val matcher = pattern.matcher(json)
            if (matcher.find()) {
                json = json.replace(matcher.group(), "null")
                file.bufferedWriter(StandardCharsets.ISO_8859_1).use { writer ->
                    writer.write(json)
                }
            }
        } catch (_: Exception) {
        }
        return chromeOptions
    }

    /**
     * step11: open chrome by args on new process
     *
     * @param chromeOptions o
     * @return p
     */
    private fun createBrowserProcess(
        chromeOptions: ChromeOptions
    ): Process {
        loadChromeOptionsArgs(chromeOptions)
        args.add(0, binaryLocation)
        return ProcessBuilder(args).start()
    }

    private fun findFreePort(): Int {
        var socket: ServerSocket? = null
        try {
            socket = ServerSocket(0)
            return socket.getLocalPort()
        } catch (_: Exception) {
            return -1
        } finally {
            try {
                socket?.close()
            } catch (_: IOException) {
            }
        }
    }

    /**
     * find args in chromeOptions
     *
     * @param chromeOptions
     */
    @Suppress("UNCHECKED_CAST")
    private fun loadChromeOptionsArgs(chromeOptions: ChromeOptions) {
        try {
            val argsField = chromeOptions.javaClass.superclass.getDeclaredField("args")
            argsField.isAccessible = true
            args = ArrayList(argsField[chromeOptions] as List<String>)
        } catch (_: Exception) {
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun undotMerge(key: String, value: Any, dict: MutableMap<String, Any>) {
        if (key.contains(".")) {
            val splits = key.split("\\.".toRegex(), limit = 2).toTypedArray()
            val k1 = splits[0]
            val k2 = splits[1]

            if (!dict.containsKey(k1)) {
                dict[k1] = HashMap<String, Any>()
            }
            try {
                undotMerge(k2, value, dict[k1] as MutableMap<String, Any>)
            } catch (_: Exception) {
            }
            return
        }
        dict[key] = value
    }

    companion object {

        fun isChromeInstalled(): Boolean {
            val chromePath = Defaults.CHROME_BROWSER_PATH.string()
            val chromeDriverPath = Defaults.CHROME_DRIVER_PATH.string()
            if (chromePath.isNotEmpty()
                && chromePath.fileExists()
                && chromeDriverPath.isNotEmpty()
                && chromeDriverPath.fileExists()
            ) {
                return true
            }
            return chromePath().isNotBlank()
        }

        fun chromePath(): String {
            var chromeDataPath = ""
            val isPosix = isMacOs || isLinux
            val possibles = mutableSetOf<String>()
            if (isPosix) {
                val names = mutableListOf(
                    "google-chrome",
                    "chromium",
                    "chromium-browser",
                    "chrome",
                    "google-chrome-stable"
                )
                path.forEach { path ->
                    names.forEach { name ->
                        possibles.add(path + File.separator + name)
                    }
                }
                if (isMacOs) {
                    possibles.add("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")
                    possibles.add("/Applications/Chromium.app/Contents/MacOS/Chromium")
                }
            } else {
                val paths = listOf(
                    getString("PROGRAMFILES"),
                    getString("PROGRAMFILES(X86)"),
                    getString("LOCALAPPDATA")
                )
                val middles = listOf(
                    "Google" + File.separator + "Chrome" + File.separator + "Application",
                    "Google" + File.separator + "Chrome Beta" + File.separator + "Application",
                    "Google" + File.separator + "Chrome Canary" + File.separator + "Application"
                )
                paths.forEach { path ->
                    middles.forEach { middle ->
                        possibles.add(path + File.separator + middle + File.separator + "chrome.exe")
                    }
                }
            }

            for (possible in possibles) {
                val file = File(possible)
                if (file.exists() && file.canExecute()) {
                    chromeDataPath = file.absolutePath
                    break
                }
            }
            return chromeDataPath
        }
    }
}
