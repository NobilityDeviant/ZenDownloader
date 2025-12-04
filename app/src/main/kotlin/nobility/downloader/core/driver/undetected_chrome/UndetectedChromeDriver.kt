package nobility.downloader.core.driver.undetected_chrome

import nobility.downloader.core.scraper.video_download.Functions
import nobility.downloader.core.settings.Defaults
import nobility.downloader.utils.FrogLog
import org.openqa.selenium.Capabilities
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class UndetectedChromeDriver(
    private val chromeOptions: ChromeOptions,
    private val headless: Boolean,
    private val keepUserDataDir: Boolean,
    private val userDataDir: String,
    private val browser: Process
) : ChromeDriver(chromeOptions) {

    fun go(
        url: String,
        catchError: Boolean = false
    ) {
        if (headless) {
            headless()
        }
        cdcProps()
        //stealth()
        if (catchError) {
            try {
                super.get(url)
            } catch (e: Exception) {
                FrogLog.error("Caught UCD get() Error: $url", e)
            }
        } else {
            super.get(url)
        }
    }

    fun blank() {
        go("https://blank.org")
    }

    override fun quit() {
        try {
            browser.destroy()
        } catch (_: Exception) {}
        try {
            super.quit()
        } catch (_: Exception) {}
        if (!keepUserDataDir) {
            val file = File(userDataDir)
            if (file.exists()) {
                file.deleteRecursively()
            }
        }
        if (Defaults.isUsingCustomChrome) {
            Functions.killChromeProcesses()
        }
    }

    /**
     * configure headless
     */
    private fun headless() {
        executeScript("return navigator.webdriver") ?: return
        val params1 = mutableMapOf<String, Any>()
        params1["source"] = """
                Object.defineProperty(window, 'navigator', {
                    value: new Proxy(navigator, {
                        has: (target, key) => (key === 'webdriver' ? false : key in target),
                        get: (target, key) =>
                            key === 'webdriver' ?
                            false :
                            typeof target[key] === 'function' ?
                            target[key].bind(target) :
                            target[key]
                        })
                });
                """.trimIndent()

        executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", params1)

        //set ua
        val params2 = mutableMapOf<String, Any>()
        params2["userAgent"] = (executeScript("return navigator.userAgent") as String).replace("Headless", "")
        executeCdpCommand("Network.setUserAgentOverride", params2)

        val params3 = mutableMapOf<String, Any>()
        params3["source"] = "Object.defineProperty(navigator, 'maxTouchPoints', {get: () => 1});"
        executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", params3)

        val params4 = mutableMapOf<String, Any>()
        params4["source"] = """
                Object.defineProperty(navigator.connection, 'rtt', {get: () => 100});
                // https://github.com/microlinkhq/browserless/blob/master/packages/goto/src/evasions/chrome-runtime.js
                window.chrome = {
                        app: {
                            isInstalled: false,
                            InstallState: {
                                DISABLED: 'disabled',
                                INSTALLED: 'installed',
                                NOT_INSTALLED: 'not_installed'
                            },
                            RunningState: {
                                CANNOT_RUN: 'cannot_run',
                                READY_TO_RUN: 'ready_to_run',
                                RUNNING: 'running'
                            }
                        },
                        runtime: {
                            OnInstalledReason: {
                                CHROME_UPDATE: 'chrome_update',
                                INSTALL: 'install',
                                SHARED_MODULE_UPDATE: 'shared_module_update',
                                UPDATE: 'update'
                            },
                            OnRestartRequiredReason: {
                                APP_UPDATE: 'app_update',
                                OS_UPDATE: 'os_update',
                                PERIODIC: 'periodic'
                            },
                            PlatformArch: {
                                ARM: 'arm',
                                ARM64: 'arm64',
                                MIPS: 'mips',
                                MIPS64: 'mips64',
                                X86_32: 'x86-32',
                                X86_64: 'x86-64'
                            },
                            PlatformNaclArch: {
                                ARM: 'arm',
                                MIPS: 'mips',
                                MIPS64: 'mips64',
                                X86_32: 'x86-32',
                                X86_64: 'x86-64'
                            },
                            PlatformOs: {
                                ANDROID: 'android',
                                CROS: 'cros',
                                LINUX: 'linux',
                                MAC: 'mac',
                                OPENBSD: 'openbsd',
                                WIN: 'win'
                            },
                            RequestUpdateCheckStatus: {
                                NO_UPDATE: 'no_update',
                                THROTTLED: 'throttled',
                                UPDATE_AVAILABLE: 'update_available'
                            }
                        }
                }

                // https://github.com/microlinkhq/browserless/blob/master/packages/goto/src/evasions/navigator-permissions.js
                if (!window.Notification) {
                        window.Notification = {
                            permission: 'denied'
                        }
                }

                const originalQuery = window.navigator.permissions.query
                window.navigator.permissions.__proto__.query = parameters =>
                        parameters.name === 'notifications'
                            ? Promise.resolve({ state: window.Notification.permission })
                            : originalQuery(parameters)
                        
                const oldCall = Function.prototype.call 
                function call() {
                        return oldCall.apply(this, arguments)
                }
                Function.prototype.call = call

                const nativeToStringFunctionString = Error.toString().replace(/Error/g, 'toString')
                const oldToString = Function.prototype.toString

                function functionToString() {
                        if (this === window.navigator.permissions.query) {
                            return 'function query() { [native code] }'
                        }
                        if (this === functionToString) {
                            return nativeToStringFunctionString
                        }
                        return oldCall.call(oldToString, this)
                }
                // eslint-disable-next-line
                Function.prototype.toString = functionToString
                """.trimIndent()
        executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", params4)
    }

    /**
     * remove cdc
     */
    @Suppress("UNCHECKED_CAST")
    private fun cdcProps() {
        val f = executeScript(
            """
                let objectToInspect = window, result = [];
                while(objectToInspect !== null) { 
                    result = result.concat(Object.getOwnPropertyNames(objectToInspect));
                    objectToInspect = Object.getPrototypeOf(objectToInspect); 
                }
                return result.filter(i => i.match(/.+_.+_(Array|Promise|Symbol)/ig))
            """.trimIndent()) as List<String>

        if (f.isNotEmpty()) {
            val param = mutableMapOf<String, Any>()
            param["source"] = """
                    let objectToInspect = window, result = [];
                    while(objectToInspect !== null) { 
                        result = result.concat(Object.getOwnPropertyNames(objectToInspect));
                        objectToInspect = Object.getPrototypeOf(objectToInspect); 
                    }
                    result.forEach(p => p.match(/.+_.+_(Array|Promise|Symbol)/ig)
                                        &&delete window[p]&&console.log('removed',p))
                    """.trimIndent()
            executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", param)
        }
    }

    @Suppress("UNUSED")
    private fun stealth() {
        val stringBuffer = StringBuilder()
        try {
            val stealthResource = this.javaClass.getResourceAsStream("/static/js/stealth.min.js")
            if (stealthResource != null) {
                val br = BufferedReader(InputStreamReader(stealthResource))
                br.use { reader ->
                    var str: String
                    while ((reader.readLine().also { str = it }) != null) {
                        stringBuffer.append(str)
                        stringBuffer.append("\n")
                    }
                    stealthResource.close()
                }
            } else {
                FrogLog.error("Failed to inject stealth script.", "stealth.min.js not found")
            }
        } catch (e: Exception) {
            FrogLog.error("Failed to inject stealth script.", e)
        }
        val params = mutableMapOf<String, Any>(
            "source" to stringBuffer.toString()
        )
        executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", params)
    }

    public override fun startSession(capabilities: Capabilities?) {
        var mCapabilities = capabilities
        if (mCapabilities == null) {
            mCapabilities = chromeOptions
        }
        super.startSession(mCapabilities)
    }
}



