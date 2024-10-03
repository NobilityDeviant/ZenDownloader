package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.config

import java.net.Proxy

interface HttpRequestConfig {
    val proxy: Proxy?
    val retryCount: Int
    val requestHeaderMap: Map<String, Any>?
}
