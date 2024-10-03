package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.config

import java.net.Proxy

class PureHttpRequestConfig(
    proxy: Proxy?,
    retryCount: Int,
    requestHeaderMap: Map<String, Any>?
) : HttpRequestConfigBase(proxy, retryCount, requestHeaderMap)
