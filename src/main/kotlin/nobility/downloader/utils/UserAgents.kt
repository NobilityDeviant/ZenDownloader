package nobility.downloader.utils

import nobility.downloader.ui.windows.AssetUpdateWindow
import java.io.File

class UserAgents {

    companion object {

        private val shared = UserAgents()

        init {
            shared.loadAssetList()
        }

        val random get() = if (shared.list.isNotEmpty())
            shared.list.random()
        else
            shared.backupList.random()

    }

    private fun loadAssetList() {
        val userAgentFile = File(AssetUpdateWindow.userAgentsPath)
        if (userAgentFile.exists()) {
            list.addAll(userAgentFile.readLines())
        }
    }

    private val list = mutableListOf<String>()

    /**
     * A backup list in case the user_agents.txt file isn't present.
     * Scraped from https://www.useragents.me/
     * Old and mobile user agents should never be used.
     * Cloudflare blocks old ones and mobile ones can change the website resulting
     * in potentially important components being gone or named differently.
     */
    private val backupList get() =
        """
            Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 OPR/112.0.0.0
            Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.
        """.trimIndent().split("\n").toList().filter { it.isNotEmpty() }

}