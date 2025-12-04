package nobility.downloader.core.driver.undetected_chrome

import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.driver.undetected_chrome.SysUtil.getString
import nobility.downloader.core.driver.undetected_chrome.SysUtil.isLinux
import nobility.downloader.core.driver.undetected_chrome.SysUtil.isMacOs
import nobility.downloader.core.driver.undetected_chrome.SysUtil.path
import nobility.downloader.core.settings.Defaults
import nobility.downloader.utils.fileExists
import java.io.File

object ChromeUtils {

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

    fun isDriverSetup(): Boolean {
        val chromePath = Defaults.CHROME_BROWSER_PATH.string()
        val chromeDriverPath = Defaults.CHROME_DRIVER_PATH.string()
        if (chromePath.fileExists() && chromeDriverPath.fileExists()
        ) {
            return true
        }
        return System.getProperty("webdriver.chrome.driver").fileExists()

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

    @Suppress("UNCHECKED_CAST")
    fun removeMergeDot(
        key: String,
        value: Any,
        dict: MutableMap<String, Any>
    ) {
        if (key.contains(".")) {
            val splits = key.split("\\.".toRegex(), limit = 2).toTypedArray()
            val k1 = splits[0]
            val k2 = splits[1]

            if (!dict.containsKey(k1)) {
                dict[k1] = HashMap<String, Any>()
            }
            try {
                removeMergeDot(k2, value, dict[k1] as MutableMap<String, Any>)
            } catch (_: Exception) {
            }
            return
        }
        dict[key] = value
    }
}