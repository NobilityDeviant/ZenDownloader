
import AppInfo.GITHUB_LATEST
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import javax.net.ssl.HttpsURLConnection

object Updater {

    private fun fetchLatestRelease(): Resource<JsonObject> {
        try {
            val urlConnection = URI(GITHUB_LATEST).toURL()
                .openConnection() as HttpsURLConnection
            urlConnection.readTimeout = 30000
            urlConnection.connectTimeout = 30000
            urlConnection.instanceFollowRedirects = true
            urlConnection.connect()
            val input = BufferedReader(InputStreamReader(urlConnection.inputStream))
            val stringBuilder = StringBuilder()
            var inputLine: String?
            while (input.readLine().also { inputLine = it } != null) {
                stringBuilder.append(inputLine).append("\n")
            }
            input.close()
            urlConnection.disconnect()
            return Resource.Success(
                JsonParser.parseString(stringBuilder.toString()).asJsonObject
            )
        } catch (e: Exception) {
            return Resource.Error(e)
        }
    }

    fun parseLatestRelease(): Resource<Update> {
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
                        if (
                            o.has("browser_download_url")
                            && o.has("content_type")
                            && o.has("name")
                        ) {
                            val url = o["browser_download_url"].asString
                            val type = o["content_type"].asString
                            val name = o["name"].asString
                            if (url.endsWith(".jar")) {
                                return Resource.Success(
                                    Update(
                                        version,
                                        downloadLink = url,
                                        downloadName = name,
                                        downloadType = type,
                                        updateDescription = body
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } else if (result.message != null) {
            return Resource.Error(
                "Failed to find release data.",
                result.message
            )
        }
        return Resource.Error("Failed to find release data. No error has been found.")
    }

    fun isLatest(latest: String?): Boolean {
        if (latest == null) {
            return true
        }
        val currentParts = AppInfo.VERSION.split(".").map { it.toIntOrNull() ?: 0 }
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLength = maxOf(currentParts.size, latestParts.size)
        val paddedCurrent = currentParts + List(maxLength - currentParts.size) { 0 }
        val paddedLatest = latestParts + List(maxLength - latestParts.size) { 0 }

        for (i in 0 until maxLength) {
            if (paddedLatest[i] > paddedCurrent[i]) {
                return false
            }
            if (paddedLatest[i] < paddedCurrent[i]) {
                return true
            }
        }

        return true
    }

}