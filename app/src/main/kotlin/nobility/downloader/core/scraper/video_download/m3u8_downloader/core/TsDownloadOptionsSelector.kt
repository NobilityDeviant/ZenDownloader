package nobility.downloader.core.scraper.video_download.m3u8_downloader.core

import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.response.FileDownloadOptions
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

interface TsDownloadOptionsSelector {
    fun getDownloadOptions(
        m3u8Download: M3u8Download,
        tsDownloads: List<TsDownload>
    ): FileDownloadOptions? {
        if (tsDownloads.isEmpty()) {
            return null
        }
        // spec: loop back addr
        val uri = tsDownloads[0].uri
        val inetSocketAddress = InetSocketAddress(uri.host, 80)
        if (inetSocketAddress.address.isLoopbackAddress) {
            log.info(
                "loopback addr, use false, identity={}, addr={}",
                m3u8Download.identity,
                inetSocketAddress.address
            )
            return FileDownloadOptions.getInstance(
                ifAsyncSink = false,
                useBufferPool = false
            )
        }
        return getDownloadOptionsInternal(m3u8Download, tsDownloads)
    }


    fun getDownloadOptionsInternal(
        m3u8Download: M3u8Download,
        tsDownloads: List<TsDownload>
    ): FileDownloadOptions?

    class PlainTsDownloadOptionsSelector(
        private val fileDownloadOptions: FileDownloadOptions
    ) : TsDownloadOptionsSelector {

        override fun getDownloadOptionsInternal(
            m3u8Download: M3u8Download,
            tsDownloads: List<TsDownload>
        ): FileDownloadOptions {
            return fileDownloadOptions
        }

        companion object {
            fun optionsSelector(
                ifAsyncSink: Boolean,
                useBufferPool: Boolean
            ): PlainTsDownloadOptionsSelector {
                return PlainTsDownloadOptionsSelector(
                    FileDownloadOptions.getInstance(ifAsyncSink, useBufferPool)
                )
            }
        }
    }


    companion object {
        val log: Logger = LoggerFactory.getLogger(TsDownloadOptionsSelector::class.java)
    }
}

