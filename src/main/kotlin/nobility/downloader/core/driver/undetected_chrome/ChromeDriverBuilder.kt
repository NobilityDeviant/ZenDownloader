package nobility.downloader.core.driver.undetected_chrome

import com.alibaba.fastjson.JSONObject
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.driver.undetected_chrome.SysUtil.getString
import nobility.downloader.core.driver.undetected_chrome.SysUtil.isLinux
import nobility.downloader.core.driver.undetected_chrome.SysUtil.isMacOs
import nobility.downloader.core.driver.undetected_chrome.SysUtil.path
import nobility.downloader.core.settings.Defaults
import nobility.downloader.utils.FrogLog
import nobility.downloader.utils.fileExists
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import java.io.*
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.nio.file.Files
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
        suppressWelcome: Boolean = true,
        needPrintChromeInfo: Boolean = false,
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
        chromeOptions = suppressWelcome(chromeOptions, suppressWelcome)

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
        val browser = createBrowserProcess(chromeOptions, needPrintChromeInfo)

        //step12, make undetectedChrome chrome driver
        val undetectedChromeDriver = UndetectedChromeDriver(
            chromeOptions,
            headless,
            keepUserDataDir,
            userDataDir,
            browser
        )

        return undetectedChromeDriver
    }

    @Suppress("UNCHECKED_CAST")
    fun build(
        options: ChromeOptions,
        driverExecutablePath: String,
        binaryLocation: String = "",
        suppressWelcome: Boolean = true,
        needPrintChromeInfo: Boolean = false
    ): ChromeDriver {
        var headless = false
        try {
            val argsField = options.javaClass.superclass.getDeclaredField("args")
            argsField.isAccessible = true
            val args = argsField[options] as List<String>
            if (args.contains("--headless") || args.contains("--headless=new") || args.contains("--headless=chrome")) {
                headless = true
            }
        } catch (e: Exception) {
            FrogLog.logError(
                "Failed to add headless to the driver.",
                e
            )
        }

        var prefs: Map<String, Any>? = null
        try {
            val argsField = options.javaClass.superclass.getDeclaredField("experimentalOptions")
            argsField.isAccessible = true
            val args = argsField[options] as MutableMap<String, Any>
            if (args.containsKey("prefs")) {
                prefs = HashMap(args["prefs"] as Map<String, Any>)
                args.remove("prefs")
            }
        } catch (e: Exception) {
            FrogLog.logError(
                "Failed to set preferences for the driver.",
                e
            )
        }

        return build(
            options,
            driverExecutablePath,
            binaryLocation,
            headless,
            suppressWelcome,
            needPrintChromeInfo,
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
                    debugHost = arg.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
                } catch (_: Exception) {}
            }
            if (arg.contains("--remote-debugging-port")) {
                try {
                    debugPort = arg.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1].toInt()
                } catch (_: Exception) {}
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
            val experimentalOptions = chromeOptions.javaClass.superclass.getDeclaredField("experimentalOptions")
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
        for (arg: String in args) {
            if (arg.contains("--user-data-dir")) {
                try {
                    userDataDir = arg.split("=".toRegex())
                        .dropLastWhile { it.isEmpty() }
                        .toTypedArray()[1]
                } catch (_: Exception) {}
                break
            }
        }
        if (userDataDir.isEmpty()) {
            //no user data dir in it
            keepUserDataDir = false
            try {
                //create temp dir
                userDataDir = Files.createTempDirectory("undetected_chrome_driver").toString()
            } catch (e: Exception) {
                e.printStackTrace()
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
                FrogLog.logError(
                    """
                        Chrome isn't installed. Install it and restart the app.
                        For some help visit: https://github.com/NobilityDeviant/ZenDownloader/#download--install
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
        suppressWelcome: Boolean
    ): ChromeOptions {
        if (suppressWelcome) {
            if (!args.contains("--no-default-browser-check")) {
                chromeOptions.addArguments("--no-default-browser-check")
            }
            if (!args.contains("--no-first-run")) {
                chromeOptions.addArguments("--no-first-run")
            }
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
            if (!args.contains("--headless=new") || !args.contains("--headless=chrome") || !args.contains("--headless=old")) {
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
        if (!defaultFile.exists()) {
            defaultFile.mkdirs()
        }

        var newPrefs: MutableMap<String, Any>

        val prefsFilePath = defaultPath + File.separator + "Preferences"
        val prefsFile = File(prefsFilePath)
        if (prefsFile.exists()) {
            try {
                BufferedReader(FileReader(prefsFile, StandardCharsets.ISO_8859_1)).use { br ->
                    var line: String?
                    val stringBuilder: StringBuilder = StringBuilder()
                    while ((br.readLine().also { line = it }) != null) {
                        stringBuilder.append(line)
                        stringBuilder.append("\n")
                    }
                    newPrefs = JSONObject.parseObject(stringBuilder.toString()).innerMap
                }
            } catch (_: Exception) {
                throw RuntimeException("Default preferences directory not found.")
            }

            try {
                prefs.entries.forEach { pref ->
                    undotMerge(pref.key, pref.value, newPrefs)
                }
            } catch (_: Exception) {
                throw RuntimeException("Failed to merge preferences.")
            }

            try {
                BufferedWriter(FileWriter(prefsFilePath, StandardCharsets.ISO_8859_1)).use { bw ->
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
        var reader: BufferedReader? = null
        var writer: BufferedWriter? = null
        try {
            val filePath = userDataDir + File.separator + "Default" + File.separator + "Preferences"
            reader = BufferedReader(FileReader(filePath, StandardCharsets.ISO_8859_1))
            var line: String?
            val jsonStr = StringBuilder()
            while ((reader.readLine().also { line = it }) != null) {
                jsonStr.append(line)
                jsonStr.append("\n")
            }
            reader.close()
            var json = jsonStr.toString()
            val pattern = Pattern.compile("(?<=exit_type\"\":)(.*?)(?=,)")
            val matcher = pattern.matcher(json)
            if (matcher.find()) {
                writer = BufferedWriter(FileWriter(filePath, StandardCharsets.ISO_8859_1))
                json = json.replace(matcher.group(), "null")
                writer.write(json)
                writer.close()
            }
        } catch (_: Exception) {
        } finally {
            if (reader != null) {
                try {
                    reader.close()
                } catch (_: Exception) {
                }
            }
            if (writer != null) {
                try {
                    writer.close()
                } catch (_: Exception) {
                }
            }
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
        chromeOptions: ChromeOptions,
        needPrintChromeInfo: Boolean
    ): Process {
        loadChromeOptionsArgs(chromeOptions)
        args.add(0, binaryLocation)
        val process = ProcessBuilder(args).start()

        val outputThread = Thread {
            try {
                val br = process.inputStream.bufferedReader()
                br.readLines().forEach {
                    FrogLog.logDebug("[BROWSER] $it")
                }
                br.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val errorPutThread = Thread {
            try {
                val er = process.errorStream.bufferedReader()
                er.readLines().forEach {
                    FrogLog.logError("[BROWSER] $it")
                }
                er.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (needPrintChromeInfo) {
            outputThread.start()
            errorPutThread.start()
        }

        return process
    }

    private fun findFreePort(): Int {
        var socket: ServerSocket? = null
        try {
            socket = ServerSocket(0)
            return socket.getLocalPort()
        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        } finally {
            try {
                socket?.close()
            } catch (e: IOException) {
                e.printStackTrace()
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
