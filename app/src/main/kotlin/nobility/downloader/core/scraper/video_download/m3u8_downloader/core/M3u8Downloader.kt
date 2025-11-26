package nobility.downloader.core.scraper.video_download.m3u8_downloader.core

import kotlinx.coroutines.*
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.DecryptionKey
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.HttpRequestManager
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.config.HttpRequestConfig
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.config.HttpRequestManagerConfig
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.response.FileDownloadPostProcessor
import java.net.URI
import java.nio.ByteBuffer
import java.nio.file.Path

class M3u8Downloader(
    requestManagerConfig: HttpRequestManagerConfig
) {

    private val requestManager = HttpRequestManager(requestManagerConfig)
    private val progressScheduler = M3u8Progress()

    suspend fun run(
        m3u8Download: M3u8Download
    ) = withContext(Dispatchers.IO) {

        val tsDownloads = m3u8Download.resolveTsDownloads(
            bytesResponseGetter()
        )

        launch(Dispatchers.Default) {
            progressScheduler.addM3u8(
                m3u8Download
            )
            progressScheduler.run()
        }

        val allSucceeded: Boolean = downloadTs(tsDownloads)

        m3u8Download.markComplete(allSucceeded)
        m3u8Download.downloadListener?.downloadFinished?.invoke(
            m3u8Download,
            allSucceeded
        )

        if (allSucceeded) {
            m3u8Download.mergeIntoVideo()
        }
    }

    fun shutdown() {
        requestManager.shutdown()
    }

    private suspend fun downloadTs(
        tsDownloads: List<TsDownload>
    ): Boolean = withContext(Dispatchers.IO) {
        val results = tsDownloads.map { dl ->
            async {
                try {
                    downloadTs(dl)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }.awaitAll()

        results.all { it }
    }

    private fun convertKey(m3u8SecretKey: M3u8SecretKey?): DecryptionKey? {
        if (m3u8SecretKey == null || m3u8SecretKey == M3u8SecretKey.NONE
            || m3u8SecretKey.method == M3u8SecretKey.NONE.method) {
            return null
        }
        return DecryptionKey(
            m3u8SecretKey.key!!,
            m3u8SecretKey.method,
            m3u8SecretKey.initVector!!
        )
    }

    private suspend fun downloadTs(
        tsDownload: TsDownload
    ): Path {
        val uri = tsDownload.uri
        val filePath= tsDownload.filePath
        val m3u8Download: M3u8Download = tsDownload.m3u8Download
        val decryptionKey = convertKey(tsDownload.m3u8SecretKey)
        val m3u8DownloadOptions = m3u8Download.getM3u8DownloadOptions()
        val requestConfig = m3u8DownloadOptions.m3u8HttpRequestConfigStrategy.getConfig(
            M3u8HttpRequestType.REQ_FOR_TS,
            uri
        )
        val fileDownloadPostProcessor: FileDownloadPostProcessor = object : FileDownloadPostProcessor {
            override fun startDownload(contentLength: Long?, reStart: Boolean) {
                tsDownload.startRead(contentLength?: -1L, reStart)
            }

            override fun afterReadBytes(size: Int, end: Boolean) {
                tsDownload.readBytes(size)
            }

            override fun afterDownloadComplete() {
                tsDownload.complete()
            }

            override fun afterDownloadFailed() {
                tsDownload.failed()
            }
        }

        return requestManager.downloadFile(
            uri,
            filePath,
            decryptionKey,
            requestConfig,
            fileDownloadPostProcessor
        )
    }

    private fun bytesResponseGetter(): suspend (URI, HttpRequestConfig?) -> ByteBuffer {
        return { uri, cfg ->
            requestManager.getBytes(
                uri,
                cfg
            )
        }
    }
}







