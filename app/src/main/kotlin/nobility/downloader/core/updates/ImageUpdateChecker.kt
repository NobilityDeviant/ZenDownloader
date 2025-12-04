package nobility.downloader.core.updates

import Resource
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nobility.downloader.core.BoxHelper
import org.jsoup.Jsoup
import java.io.File
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.*

object ImageUpdateChecker {

    private const val COMMIT_URL = "https://api.github.com/repos/NobilityDeviant/ZenDownloaderSeriesImages/commits?page=1&per_page=1"

    suspend fun check(): Resource<Boolean> = withContext(Dispatchers.IO) {
        val imageFolder = File(BoxHelper.seriesImagesPath)
        if (!imageFolder.exists()) {
            return@withContext Resource.Success(true)
        }
        if (imageFolder.listFiles().isEmpty()) {
            return@withContext Resource.Success(true)
        }
        val imageFolderLastModified = imageFolder.lastModified()

        val api = Jsoup.connect(COMMIT_URL)
            .ignoreContentType(true)
            .get()
        val reader = JsonReader(StringReader(api.body().html()))
        reader.strictness = Strictness.LENIENT
        val jsonArray = JsonParser.parseReader(reader).asJsonArray
        val first = jsonArray.get(0).asJsonObject
        val commitDate = first.get("commit").asJsonObject
            .get("committer").asJsonObject
            .get("date").asString
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
        formatter.timeZone = TimeZone.getTimeZone("GMT")
        val formattedDate = formatter.parse(commitDate)
        val onlineLastModified = formattedDate.time
        return@withContext Resource.Success(
            onlineLastModified >= imageFolderLastModified
        )
    }
}