package nobility.downloader.core.scraper.video_download.m3u8_downloader.core

interface M3u8DownloadListener {

    fun downloadStarted(m3u8Download: M3u8Download) {
    }

    fun downloadProgress(
        downloadPercentage: String,
        remainingSeconds: Int,
        remainingTsCount: Int,
        totalTsCount: Int
    ) {

    }

    fun downloadSizeUpdated(fileSize: Long) {

    }

    fun downloadFinished(
        m3u8Download: M3u8Download,
        complete:Boolean
    ) {
    }

    fun onMergeStarted(m3u8Download: M3u8Download) {

    }

    fun onMergeFinished(m3u8Download: M3u8Download) {

    }

}
