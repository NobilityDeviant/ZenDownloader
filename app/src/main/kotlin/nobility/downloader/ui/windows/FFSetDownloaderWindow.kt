package nobility.downloader.ui.windows

import AppInfo
import Resource
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import nobility.downloader.ui.components.DefaultButton
import nobility.downloader.ui.components.FullBox
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.FrogLog
import nobility.downloader.utils.Tools
import nobility.downloader.utils.computer_info.Architecture
import nobility.downloader.utils.computer_info.ComputerInfo
import nobility.downloader.utils.computer_info.OperatingSystem
import nobility.downloader.utils.normalizeEnumName
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.net.ssl.HttpsURLConnection

class FFSetDownloaderWindow() {

    private val myPC = ComputerInfo.myComputer()
    private var ffSetFound by mutableStateOf(Tools.ffSetFound())
    private val downloadScope = CoroutineScope(Dispatchers.IO)
    private var appWindowScope: AppWindowScope? = null
    private var cancelled by mutableStateOf(false)
    private var updateButtonEnabled = mutableStateOf(!ffSetFound)
    private var cancelButtonEnabled = mutableStateOf(false)
    private var downloadProgress by mutableStateOf(0f)
    private var downloadMessage by mutableStateOf("")
    private var downloading by mutableStateOf(false)

    fun open() {
        ApplicationState.newWindow(
            "Download FFmpeg & FFplay",
            onClose = {
                if (downloading) {
                    DialogHelper.showConfirm(
                        title = "FFMpeg & FFPlay are currently downloading.",
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
            size = DpSize(500.dp, 550.dp)
        ) {
            appWindowScope = this
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .wrapContentHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            if (!ffSetFound) {
                                DefaultButton(
                                    "Download",
                                    height = 40.dp,
                                    width = 150.dp,
                                    enabled = updateButtonEnabled
                                ) {
                                    downloadScope.launch {
                                        downloadUpdate()
                                    }
                                }
                                DefaultButton(
                                    "Cancel",
                                    height = 40.dp,
                                    width = 150.dp,
                                    enabled = cancelButtonEnabled
                                ) {
                                    cancelDownload()
                                }
                            } else {
                                DefaultButton(
                                    "Close",
                                    Modifier.height(40.dp)
                                        .width(150.dp)
                                ) {
                                    close()
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                FullBox {
                    Column(
                        modifier = Modifier.padding(
                            bottom = padding.calculateBottomPadding())
                            .fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth()
                                .padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                LinearProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                        .height(25.dp).padding(top = 10.dp),
                                    trackColor = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    downloadMessage,
                                    textAlign = TextAlign.Center,
                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(5.dp)
                                )
                            }
                        }
                        Text(
                            """
                                FFmpeg is needed to download m3u8 videos.
                                FFplay is used for the "Watch Online" feature.
                                This window is used to download both of these at a single time.
                                It will automatically download them for your operating system.
                                
                                Operating System: ${myPC.operatingSystem.name.normalizeEnumName()}
                                Architecture: ${myPC.architecture.name.normalizeEnumName()}
                                
                                ${if (ffSetFound) "FFmpeg & FFplay Found. You can close this window." else ""}
                            """.trimIndent(),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(4.dp)
                                .verticalScroll(rememberScrollState())
                        )

                    }
                }
                ApplicationState.AddToastToWindow(this)
            }
        }
    }

    private suspend fun downloadUpdate() = withContext(Dispatchers.IO) {
        downloadMessage = "Downloading FFmpeg & FFplay..."
        downloadProgress = 0f
        cancelled = false
        cancelButtonEnabled.value = true
        updateButtonEnabled.value = false
        val result = downloadLatestFFmpeg(myPC)
        if (result.isFailed) {
            FrogLog.error(
                "Failed to download FFmpeg & FFplay.",
                result.message
            )
            downloadMessage = "Failed to download FFmpeg & FFplay. Check the console."
            cancelDownload()
            return@withContext
        } else {
            ffSetFound = true
            downloadMessage = "Finished downloading FFmpeg & FFplay."
            cancelButtonEnabled.value = false
            updateButtonEnabled.value = false
            downloadProgress = 1f
        }
        downloading = false
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
        appWindowScope?.closeWindow(false)
    }

    suspend fun downloadLatestFFmpeg(
        computerInfo: ComputerInfo = ComputerInfo.myComputer()
    ): Resource<Boolean> = withContext(Dispatchers.IO) {
        val link = fetchLinkForPC(computerInfo)
        if (link.isEmpty()) {
            return@withContext Resource.Error("This PC isn't supported.")
        }
        val downloadFile = File(AppInfo.databasePath, "ffmpeg.zip")
        val outputDirectoryFile = File(AppInfo.databasePath)
        if (ffSetFound) {
            FrogLog.info("FFmpeg & FFplay already found.")
            return@withContext Resource.Success(true)
        }
        if (downloadFile.exists()) {
            downloadFile.delete()
        }
        var con: HttpsURLConnection? = null
        try {
            con = Tools.openFollowingRedirects(link)
            val expectedSize = con.contentLengthLong
            con.inputStream.use { input ->
                BufferedOutputStream(FileOutputStream(downloadFile)).use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        total += bytesRead
                        output.write(buffer, 0, bytesRead)
                        downloadProgress = (100.0 * total / bytesRead).toFloat()
                    }
                }
            }

            val actualSize = downloadFile.length()
            if (actualSize != expectedSize) {
                return@withContext Resource.Error(
                    "Download incomplete or corrupted. Expected Length: $expectedSize Downloaded Length: $actualSize"
                )
            }
            downloadMessage = "Finished downloading ffmpeg zip. Extracting files..."
            Tools.unzipFile(
                downloadFile,
                outputDirectoryFile
            )
            downloadFile.delete()
            val files = outputDirectoryFile.listFiles()
            files?.forEach {
                if (it.name.contains("ffprobe", true)) {
                    it.delete()
                    return@forEach
                }
            }
        } catch (e: Exception) {
            return@withContext Resource.Error(e)
        } finally {
            try {
                con?.disconnect()
            } catch (_: Exception) {}
        }

        return@withContext Resource.Success(true)
    }

    private fun fetchLinkForPC(info: ComputerInfo): String {
        val base = "https://github.com/Tyrrrz/FFmpegBin/releases/download/7.1.1/ffmpeg-%s-%s.zip"
        if (info.architecture == Architecture.UNSUPPORTED
            || info.operatingSystem == OperatingSystem.UNSUPPORTED) {
            return ""
        }
        val osName = when (info.operatingSystem) {
            OperatingSystem.WINDOWS -> "windows"
            OperatingSystem.MAC -> "osx"
            OperatingSystem.LINUX -> "linux"
            else -> ""
        }

        val archName = when (info.architecture) {
            Architecture.AMD64 -> "x64"
            Architecture.ARM64 -> "arm64"
            else -> ""
        }
        return base.format(osName, archName)
    }
}