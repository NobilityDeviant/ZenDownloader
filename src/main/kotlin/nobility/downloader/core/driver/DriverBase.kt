package nobility.downloader.core.driver

import io.github.bonigarcia.wdm.WebDriverManager
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.Core
import nobility.downloader.core.driver.undetected_chrome.ChromeDriverBuilder
import nobility.downloader.core.settings.Defaults
import nobility.downloader.utils.FrogLog
import nobility.downloader.utils.UserAgents
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeOptions
import java.time.Duration

abstract class DriverBase(
    userAgent: String = ""
) {

    private val chromeOptions = ChromeOptions()
    private var nDriver: WebDriver? = null
    val driver get() = nDriver!!
    private var isSetup = false
    var userAgent = ""
    private val headless = Defaults.HEADLESS_MODE.boolean()


    init {
        this.userAgent = userAgent
        setupDriver()
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
        if (headless) {
            chromeOptions.addArguments("--headless=new")
            chromeOptions.addArguments("--window-position=-2400,-2400")
            //chromeOptions.addArguments("--headless=old")
        }
        chromeOptions.addArguments("--mute-audio")
        chromeOptions.addArguments("--user-agent=$userAgent")
        val chromePath = Defaults.CHROME_BROWSER_PATH.string()
        val chromeDriverPath = Defaults.CHROME_DRIVER_PATH.string()
        if (chromePath.isNotEmpty() && chromeDriverPath.isNotEmpty()) {
            FrogLog.logInfo(
                """
                    Using chrome browser from settings.
                    Chrome Browser Path: $chromePath
                    Chrome Driver Path: $chromeDriverPath
                """.trimIndent()
            )
            nDriver = ChromeDriverBuilder().build(
                options = chromeOptions,
                driverExecutablePath = chromeDriverPath,
                binaryLocation = chromePath
            )
        } else {
            WebDriverManager.chromedriver().setup()
            val binary = WebDriverManager.chromedriver().browserPath
            val exportedChromeDriver = System.getProperty("webdriver.chrome.driver")
            nDriver = if (binary.isPresent) {
                ChromeDriverBuilder().build(
                    options = chromeOptions,
                    driverExecutablePath = exportedChromeDriver,
                    binaryLocation = binary.get().toString(),
                )
            } else {
                //if not found with WDM, it will look for it.
                ChromeDriverBuilder().build(
                    options = chromeOptions,
                    driverExecutablePath = exportedChromeDriver,
                )
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
        Core.child.runningDrivers.add(driver)
        isSetup = true
    }

    fun executeJs(script: String) {
        if (driver !is JavascriptExecutor) {
            FrogLog.logError(
                "Failed to execute script: $script",
                "This browser doesn't support JavascriptExecutor."
            )
            return
        }
        val js = driver as JavascriptExecutor
        js.executeScript(script)
    }

    open fun killDriver() {
        if (nDriver != null) {
            Core.child.runningDrivers.remove(driver)
            driver.close()
            driver.quit()
        }
    }
}