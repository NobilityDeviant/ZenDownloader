package nobility.downloader.ui.windows

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.BoxHelper.Companion.update
import nobility.downloader.core.Core
import nobility.downloader.core.driver.undetected_chrome.SysUtil
import nobility.downloader.core.settings.Defaults
import nobility.downloader.core.updates.Update
import nobility.downloader.ui.components.DefaultButton
import nobility.downloader.ui.components.FullBox
import nobility.downloader.ui.components.GithubText
import nobility.downloader.ui.components.defaultButton
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.ui.windows.utils.AppWindowScope
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.*
import nobility.downloader.utils.Constants.bottomBarHeight
import java.io.*
import java.net.URI
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class UpdateWindow(
    private val justCheck: Boolean = false
) {

    private var mUpdate: Update? = null
    private val update get() = mUpdate!!
    private val downloadScope = CoroutineScope(Dispatchers.IO)
    private var appWindowScope: AppWindowScope? = null
    private var cancelled by mutableStateOf(false)
    private var updateButtonEnabled = mutableStateOf(true)
    private var cancelButtonEnabled = mutableStateOf(false)
    private var downloadProgress by mutableStateOf(0f)
    private var downloadProgressText by mutableStateOf("0/0")


    init {
        downloadScope.launch {
            checkForUpdates()
        }
    }

    private fun open() {
        if (mUpdate == null) {
            return
        }
        ApplicationState.newWindow(
            if (update.isLatest) "Updated" else "Update Available",
            onClose = {
                if (Core.child.isUpdating) {
                    appWindowScope?.showToast(
                        "The update is currently downloading."
                    )
                    false
                } else {
                    if (downloadScope.isActive) {
                        downloadScope.cancel()
                    }
                    true
                }
            }
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
                            if (!cancelled) {
                                if (!Core.child.isUpdating && !Defaults.DENIED_UPDATE.boolean()) {
                                    defaultButton(
                                        "Deny Update",
                                        height = 40.dp,
                                        width = 150.dp,
                                        enabled = !update.isLatest
                                    ) {
                                        Defaults.DENIED_UPDATE.update(true)
                                        FrogLog.writeMessage(
                                            "The latest update has been denied. You will no longer receive a notification about it until the next update."
                                        )
                                        close()
                                    }
                                }
                                DefaultButton(
                                    "Update",
                                    height = 40.dp,
                                    width = 150.dp,
                                    enabled = updateButtonEnabled
                                ) {
                                    downloadUpdate()
                                }
                                if (Core.child.isUpdating) {
                                    DefaultButton(
                                        "Cancel",
                                        height = 40.dp,
                                        width = 150.dp,
                                        enabled = cancelButtonEnabled
                                    ) {
                                        DialogHelper.showConfirm(
                                            """
                                           The new update is currently being downloaded.
                                           Stopping the download now will delete the file which means you will have to restart the process.
                                           Would you like to cancel the update?
                                        """.trimIndent(),
                                            "Cancel Download",
                                            onConfirmTitle = "Cancel Download",
                                            onDenyTitle = "Keep Downloading"
                                        ) {
                                            cancel()
                                        }
                                    }
                                }
                            } else {
                                defaultButton(
                                    "Close Window",
                                    height = 40.dp,
                                    width = 150.dp,
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
                            bottom = padding.calculateBottomPadding()
                        ).fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            if (update.isLatest)
                                "You have the latest update."
                            else
                                "There's a new update available!",
                            textAlign = TextAlign.Center,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(4.dp)
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            GithubText(
                                text = """
                                      Current Version: ${AppInfo.VERSION}
                                      Latest Version: ${update.version}
                                      
                                      Update Download Link:
                                       
                                      ${update.downloadLink}
                                   """.trimIndent(),
                                style = MaterialTheme.typography.bodyMedium,
                                textColor = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                linkColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth().padding(4.dp)
                            )
                        }
                        Text(
                            "Update Log:",
                            textAlign = TextAlign.Center,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                            fontWeight = FontWeight.Bold
                        )
                        val scrollState = rememberScrollState()
                        val scope = rememberCoroutineScope()
                        Surface(
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            GithubText(
                                text = update.updateDescription,
                                style = MaterialTheme.typography.bodyMedium,
                                textColor = MaterialTheme.colorScheme.onSurface,
                                linkColor = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Justify,
                                modifier = Modifier.padding(10.dp).verticalScroll(scrollState)
                                    .draggable(
                                        state = rememberDraggableState {
                                            scope.launch {
                                                scrollState.scrollBy(-it)
                                            }
                                        },
                                        orientation = Orientation.Vertical,
                                    )
                            )
                        }
                        if (Core.child.isUpdating) {
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    LinearProgressIndicator(
                                        progress = { downloadProgress },
                                        modifier = Modifier.fillMaxWidth(0.9f)
                                            .height(25.dp).padding(top = 10.dp),
                                        trackColor = MaterialTheme.colorScheme.background,
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
                        }
                    }
                }
                ApplicationState.addToastToWindow(this)
            }
        }
    }

    private fun downloadUpdate() {
        Defaults.DENIED_UPDATE.update(false)
        cancelButtonEnabled.value = true
        updateButtonEnabled.value = false
        downloadScope.launch {
            val downloadedUpdate = File(
                Defaults.SAVE_FOLDER.value.toString()
                        + File.separator + update.downloadName
            )
            var con: HttpsURLConnection? = null
            var bis: BufferedInputStream? = null
            var fos: FileOutputStream? = null
            var bos: BufferedOutputStream? = null
            val finished: Boolean
            try {
                Core.child.isUpdating = true
                if (downloadedUpdate.exists()) {
                    downloadedUpdate.delete()
                }
                downloadProgress = 0f
                con = URI(update.downloadLink).toURL()
                    .openConnection() as HttpsURLConnection
                con.addRequestProperty(
                    "Accept",
                    update.downloadType
                )
                con.addRequestProperty("Accept-Encoding", "gzip, deflate, br")
                con.addRequestProperty("Accept-Language", "en-US,en;q=0.9")
                con.addRequestProperty("Connection", "keep-alive")
                con.addRequestProperty("User-Agent", UserAgents.random)
                con.connectTimeout = Defaults.TIMEOUT.int() * 1000
                con.readTimeout = Defaults.TIMEOUT.int() * 1000
                bis = BufferedInputStream(con.inputStream)
                val completeFileSize = con.contentLengthLong
                if (completeFileSize == -1L) {
                    throw Exception("Failed to find update file size.")
                }
                downloadProgressText = "0/" + Tools.bytesToString(completeFileSize)
                val buffer = ByteArray(8192)
                fos = FileOutputStream(downloadedUpdate, true)
                bos = BufferedOutputStream(fos, buffer.size)
                var count: Int
                var total = 0L
                while (bis.read(buffer).also { count = it } != -1) {
                    if (cancelled) {
                        break
                    }
                    total += count.toLong()
                    bos.write(buffer, 0, count)
                    downloadProgressText = Tools.bytesToString(total) + "/" +
                            Tools.bytesToString(completeFileSize)
                    downloadProgress = ((total.toFloat() / completeFileSize.toFloat()))
                }
                Core.child.isUpdating = false
                finished = total >= completeFileSize
                if (finished) {
                    FrogLog.logInfo(
                        "Update downloaded successfully! Size: " +
                                Tools.bytesToString(completeFileSize) +
                                " | Path: " + downloadedUpdate.absolutePath
                    )
                }
            } catch (e: Exception) {
                Core.child.isUpdating = false
                cancelled = true
                DialogHelper.showError(
                    """
                        Failed to download the client. The update link has been printed
                        to the console. Please manually download and update it yourself 
                        or try again through the home pages options menu (3 Dots).
                    """.trimIndent(),
                    e
                )
                println("Releases Link: ${AppInfo.RELEASES_LINK}")
                return@launch
            } finally {
                bos?.close()
                fos?.close()
                con?.disconnect()
                bis?.close()
            }
            if (finished) {
                DialogHelper.showConfirm(
                    message = """
                        Download Complete!
                        
                        The new update is ready to be installed.
                        It has been downloaded to:
                        
                        ${downloadedUpdate.absolutePath}
                         
                        Press the "Finish" button to shutdown the app and open the file or folder.
                        You can also press "Continue" to close this window and keep the app running.
                    """.trimIndent(),
                    "Download Complete",
                    onDeny = { close() },
                    onDenyTitle = "Continue",
                    onConfirmTitle = "Finish"
                ) {
                    if (downloadedUpdate.name.endsWith(OS.WINDOWS.extension)) {
                        Tools.openFile(
                            downloadedUpdate.absolutePath,
                            appWindowScope = appWindowScope
                        )
                    } else {
                        Tools.openFile(
                            downloadedUpdate.absolutePath,
                            true,
                            appWindowScope
                        )
                    }
                    Core.child.shutdown(true)
                }
            } else {
                if (!cancelled) {
                    DialogHelper.showError(
                        """
                        Failed to download the client. The update link has been printed
                        to the console. Please manually download and update it yourself 
                        or try again through the home pages options menu(3 Dots).
                    """.trimIndent()
                    )
                    println("Releases Link: ${AppInfo.RELEASES_LINK}")
                }
            }
        }
    }

    private fun cancel() {
        cancelled = true
        cancelButtonEnabled.value = false
        updateButtonEnabled.value = false
        val downloadedUpdate = File(
            Defaults.SAVE_FOLDER.string()
                    + File.separator + update.downloadName
        )
        if (downloadedUpdate.exists()) {
            downloadedUpdate.delete()
        }
    }

    private fun close() {
        if (downloadScope.isActive) {
            downloadScope.cancel()
        }
        appWindowScope?.closeWindow()
    }

    private suspend fun checkForUpdates() = withContext(Dispatchers.IO) {
        if (!justCheck) {
            FrogLog.writeMessage(
                "Checking for updates..."
            )
        }
        if (mUpdate == null) {
            val result = parseLatestRelease()
            if (result.data != null) {
                mUpdate = result.data
            } else {
                val message = result.message
                FrogLog.logError(
                    "Failed to check for updates. " +
                            if (!message.isNullOrEmpty()) message else ""
                )
                return@withContext
            }
        }
        if (!Defaults.UPDATE_VERSION.string().equals(
                update.version, ignoreCase = true
            )
        ) {
            Defaults.DENIED_UPDATE.update(false)
        }
        Defaults.UPDATE_VERSION.update(update.version)
        val latest = isLatest(update.version)
        if (justCheck && Defaults.DENIED_UPDATE.boolean()) {
            close()
            return@withContext
        }
        if (justCheck && latest) {
            close()
            return@withContext
        }
        update.isLatest = latest
        updateButtonEnabled.value = !latest
        withContext(Dispatchers.Main) {
            open()
        }
    }

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
            FrogLog.logError(
                "Failed to fetch update details.",
                e,
                true
            )
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
                        if (
                            o.has("browser_download_url")
                            && o.has("content_type")
                            && o.has("name")
                        ) {
                            val url = o["browser_download_url"].asString
                            val type = o["content_type"].asString
                            val name = o["name"].asString
                            if (OS.downloadMatchesMyOs(url)) {
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
            return Resource.Error(result.message)
        }
        return Resource.Error("Failed to find release for your operating system.")
    }


    private fun isLatest(latest: String?): Boolean {
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
        } catch (_: Exception) {
        }
    }

    private enum class OS(
        val extension: String
    ) {
        WINDOWS(".exe"),
        MAC(".dmg"),
        DEBIAN(".deb"),
        ARCH(".rpm");

        companion object {

            val myOs: OS get() {
                return if (SysUtil.isArch)
                    ARCH
                else if (SysUtil.isDebian)
                    DEBIAN
                else if (SysUtil.isMacOs)
                    MAC
                else
                    WINDOWS
            }

            fun downloadMatchesMyOs(url: String): Boolean {
                val os = myOs
                return url.lowercase(Locale.getDefault()).endsWith(os.extension)
            }
        }
    }
}