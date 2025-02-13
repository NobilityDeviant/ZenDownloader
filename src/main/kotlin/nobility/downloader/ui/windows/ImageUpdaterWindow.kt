package nobility.downloader.ui.windows

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.google.common.collect.Lists
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.settings.Defaults
import nobility.downloader.ui.components.FullBox
import nobility.downloader.ui.components.defaultButton
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.Constants.bottomBarHeight
import nobility.downloader.utils.Resource
import nobility.downloader.utils.Tools
import java.io.BufferedReader
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.HttpsURLConnection

class ImageUpdaterWindow() {

    private val seriesImageUrlTreeUrl =
        "https://api.github.com/repos/NobilityDeviant/ZenDownloaderSeriesImages/git/trees/393d8ae0809bed6043eff177ec9ab6d125c6a506"
    private val seriesImageRawUrl =
        "https://raw.githubusercontent.com/NobilityDeviant/ZenDownloaderSeriesImages/master/series_images/"
    private val downloadScope = CoroutineScope(Dispatchers.IO)
    private var appWindowScope: AppWindowScope? = null
    private var cancelled by mutableStateOf(false)
    private var updateButtonEnabled = mutableStateOf(true)
    private var cancelButtonEnabled = mutableStateOf(false)
    private var downloadProgress by mutableStateOf(0f)
    private var downloadProgressText by mutableStateOf("0/0")
    private var downloading by mutableStateOf(false)
    private var consoleText by mutableStateOf("")

    fun open() {
        ApplicationState.newWindow(
            "Series Image Updater",
            onClose = {
                if (downloading) {
                    DialogHelper.showConfirm(
                        title = "The series images are currently downloading.",
                        message = "Are you sure you want to exit and stop the process?",
                        onConfirmTitle = "Exit",
                        onDenyTitle = "Stay",
                        size = DpSize(300.dp, 250.dp)
                    ) {
                        cancelDownload()
                        close()
                    }
                    false
                } else {
                    cancelDownload()
                    if (downloadScope.isActive) {
                        downloadScope.cancel()
                    }
                    true
                }
            },
            size = DpSize(450.dp, 400.dp),
            resizable = false
        ) {
            appWindowScope = this
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    Column(
                        modifier = Modifier.fillMaxWidth().height(bottomBarHeight)
                    ) {
                        HorizontalDivider()
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                                .padding(10.dp)
                        ) {
                            defaultButton(
                                "Update Images",
                                height = 40.dp,
                                width = 150.dp,
                                enabled = updateButtonEnabled
                            ) {
                                downloadScope.launch {
                                    downloadUpdate()
                                }
                            }
                            defaultButton(
                                "Cancel",
                                height = 40.dp,
                                width = 150.dp,
                                enabled = cancelButtonEnabled
                            ) {
                                cancelDownload()
                            }
                        }
                    }
                }
            ) { padding ->
                FullBox {
                    Column(
                        modifier = Modifier.padding(
                            bottom = padding.calculateBottomPadding()
                        ).fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                LinearProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                        .height(25.dp).padding(top = 10.dp),
                                    trackColor = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    downloadProgressText,
                                    textAlign = TextAlign.Center,
                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(5.dp)
                                )
                            }
                        }
                        HorizontalDivider()
                        val scrollState = rememberScrollState(0)
                        TextField(
                            value = consoleText,
                            readOnly = true,
                            onValueChange = {
                                consoleText = it
                            },
                            modifier = Modifier.fillMaxSize()
                                .verticalScroll(scrollState),
                            colors = TextFieldDefaults.colors(),
                            textStyle = MaterialTheme.typography.labelSmall
                        )
                        LaunchedEffect(consoleText.length) {
                            if (Defaults.AUTO_SCROLL_CONSOLES.boolean()) {
                                scrollState.animateScrollTo(scrollState.maxValue)
                            }
                        }
                    }
                }
                ApplicationState.addToastToWindow(this)
            }
        }
    }

    private suspend fun downloadUpdate() = withContext(Dispatchers.IO) {
        downloadProgressText = "0/0"
        downloadProgress = 0f
        val imagesPath = BoxHelper.seriesImagesPath
        val imagesFolder = File(imagesPath)
        if (!imagesFolder.exists() && !imagesFolder.mkdirs()) {
            DialogHelper.showError(
                """
                    Failed to find or create the series_images folder.
                    Please manually create the folder yourself before downloading.
                    Folder: ${imagesFolder.absolutePath}
                """.trimIndent()
            )
            Tools.openFile(
                imagesFolder.absolutePath,
                true,
                appWindowScope
            )
            return@withContext
        }
        cancelled = false
        cancelButtonEnabled.value = true
        updateButtonEnabled.value = false
        consoleText = ""
        message("Starting download process...")
        var finished = false
        var downloaded = AtomicInteger()
        var failed = AtomicInteger()
        var skipped = AtomicInteger()
        var totalImages = 0
        var updateJob: Job? = null
        val jobs = mutableListOf<Job>()

        val result = readUrlLines(seriesImageUrlTreeUrl)
        if (result.isFailed) {
            message("Failed to read json response. Error: ${result.message}")
            cancelDownload()
            return@withContext
        }
        try {
            val data = result.data!!
            val json = Json {
                isLenient = true
            }
            val jsonObj = json.parseToJsonElement(data.toString()).jsonObject
            val tree = jsonObj["tree"]?.jsonArray
            if (tree != null) {
                updateJob = launch {
                    while (isActive) {
                        val progress = downloaded.get() + failed.get() + skipped.get()
                        downloadProgressText = "$progress/$totalImages" +
                                "\nDownloaded: $downloaded" +
                                " | Failed: $failed" +
                                " | Skipped: $skipped"
                        downloadProgress = progress.toFloat() / totalImages.toFloat()
                        delay(500)
                    }
                }
                downloading = true
                downloadProgress = 0f
                totalImages = tree.size
                val threads = 15
                val lists = Lists.partition(tree, tree.size / threads)
                lists.forEach { list ->
                    jobs.add(launch {
                        for (obj in list) {
                            if (cancelled) {
                                break
                            }
                            val lineObj = obj.jsonObject
                            val path = lineObj["path"]?.jsonPrimitive?.content
                            val size = lineObj["size"]?.jsonPrimitive?.longOrNull
                            if (path != null && size != null) {
                                val imageFile = File(imagesPath.plus(path))
                                if (!imageFile.exists() || imageFile.length() < size) {
                                    try {
                                        Tools.downloadFile(
                                            (seriesImageRawUrl + URLEncoder.encode(path, Charsets.UTF_8))
                                                .replace("+", "%20"),
                                            imageFile
                                        )
                                        downloaded.andIncrement
                                    } catch (e: Exception) {
                                        failed.andIncrement
                                        message("Failed to download $path Error: ${e.localizedMessage}")
                                        e.printStackTrace()
                                    }
                                } else {
                                    skipped.andIncrement
                                }
                            } else {
                                failed.andDecrement
                                message("Skipped task for: $lineObj Path or Size is null.")
                            }
                        }
                    })
                }
                jobs.joinAll()
            }
        } catch (e: Exception) {
            message("Failed to download images. Error: ${e.localizedMessage}")
            cancelDownload()
            return@withContext
        } finally {
            updateJob?.cancel()
            jobs.forEach {
                it.cancel()
            }
        }
        downloading = false
        cancelButtonEnabled.value = false
        updateButtonEnabled.value = true
        finished = (downloaded.get() + failed.get() + skipped.get()) >= totalImages
        if (finished) {
            if (failed.get() == 0) {
                message("Finished downloading every image with no errors.")
                cancelButtonEnabled.value = false
                updateButtonEnabled.value = false
                downloadProgress = 1f
                downloadProgressText = "$totalImages/$totalImages"
            } else {
                message("Finished checking every image with some errors.")
                message("Downloaded: $downloaded | Failed: $failed | Skipped: $skipped")
                downloadProgress = 1f
                downloadProgressText = "$totalImages/$totalImages"
            }
        }
    }

    private fun cancelDownload() {
        downloading = false
        cancelled = true
        cancelButtonEnabled.value = false
        updateButtonEnabled.value = true
    }

    private fun close() {
        if (downloadScope.isActive) {
            downloadScope.cancel()
        }
        appWindowScope?.closeWindow()
    }

    private fun message(s: String) {
        if (consoleText.lines().size >= 500) {
            consoleText = ""
        }
        consoleText += "$s\n"
    }

    suspend fun readUrlLines(
        url: String
    ): Resource<StringBuilder> = withContext(Dispatchers.IO) {
        var con: HttpsURLConnection? = null
        var reader: BufferedReader? = null
        val sb = StringBuilder()
        try {
            con = URI(url).toURL().openConnection() as HttpsURLConnection
            con.addRequestProperty(
                "Accept",
                "*/*"
            )
            con.connectTimeout = Defaults.TIMEOUT.int() * 1000
            con.readTimeout = Defaults.TIMEOUT.int() * 1000
            reader = con.inputStream.bufferedReader()
            reader.readLines().forEach {
                sb.appendLine(it)
            }
            return@withContext Resource.Success(sb)
        } catch (e: Exception) {
            return@withContext Resource.Error(e)
        } finally {
            try {
                con?.disconnect()
                reader?.close()
            } catch (_: Exception) {
            }
        }
    }
}