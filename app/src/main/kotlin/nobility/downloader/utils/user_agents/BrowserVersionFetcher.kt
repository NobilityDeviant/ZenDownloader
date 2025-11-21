package nobility.downloader.utils.user_agents

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.settings.Defaults
import nobility.downloader.utils.FrogLog
import java.net.HttpURLConnection
import java.net.URI

object BrowserVersionFetcher {

    private suspend fun readJson(
        url: String,
        timeout: Int = Defaults.TIMEOUT.int() * 1000
    ): String = withContext(Dispatchers.IO) {
        try {
            val con = URI(url).toURL().openConnection() as HttpURLConnection
            con.connectTimeout = timeout
            con.readTimeout = timeout
            con.requestMethod = "GET"
            return@withContext con.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            FrogLog.error(
                "Failed to find browser version from: $url",
                e.localizedMessage
            )
            return@withContext ""
        }
    }

    suspend fun chrome(): String {
        val json = readJson("https://versionhistory.googleapis.com/v1/chrome/platforms/win/channels/stable/versions")
        val regex = """"version"\s*:\s*"([0-9.]+)"""".toRegex()
        return regex.find(json)?.groupValues?.get(1) ?: "143.0.7499.40"
    }

    suspend fun firefox(): String {
        val json = readJson("https://product-details.mozilla.org/1.0/firefox_versions.json")
        val regex = """"LATEST_FIREFOX_VERSION"\s*:\s*"([0-9.]+)"""".toRegex()
        return regex.find(json)?.groupValues?.get(1) ?: "145.0.1"
    }

    suspend fun opera(): String {
        val json = readJson("https://formulae.brew.sh/api/cask/opera.json")
        val regex = """"version"\s*:\s*"([0-9.]+)"""".toRegex()
        return regex.find(json)?.groupValues?.get(1) ?: "124.0.5705.42"
    }

    suspend fun vivaldi(): String {
        val json = readJson("https://formulae.brew.sh/api/cask/vivaldi.json")
        val regex = """"version"\s*:\s*"([0-9.]+)"""".toRegex()
        return regex.find(json)?.groupValues?.get(1) ?: "7.7.3851.52"
    }
}
