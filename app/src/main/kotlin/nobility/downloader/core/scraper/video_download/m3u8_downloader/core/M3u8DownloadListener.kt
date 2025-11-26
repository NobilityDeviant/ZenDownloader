package nobility.downloader.core.scraper.video_download.m3u8_downloader.core

data class M3u8DownloadListener(
    var downloadStarted: ((M3u8Download) -> Unit)? = null,
    //dl percent, remaining secs
    var downloadProgress: ((String, Int) -> Unit)? = null,
    var downloadSizeUpdated: ((Long) -> Unit)? = null,
    //download, success
    var downloadFinished: ((M3u8Download, Boolean) -> Unit)? = null,
    var onMergeStarted: ((M3u8Download, String) -> Unit)? = null,
    var onMergeFinished: ((M3u8Download, Exception?) -> Unit)? = null
)
