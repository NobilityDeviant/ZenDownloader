package nobility.downloader.core.updates

import com.google.api.client.http.GenericUrl
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.BoxHelper.Companion.long
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.BoxHelper.Companion.update
import nobility.downloader.core.Core
import nobility.downloader.core.settings.Defaults
import nobility.downloader.utils.*
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URI
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class UpdateManager {

    var latestUpdate: Update? = null

    private fun fetchLatestRelease(): Resource<JsonObject> {
        try {
            removeValidation()
            val urlConnection = URI(AppInfo.GITHUB_LATEST).toURL()
                .openConnection() as HttpsURLConnection
            urlConnection.readTimeout = 20000
            urlConnection.connectTimeout = 20000
            urlConnection.instanceFollowRedirects = true
            urlConnection.setRequestProperty("User-Agent", UserAgents.random)
            urlConnection.connect()
            val `in` = BufferedReader(InputStreamReader(urlConnection.inputStream))
            val stringBuilder = StringBuilder()
            var inputLine: String?
            while (`in`.readLine().also { inputLine = it } != null) {
                stringBuilder.append(inputLine).append("\n")
            }
            `in`.close()
            urlConnection.disconnect()
            return Resource.Success(
                JsonParser.parseString(stringBuilder.toString()).asJsonObject
            )
        } catch (e: Exception) {
            return Resource.Error(e)
        }
    }

    private fun parseLatestRelease(): Resource<Update> {
        val result = fetchLatestRelease()
        if (result.data != null) {
            val json = result.data
            val version = json["tag_name"].asString
            val body = json["body"].asString
            val array = json.getAsJsonArray("assets")
            if (array != null) {
                for (element in array) {
                    if (element.isJsonObject) {
                        val o = element.asJsonObject
                        if (o.has("browser_download_url")) {
                            val url = o["browser_download_url"].asString
                            if (url.endsWith(".jar")) {
                                return Resource.Success(Update(version, url, body))
                            }
                        }
                    }
                }
            }
        } else if (result.message != null) {
            return Resource.Error(result.message)
        }
        return Resource.Error("Failed to parse github api response.")
    }

    suspend fun checkForUpdates(
        prompt: Boolean,
        refresh: Boolean
    ) = withContext(Dispatchers.IO) {
        val latestUpdate = latestUpdate
        if (latestUpdate == null || refresh) {
            val result = parseLatestRelease()
            if (result.data != null) {
                this@UpdateManager.latestUpdate = result.data
            } else {
                FrogLog.writeMessage(
                    result.message?: "Failed to check for updates."
                )
            }
        }
        if (latestUpdate == null) {
            FrogLog.writeMessage("Failed to find latest update details. No error found.")
            return@withContext
        }
        if (!Defaults.UPDATE_VERSION.string().equals(
                latestUpdate.version, ignoreCase = true)
            ) {
            Defaults.DENIED_UPDATE.update(false)
        }
        if (!Defaults.DENIED_UPDATE.boolean()) {
            Defaults.UPDATE_VERSION.update(latestUpdate.version)
            val latest = isLatest(latestUpdate.version)
            if (latest && !prompt) {
                return@withContext
            }
            withContext(Dispatchers.Main) {
                //Core.showUpdateConfirm(
                  //  (if (latest) "Updated" else "Update Available") + " - ver. "
                    //        + latestUpdate.version, false, latest
                //)
            }
        }
    }

    private fun isLatest(latest: String?): Boolean {
        if (latest == null || latest == AppInfo.VERSION) {
            return true
        }
        try {
            val latestSplit = latest.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val current = AppInfo.VERSION.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (latestSplit[0].toInt() > current[0].toInt()) {
                return false
            }
            if (latestSplit.size > 2) {
                if (latestSplit[1].toInt() > current[1].toInt()) {
                    return false
                }
            }
            if (latestSplit.size > 3) {
                if (latestSplit[2].toInt() > current[2].toInt()) {
                    return false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return true
        }
        return false
    }

    private fun removeValidation() {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(arg0: Array<X509Certificate>, arg1: String) {}
            override fun checkServerTrusted(arg0: Array<X509Certificate>, arg1: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return emptyArray()
            }
        })
        try {
            val sc = SSLContext.getInstance("TLS")
            sc.init(null, trustAllCerts, SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
        } catch (ignored: Exception) {
        }
    }

    /**
     * New and improved to actually follow redirects properly!
     * This is used to update the wcofun url in the settings.
     * First we iterate through all the urls found inside the repository
     * and if the url changed, then we update the domain name and extension.
     * I have decided to use an external (and easily updatable) text file just in case
     * the website changes drastically.
     */
    suspend fun updateWcoUrl() = withContext(Dispatchers.IO) {
        val lastUpdated = Defaults.WCO_LAST_UPDATED.long()
        if (lastUpdated > 0) {
            val lastUpdatedCal = Calendar.getInstance()
            lastUpdatedCal.time = Date(lastUpdated)
            val currentCal = Calendar.getInstance()
            val lastUpdatedDay = lastUpdatedCal.get(Calendar.DAY_OF_YEAR)
            val currentDay = currentCal.get(Calendar.DAY_OF_YEAR)
            val difference = currentDay - lastUpdatedDay
            if (difference <= Constants.daysToUpdateWcoUrl) {
                FrogLog.logInfo(
                    "Skipping wco url update. It has been updated recently. Last Checked Day: $lastUpdatedDay Current Day: $currentDay Difference: $difference (Max Days: ${Constants.daysToUpdateWcoUrl})"
                )
                return@withContext
            } else {
                FrogLog.logInfo(
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
        } catch (e: IOException) {
            urls.addAll(
                listOf(
                    "https://wcofun.org",
                    "https://wcofun.com",
                    "https://wcofun.net"
                )
            )
        }
        for (u in urls) {
            FrogLog.logDebug(
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
                    FrogLog.logDebug(
                        "Received status code 200 for $u"
                    )
                    val domainWithoutExtension = Tools.extractDomainFromLink(newUrl)
                    val extension = Tools.extractExtensionFromLink(newUrl)
                    FrogLog.logDebug(
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
                        FrogLog.writeMessage("Successfully updated main url to: $newUrl")
                    }
                    Defaults.WCO_LAST_UPDATED.update(Date().time)
                    break
                } else {
                    FrogLog.logDebug(
                        "Failed with status code: $statusCode for url: $u" +
                                "\nSkipping..."
                    )
                }
            } catch (e: Throwable) {
                FrogLog.logError(
                    "Failed to check for updated website url with: $u",
                    e
                )
            }
        }
    }

}