package nobility.downloader.utils.user_agents

import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.BoxHelper.Companion.long
import nobility.downloader.core.BoxHelper.Companion.update
import nobility.downloader.core.settings.Defaults
import nobility.downloader.ui.windows.AssetUpdateWindow
import nobility.downloader.utils.Constants
import nobility.downloader.utils.FrogLog
import nobility.downloader.utils.Tools
import java.io.File
import java.util.*

class UserAgents {

    companion object {

        private val shared = UserAgents()

        suspend fun init() {
            shared.loadAssetList()
            shared.downloadLatest()
        }

        val random get() = if (shared.list.isNotEmpty())
            shared.list.random()
        else
            shared.backupList.random()
    }

    private val list = mutableListOf<String>()

    suspend fun downloadLatest() {

        if (!Defaults.AUTOMATIC_USER_AGENT_DOWNLOAD.boolean()) {
            return
        }

        val autoUserAgentsFile = File(
            AssetUpdateWindow.autoUserAgentsPath
        )

        val lastUpdated = Defaults.USER_AGENTS_LAST_UPDATED.long()

        if (lastUpdated > 0) {
            val lastUpdatedCal = Calendar.getInstance()
            lastUpdatedCal.time = Date(lastUpdated)
            val currentCal = Calendar.getInstance()
            val lastUpdatedDay = lastUpdatedCal.get(Calendar.DAY_OF_YEAR)
            val currentDay = currentCal.get(Calendar.DAY_OF_YEAR)
            val difference = currentDay - lastUpdatedDay
            if (difference >= 0 && difference <= Constants.daysToUpdateUserAgents) {
                FrogLog.info(
                    "Skipping useragents update. They have been updated recently. Last Checked Day: $lastUpdatedDay Current Day: $currentDay Difference: $difference (Max Days: ${Constants.daysToUpdateUserAgents})"
                )
                if (autoUserAgentsFile.exists()) {
                    list.addAll(autoUserAgentsFile.readLines())
                }
                return
            } else {
                FrogLog.info(
                    "Useragents need to be updated. Last Checked Day: $lastUpdatedDay Current Day: $currentDay Difference: $difference (Max Days: ${Constants.daysToUpdateUserAgents})"
                )
            }
        }

        if (!autoUserAgentsFile.exists()) {
            autoUserAgentsFile.createNewFile()
        }

        val chromeVer = BrowserVersionFetcher.chrome()
        val firefoxVer = BrowserVersionFetcher.firefox()
        val operaVer = BrowserVersionFetcher.opera()
        val vivaldiVer = BrowserVersionFetcher.vivaldi()

        val output = mutableListOf<String>()

        for (os in UserAgentOS.entries) {
            output += UserAgentBuilder.chrome(chromeVer, os)
            output += UserAgentBuilder.opera(chromeVer, operaVer, os)
            output += UserAgentBuilder.vivaldi(chromeVer, vivaldiVer, os)
            output += UserAgentBuilder.firefox(firefoxVer, os)
        }

        if (autoUserAgentsFile.exists()) {
            autoUserAgentsFile.writeText(output.joinToString("\n"))
        }

        list.addAll(output)

        FrogLog.debug("Successfully updated the useragents.")

        Defaults.USER_AGENTS_LAST_UPDATED.update(Tools.currentTime)
    }

    private fun loadAssetList() {
        val userAgentFile = File(
            AssetUpdateWindow.userAgentsPath
        )
        if (userAgentFile.exists()) {
            list.addAll(userAgentFile.readLines())
        }
    }

    private val backupList: List<String> get() = """
        Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.7499.40 Safari/537.36
        Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.7499.40 Safari/537.36 OPR/124.0.5705.42
        Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.7499.40 Safari/537.36 Vivaldi/7.7.3851.52
        Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:145.0.1) Gecko/20100101 Firefox/145.0.1
        Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.7499.40 Safari/537.36
        Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.7499.40 Safari/537.36 OPR/124.0.5705.42
        Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.7499.40 Safari/537.36 Vivaldi/7.7.3851.52
        Mozilla/5.0 (X11; Linux x86_64; rv:145.0.1) Gecko/20100101 Firefox/145.0.1
        Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.7499.40 Safari/537.36
        Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.7499.40 Safari/537.36 OPR/124.0.5705.42
        Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.7499.40 Safari/537.36 Vivaldi/7.7.3851.52
        Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7; rv:145.0.1) Gecko/20100101 Firefox/145.0.1
        Mozilla/5.0 (Macintosh; arm64; Mac OS X 14_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.7499.40 Safari/537.36
        Mozilla/5.0 (Macintosh; arm64; Mac OS X 14_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.7499.40 Safari/537.36 OPR/124.0.5705.42
        Mozilla/5.0 (Macintosh; arm64; Mac OS X 14_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.7499.40 Safari/537.36 Vivaldi/7.7.3851.52
        Mozilla/5.0 (Macintosh; arm64; Mac OS X 14_0; rv:145.0.1) Gecko/20100101 Firefox/145.0.1
    """.trimIndent().split("\n")
}