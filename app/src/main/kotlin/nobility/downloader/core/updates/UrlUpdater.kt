package nobility.downloader.core.updates

import AppInfo
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.javanet.NetHttpTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.BoxHelper.Companion.long
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.BoxHelper.Companion.update
import nobility.downloader.core.Core
import nobility.downloader.core.settings.Defaults
import nobility.downloader.utils.Constants
import nobility.downloader.utils.FrogLog
import nobility.downloader.utils.Tools
import nobility.downloader.utils.domainFromLink
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URI
import java.util.*

object UrlUpdater {

    /**
     * New and improved to actually follow redirects properly!
     * This is used to update the wcofun url in the settings.
     * First we iterate through all the urls found inside the repository
     * and if the url changed, then we update the domain name and extension.
     * I have decided to use an external (and easily updatable) text file just in case
     * the website changes drastically.
     */
    suspend fun updateWcoUrl() = withContext(Dispatchers.IO) {
        if (Defaults.DISABLE_WCO_URLS_UPDATE.boolean()) {
            return@withContext
        }
        val lastUpdated = Defaults.WCO_LAST_UPDATED.long()
        if (lastUpdated > 0) {
            val lastUpdatedCal = Calendar.getInstance()
            lastUpdatedCal.time = Date(lastUpdated)
            val currentCal = Calendar.getInstance()
            val lastUpdatedDay = lastUpdatedCal.get(Calendar.DAY_OF_YEAR)
            val currentDay = currentCal.get(Calendar.DAY_OF_YEAR)
            val difference = currentDay - lastUpdatedDay
            if (difference >= 0 && difference <= Constants.daysToUpdateWcoUrl) {
                FrogLog.info(
                    "Skipping wco url update. It has been updated recently. Last Checked Day: $lastUpdatedDay Current Day: $currentDay Difference: $difference (Max Days: ${Constants.daysToUpdateWcoUrl})"
                )
                return@withContext
            } else {
                FrogLog.info(
                    "Wco url needs to be updated. Last Checked Day: $lastUpdatedDay Current Day: $currentDay Difference: $difference (Max Days: ${Constants.daysToUpdateWcoUrl})"
                )
            }
        }
        val urls = mutableListOf<String>()
        try {
            BufferedReader(
                InputStreamReader(URI(AppInfo.WCOFUN_WEBSITE_URLS_LINK).toURL().openStream())
            ).use { input ->
                while (true) {
                    val line = input.readLine()
                    if (!line.isNullOrEmpty()) {
                        urls.add(line)
                    } else {
                        break
                    }
                }
            }
        } catch (_: IOException) {
            urls.addAll(
                listOf(
                    "https://wcofun.org",
                    "https://wcofun.com",
                    "https://wcofun.net",
                    "https://www.wcoflix.tv"
                )
            )
        }
        for (u in urls) {
            FrogLog.debug(
                "Checking for changed url with: $u"
            )
            try {
                val requestFactory = NetHttpTransport().createRequestFactory()
                val request = requestFactory
                    .buildGetRequest(
                        GenericUrl(u)
                    ).setFollowRedirects(true)
                    .setConnectTimeout(30_000)
                    .setReadTimeout(30_000)
                    .setNumberOfRetries(3)
                    .setUseRawRedirectUrls(true)
                val response = request.execute()
                val statusCode = response.statusCode
                val newUrl = request.url.toString()
                response.disconnect()
                if (statusCode == 200) {
                    FrogLog.debug(
                        "Received status code 200 for $u"
                    )
                    val domainWithoutExtension = newUrl.domainFromLink()
                    val extension = Tools.extractExtensionFromLink(newUrl)
                    FrogLog.debug(
                        "Found Domain: $domainWithoutExtension Extension: $extension"
                    )
                    var updated = false
                    if (Defaults.WCO_EXTENSION.string() != extension) {
                        Defaults.WCO_EXTENSION.update(extension)
                        updated = true
                    }
                    if (Defaults.WCO_DOMAIN.string() != domainWithoutExtension) {
                        Defaults.WCO_DOMAIN.update(domainWithoutExtension)
                        updated = true
                    }
                    if (updated) {
                        Core.currentUrlHint = Core.exampleSeries
                        FrogLog.message("Successfully updated main url to: $newUrl")
                    }
                    Defaults.WCO_LAST_UPDATED.update(Tools.currentTime)
                    break
                } else {
                    FrogLog.debug(
                        "Failed with status code: $statusCode for url: $u" +
                                "\nSkipping..."
                    )
                }
            } catch (e: Throwable) {
                FrogLog.error(
                    "Failed to check for updated website url with: $u",
                    e
                )
            }
        }
    }
}