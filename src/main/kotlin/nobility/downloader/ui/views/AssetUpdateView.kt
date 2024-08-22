package nobility.downloader.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import nobility.downloader.ui.components.defaultButton
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.utils.Tools
import nobility.downloader.utils.UserAgents
import org.jsoup.Jsoup
import java.io.*
import java.net.URI
import java.text.SimpleDateFormat
import java.util.*
import javax.net.ssl.HttpsURLConnection
import kotlin.system.exitProcess


/**
 * An experimental asset update window.
 * Since we can't manage files in use, why not update them before?
 * Note that we can't access anything from the Core so it needs to be standalone.
 * Comes with a shutdown/fail feature to ensure incomplete downloads get handled.
 * All assets are very low in file size. We don't really need to worry about much besides available file space.
 */
class AssetUpdateView {

    private var downloadProgress by mutableStateOf(0f)
    private var downloadText by mutableStateOf("0/0")
    private var downloading by mutableStateOf(false)
    private val started = mutableMapOf<String, Boolean>()
    private val completed = mutableMapOf<String, Boolean>()
    private var shuttingDown by mutableStateOf(false)
    private var retry by mutableStateOf(false)

    private val finished get() = completed.entries.map { it.value }.size == Asset.entries.size

    @Composable
    fun assetUpdaterUi(
        scope: AppWindowScope
    ) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            val coroutineScope = rememberCoroutineScope()
            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.border(
                    2.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(5.dp)
                ).background(
                    Color(0xFF191C1B),
                    RoundedCornerShape(5.dp)
                ).align(Alignment.CenterVertically).padding(10.dp)
            ) {
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.fillMaxWidth(0.9f)
                        .height(25.dp).padding(top = 10.dp),
                    trackColor = MaterialTheme.colorScheme.background,
                )
                Text(
                    downloadText,
                    textAlign = TextAlign.Center,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    color = Color(0xFFE1E3E0),
                    modifier = Modifier.padding(5.dp)
                )
                if (retry) {
                    defaultButton(
                        "Retry",
                        width = 150.dp
                    ) {
                        retry = false
                        coroutineScope.launch {
                            downloading = true
                            Asset.entries.forEach { asset ->
                                downloadAsset(asset)
                            }
                            if (!shuttingDown) {
                                if (finished) {
                                    downloadText = "All downloads have been finished."
                                    scope.closeWindow()
                                } else {
                                    retry = true
                                    downloadText = "Failed to download all assets."
                                }
                            }
                            downloading = false
                        }
                    }
                } else if (!shuttingDown) {
                    defaultButton(
                        if (downloading) "Shutdown" else "Continue To App",
                        width = 150.dp
                    ) {
                        if (downloading) {
                            coroutineScope.launch(Dispatchers.Default) {
                                shuttingDown = true
                                delay(10_000)
                                Asset.entries.forEach { asset ->
                                    val started = started[asset.fileName]
                                    val completed = completed[asset.fileName]
                                    if (started != null && started && (completed == null || !completed)) {
                                        println("Detected incomplete download for $asset")
                                        val assetFile = File(asset.path)
                                        val downloadFile = File(
                                            assetFile.parent + File.separator + asset.fileName
                                        )
                                        if (downloadFile.exists()) {
                                            println("Deleting asset: $asset")
                                            downloadFile.delete()
                                        }
                                        if (assetFile.exists()) {
                                            println("Deleting asset folder: ${asset.path}")
                                            assetFile.deleteRecursively()
                                        }
                                    }
                                }
                                Tools.openFolder(
                                    dubbedPath,
                                    true
                                )
                                exitProcess(0)
                            }
                        } else {
                            scope.closeWindow()
                        }
                    }
                }
                val saveKey = rememberSaveable { true }
                LaunchedEffect(saveKey) {
                    downloading = true
                    Asset.entries.forEach { asset ->
                        downloadAsset(asset)
                    }
                    if (!shuttingDown) {
                        if (finished) {
                            downloadText = "All downloads have been finished."
                            scope.closeWindow()
                        } else {
                            retry = true
                            downloadText = "Failed to download all assets."
                        }
                    }
                    downloading = false
                }
            }
        }
    }

    private suspend fun downloadAsset(
        asset: Asset
    ) = withContext(Dispatchers.IO) {
        started[asset.fileName] = true
        if (shuttingDown) {
            downloadText = "Shutting Down..."
            return@withContext
        }
        downloadText = "Accessing $asset online"
        downloadProgress = 0f
        var con: HttpsURLConnection? = null
        var bis: BufferedInputStream? = null
        var fos: FileOutputStream? = null
        var bos: BufferedOutputStream? = null
        val assetFile = File(asset.path)
        assetFile.mkdirs()
        try {
            val api = Jsoup.connect(asset.apiLink).ignoreContentType(true).get()
            val reader = JsonReader(StringReader(api.body().html()))
            reader.isLenient = true
            val jsonArray = JsonParser.parseReader(reader).asJsonArray
            val first = jsonArray.get(0).asJsonObject
            val commitDate = first.get("commit").asJsonObject
                .get("committer").asJsonObject
                .get("date").asString
            val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
            formatter.timeZone = TimeZone.getTimeZone("GMT")
            val formattedDate = formatter.parse(commitDate)
            con = URI(asset.link)
                .toURL()
                .openConnection() as HttpsURLConnection
            con.addRequestProperty(
                "Accept",
                "*/*"
            )
            con.addRequestProperty("Accept-Encoding", "gzip, deflate, br, zstd")
            con.addRequestProperty("Accept-Language", "en-US,en;q=0.9")
            con.addRequestProperty("User-Agent", UserAgents.random)
            con.connectTimeout = 30_000
            con.readTimeout = 30_000
            val completeFileSize = con.contentLengthLong
            if (completeFileSize == -1L) {
                throw Exception("Failed to find file size for asset: $asset.")
            }
            val localLastModified = assetFile.lastModified()
            val onlineLastModified = formattedDate.time
            if (!assetFile.exists() || assetFile.listFiles()?.isEmpty() == true || onlineLastModified > localLastModified) {
                println("Detected new update for: $asset. Online Last Modified: $onlineLastModified Compared to Local Last Modified: $localLastModified")
                bis = BufferedInputStream(con.inputStream)
                val buffer = ByteArray(8192)
                val downloadFile = File(
                    assetFile.parent + File.separator + asset.fileName
                )
                fos = FileOutputStream(downloadFile, true)
                bos = BufferedOutputStream(fos, buffer.size)
                var count: Int
                var total = 0L
                while (bis.read(buffer).also { count = it } != -1) {
                    if (shuttingDown) {
                        downloadText = "Shutting Down..."
                        break
                    }
                    total += count.toLong()
                    bos.write(buffer, 0, count)
                    downloadText = "Downloading $asset: " + Tools.bytesToString(total) + "/" +
                            Tools.bytesToString(completeFileSize)
                    downloadProgress = ((total.toFloat() / completeFileSize.toFloat()))
                }
                try {
                    bos.flush()
                    bos.close()
                } catch (ignored: Exception) {
                    ignored.printStackTrace()
                }
                try {
                    fos.flush()
                    fos.close()
                } catch (ignored: Exception) {
                    ignored.printStackTrace()
                }
                val finished = total >= completeFileSize
                if (finished) {
                    downloadText = "Finished downloading $asset Unzipping file..."
                    println("Successfully downloaded: $asset Unzipping file...")
                    val zipFile = ZipFile(downloadFile)
                    zipFile.extractAll(File(asset.path).parent)
                    downloadFile.delete()
                    completed[asset.fileName] = true
                }
            } else {
                completed[asset.fileName] = true
                downloadProgress = 1f
                downloadText = "Asset $asset is already updated."
                println("Asset $asset is already updated. Online Last Modified: $onlineLastModified Compared to Local Last Modified: $localLastModified")
            }
        } catch (e: Exception) {
            System.err.println(
                "Failed to download asset: $asset " +
                        "Error: ${e.localizedMessage}"
            )
            e.printStackTrace()
        } finally {
            con?.disconnect()
            bis?.close()
            try {
                bos?.close()
            } catch (ignored: Exception) {
            }
            try {
                fos?.close()
            } catch (ignored: Exception) {
            }
        }
    }

    private enum class Asset(
        val apiLink: String,
        val link: String,
        val path: String,
        val fileName: String
    ) {
        DUBBED(
            "https://api.github.com/repos/NobilityDeviant/ZenDownloader/commits?path=database/dubbed.zip&page=1&per_page=1",
            "https://github.com/NobilityDeviant/ZenDownloader/raw/master/database/dubbed.zip",
            dubbedPath,
            "dubbed.zip"
        ),
        SUBBED(
            "https://api.github.com/repos/NobilityDeviant/ZenDownloader/commits?path=database/subbed.zip&page=1&per_page=1",
            "https://github.com/NobilityDeviant/ZenDownloader/raw/master/database/subbed.zip",
            subbedPath,
            "subbed.zip"
        ),
        MOVIES(
            "https://api.github.com/repos/NobilityDeviant/ZenDownloader/commits?path=database/movies.zip&page=1&per_page=1",
            "https://github.com/NobilityDeviant/ZenDownloader/raw/master/database/movies.zip",
            moviesPath,
            "movies.zip"
        ),
        CARTOON(
            "https://api.github.com/repos/NobilityDeviant/ZenDownloader/commits?path=database/cartoon.zip&page=1&per_page=1",
            "https://github.com/NobilityDeviant/ZenDownloader/raw/master/database/cartoon.zip",
            cartoonPath,
            "cartoon.zip"
        ),
        LINKS(
            "https://api.github.com/repos/NobilityDeviant/ZenDownloader/commits?path=database/links.zip&page=1&per_page=1",
            "https://github.com/NobilityDeviant/ZenDownloader/raw/master/database/links.zip",
            linksPath,
            "links.zip"
        )
    }

    companion object {
        private val databasePath = "${System.getProperty("user.home")}${File.separator}.zen_database${File.separator}"
        private val linksPath = databasePath + "wco" + File.separator + "links" + File.separator
        private val seriesPath = databasePath + "series" + File.separator
        private val dubbedPath = seriesPath + "dubbed" + File.separator
        private val subbedPath = seriesPath + "subbed" + File.separator
        private val cartoonPath = seriesPath + "cartoon" + File.separator
        private val moviesPath = seriesPath + "movies" + File.separator
    }
}