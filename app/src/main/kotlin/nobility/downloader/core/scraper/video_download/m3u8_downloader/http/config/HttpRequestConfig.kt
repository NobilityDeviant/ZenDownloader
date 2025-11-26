package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.config

interface HttpRequestConfig {
    val retryCount: Int
    val requestHeaderMap: Map<String, Any>?
}
