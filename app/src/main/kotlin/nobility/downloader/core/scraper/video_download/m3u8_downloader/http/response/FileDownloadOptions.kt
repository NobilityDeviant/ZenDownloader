package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.response

class FileDownloadOptions(
    private val ifAsyncSink: Boolean,
    private val useBufferPool: Boolean
) {
    fun ifAsyncSink(): Boolean {
        return ifAsyncSink
    }

    fun useBufferPool(): Boolean {
        return useBufferPool
    }

    override fun toString(): String {
        return "FileDownloadOptions{" +
                "ifAsyncSink=" + ifAsyncSink +
                ", useBufferPool=" + useBufferPool +
                '}'
    }

    companion object {
        fun getInstance(ifAsyncSink: Boolean, useBufferPool: Boolean): FileDownloadOptions {
            return FileDownloadOptions(ifAsyncSink, useBufferPool)
        }

        fun defaultOptionsIfNull(options: FileDownloadOptions?): FileDownloadOptions {
            return options ?: getInstance(ifAsyncSink = false, useBufferPool = false)
        }
    }
}
