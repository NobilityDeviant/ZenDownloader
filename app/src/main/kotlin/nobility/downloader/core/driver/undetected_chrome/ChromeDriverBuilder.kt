package nobility.downloader.core.driver.undetected_chrome

import AppInfo
import com.alibaba.fastjson.JSONObject
import nobility.downloader.utils.FrogLog
import nobility.downloader.utils.fileExists
import nobility.downloader.utils.fixForFiles
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ChromeDriverBuilder {

    private var keepUserDataDir = false
    private var userDataDir = ""
    private var binaryLocation = ""
    private var args = mutableListOf<String>()
    private var chromeProcess: Process? = null
    private var debugPort: Int = -1

    @Suppress("UNCHECKED_CAST")
    fun build(
        chromeOptions: ChromeOptions,
        driverExecutablePath: String,
        binaryLocation: String = "",
        headless: Boolean = true
    ): ChromeDriver {
        try {
            fetchInternalChromeOptions(chromeOptions)
        } catch (_: Exception) {
            throw Exception("Failed to load internal chrome options.")
        }
        if (!args.contains("--no-default-browser-check")) {
            chromeOptions.addArguments("--no-default-browser-check")
        }
        if (!args.contains("--no-first-run")) {
            chromeOptions.addArguments("--no-first-run")
        }
        if (!args.contains("--log-level")) {
            chromeOptions.addArguments("--log-level=0")
        }
        var prefs: Map<String, Any>? = null
        try {
            val argsField = chromeOptions.javaClass.superclass
                .getDeclaredField("experimentalOptions")
            argsField.isAccessible = true
            val args = argsField[chromeOptions] as MutableMap<String, Any>
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
            chromeOptions,
            driverExecutablePath,
            binaryLocation,
            headless,
            prefs
        )
    }

    private fun build(
        options: ChromeOptions = ChromeOptions(),
        driverExecutablePath: String,
        binaryLocation: String = "",
        headless: Boolean = false,
        prefs: Map<String, Any>? = null
    ): ChromeDriver {
        buildPatcher(driverExecutablePath)
        var mOptions = options
        mOptions = setHostAndPort(mOptions)
        mOptions = setUserDataDir(mOptions)
        mOptions = setBinaryLocation(mOptions, binaryLocation)
        mOptions = setHeadless(mOptions, headless)
        prefs?.let {
            handlePrefs(userDataDir, it)
        }
        mOptions = fixExitType(mOptions)
        chromeProcess = try {
            createBrowserProcess(mOptions)
        } catch (e: Exception) {
            throw Exception(
                "Failed to create chrome debugger process. Error: ${e.localizedMessage}"
            )
        }
        val undetectedChromeDriver = UndetectedChromeDriver(
            mOptions,
            headless,
            keepUserDataDir,
            userDataDir,
            chromeProcess!!
        )
        return undetectedChromeDriver
    }

    private fun buildPatcher(driverExecutablePath: String) {
        val patcher = Patcher(driverExecutablePath)
        try {
            patcher.auto()
        } catch (_: Exception) {
            throw RuntimeException("CDC patcher failed.")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun setHostAndPort(chromeOptions: ChromeOptions): ChromeOptions {
        var debugHost: String? = null
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

    @OptIn(ExperimentalUuidApi::class)
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
                        "udc_${Uuid.random().toString().fixForFiles()}" +
                        File.separator
            } catch (_: Exception) {
                throw RuntimeException("Failed to create temp user data directory.")
            }
            chromeOptions.addArguments("--user-data-dir=$userDataDir")
        } else {
            keepUserDataDir = true
        }
        return chromeOptions
    }

    private fun setBinaryLocation(
        chromeOptions: ChromeOptions,
        binaryLocation: String
    ): ChromeOptions {
        var mBinaryLocation = binaryLocation
        if (mBinaryLocation.isEmpty() || !mBinaryLocation.fileExists()) {
            mBinaryLocation = ChromeUtils.chromePath()
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
            //bug in headless so we have to hide it off-screen.
            if (!args.contains("--window-position")) {
                chromeOptions.addArguments("--window-position=-2400,-2400")
            }
        }
        return chromeOptions
    }

    /**
     * step9, add prefs into user dir
     *
     * @param userDataDir T
     * @param prefs p
     */
    @Throws(RuntimeException::class)
    private fun handlePrefs(
        userDataDir: String?,
        prefs: Map<String, Any>
    ) {
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
                //todo this doesnt do anything
                prefs.entries.forEach { pref ->
                    ChromeUtils.removeMergeDot(
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

    private fun fixExitType(
        chromeOptions: ChromeOptions
    ): ChromeOptions {
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

    private fun createBrowserProcess(
        chromeOptions: ChromeOptions
    ): Process {
        args.clear()
        try {
            fetchInternalChromeOptions(chromeOptions)
        } catch (_: Exception) {
            throw Exception("Failed to load internal chrome options.")
        }
        args.add(0, binaryLocation)
        return ProcessBuilder(args).start()
    }

    private fun findFreePort(): Int {
        return PortManager.allocatePort()
    }

    @Suppress("UNCHECKED_CAST")
    private fun fetchInternalChromeOptions(
        chromeOptions: ChromeOptions
    ) {
        val argsField = chromeOptions.javaClass.superclass.getDeclaredField("args")
        argsField.isAccessible = true
        args = ArrayList(argsField[chromeOptions] as List<String>)
    }



    fun close() {
        try {
            chromeProcess?.destroy()
        } catch (_: Exception) {}
        PortManager.releasePort(debugPort)
    }
}
