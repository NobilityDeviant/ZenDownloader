package nobility.downloader.core.driver

import kotlinx.coroutines.*
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.Core
import nobility.downloader.core.driver.undetected_chrome.ChromeDriverBuilder
import nobility.downloader.core.driver.undetected_chrome.UndetectedChromeDriver
import nobility.downloader.core.settings.Defaults
import nobility.downloader.core.settings.Quality
import nobility.downloader.utils.FrogLog
import nobility.downloader.utils.JavascriptHelper
import nobility.downloader.utils.fileExists
import nobility.downloader.utils.user_agents.UserAgents
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.ExpectedCondition
import org.openqa.selenium.support.ui.WebDriverWait
import java.time.Duration
import java.util.*
import java.util.logging.Level

abstract class DriverBase(
    var userAgent: String = "",
    headless: Boolean? = null,
    manualSetup: Boolean = false
) {

    private val id = UUID.randomUUID().toString()
    private val chromeOptions = ChromeOptions()
    private var nDriver: WebDriver? = null
    val driver get() = nDriver!!
    val undriver get() = driver as UndetectedChromeDriver
    private var isSetup = false
    private val headless = headless ?: Defaults.HEADLESS_MODE.boolean()
    private val browserLogCoroutine = CoroutineScope(Dispatchers.Default)
    private val browserLogs = mutableListOf<String>()
    private val builder = ChromeDriverBuilder()


    init {
        if (!manualSetup) {
            setup()
        }
    }

    fun setup() {
        if (isSetup) {
            return
        }
        try {
            setupDriver()
            //discovered you can actually read the browsers console logs
            //this will be used to print the video links and errors.
            if (driver.manage().logs().availableLogTypes.contains("browser")) {
                browserLogCoroutine.launch {
                    while (isActive) {
                        try {
                            driver.manage().logs().get("browser").all.forEach {
                                if (it.level != Level.SEVERE) {
                                    if (!browserLogs.contains(it.message)) {
                                        browserLogs.add(it.message)
                                        //FrogLog.writeMessage("[${it.level.name}]" + it.message)
                                    }
                                }
                            }
                        } catch (_: Exception) {
                        }
                        delay(25)
                    }
                }
            }
        } catch (e: Exception) {
            FrogLog.error(
                "Failed to set up Chrome.",
                e
            )
        }
    }

    suspend fun waitForSetup() {
        var checks = 0
        while (!isSetup) {
            delay(500)
            checks++
            if (checks >= 250) {
                throw Exception("waitForSetup took too long.")
            }
        }
    }

    /**
     * Used to set up the web driver.
     * This should only be called once unless you are debugging.
     */
    private fun setupDriver() {
        if (isSetup) {
            return
        }
        if (userAgent.isEmpty()) {
            userAgent = UserAgents.random
        }
        chromeOptions.addArguments("--mute-audio")
        chromeOptions.addArguments("--user-agent=$userAgent")
        val chromePath = Defaults.CHROME_BROWSER_PATH.string()
        val chromeDriverPath = Defaults.CHROME_DRIVER_PATH.string()
        if (chromePath.isNotEmpty()
            && chromePath.fileExists()
            && chromeDriverPath.isNotEmpty()
            && chromeDriverPath.fileExists()
        ) {
            FrogLog.info(
                """
                    Using chrome browser from settings.
                    Chrome Browser Path: $chromePath
                    Chrome Driver Path: $chromeDriverPath
                """.trimIndent()
            )
            System.setProperty("webdriver.chrome.driver", chromeDriverPath)
            nDriver = builder.build(
                chromeOptions = chromeOptions,
                driverExecutablePath = chromeDriverPath,
                binaryLocation = chromePath,
                headless
            )
        } else {
            val exportedChromeDriver = System.getProperty("webdriver.chrome.driver")
            nDriver = if (exportedChromeDriver.fileExists()) {
                builder.build(
                    chromeOptions = chromeOptions,
                    driverExecutablePath = exportedChromeDriver,
                    headless = headless
                )
            } else {
                throw Exception("chromedriver file not found.")
            }
        }

        val timeout = Defaults.TIMEOUT.int().toLong()
        driver.manage().timeouts().implicitlyWait(
            Duration.ofSeconds(timeout)
        )
        driver.manage().timeouts().pageLoadTimeout(
            Duration.ofSeconds(timeout)
        )
        driver.manage().timeouts().scriptTimeout(
            Duration.ofSeconds(timeout)
        )
        Core.child.runningDrivers.put(id, driver)
        isSetup = true
    }

    fun executeJs(script: String) {
        if (!isSetup) {
            FrogLog.error(
                "Failed to execute Javascript. The driver isn't set up properly."
            )
            return
        }
        if (driver !is JavascriptExecutor) {
            FrogLog.error(
                "Failed to execute script: $script",
                "This browser doesn't support JavascriptExecutor."
            )
            return
        }
        val js = driver as JavascriptExecutor
        js.executeScript(script)
    }

    fun waitForPageJs(
        timeout: Duration = Duration.ofSeconds(
            Defaults.TIMEOUT.int().toLong()
        )
    ) {
        if (!isSetup) {
            FrogLog.error(
                "Failed to execute JS wait. The driver isn't set up properly."
            )
            return
        }
        if (driver !is JavascriptExecutor) {
            FrogLog.error(
                "Failed to execute waitForPageToLoad.",
                "This browser doesn't support JavascriptExecutor."
            )
            return
        }
        try {
            val wait = WebDriverWait(driver, timeout)
            wait.until(ExpectedCondition {
                (driver as JavascriptExecutor).executeScript("return document.readyState") == "complete"
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun killDriver() {
        try {
            builder.close()
            browserLogCoroutine.cancel()
            try {
                driver.close()
            } catch (_: Exception) {}
            driver.quit()
        } catch (_: Exception) {
        } finally {
            Core.child.runningDrivers.remove(id)
        }
    }

    fun clearLogs() {
        executeJs("console.clear()")
        browserLogs.clear()
    }

    fun findLinkError(): String {
        browserLogs.forEach {
            if (it.contains(JavascriptHelper.ERR_RESPONSE_KEY)) {
                return it
            }
        }
        return ""
    }

    data class Link(
        val url: String,
        val quality: Quality
    )

    fun findLinks(): List<Link> {
        val links = mutableListOf<Link>()
        browserLogs.forEach {
            if (it.contains(JavascriptHelper.LINK_RESPONSE_KEY)) {
                val quality = it.substringAfterLast("|").substringBeforeLast("]")
                val videoUrl = it.substringAfterLast("]").replace("\"", "")
                links.add(Link(videoUrl, Quality.qualityForHtml(quality)))
            }
        }
        return links
    }
}