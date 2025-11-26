package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.config

class PureHttpRequestConfig(
    retryCount: Int,
    requestHeaderMap: Map<String, Any>?
) : HttpRequestConfigBase(
    retryCount,
    requestHeaderMap
)
