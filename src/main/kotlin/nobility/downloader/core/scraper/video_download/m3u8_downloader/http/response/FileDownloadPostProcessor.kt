package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.response

interface FileDownloadPostProcessor {
    /**
     * @param contentLength nullable
     */
    fun startDownload(
        contentLength: Long?,
        reStart: Boolean
    ) {
    }

    fun afterReadBytes(size: Int, end: Boolean) {
    }

    fun afterDownloadComplete() {
    }

    fun afterDownloadFailed() {
    }

    companion object {
        val NOP: FileDownloadPostProcessor = object : FileDownloadPostProcessor {}
    }
}
