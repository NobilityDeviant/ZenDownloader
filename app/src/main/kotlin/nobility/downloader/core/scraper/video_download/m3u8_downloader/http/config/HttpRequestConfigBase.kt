package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.config

import java.net.Proxy
import java.util.*

abstract class HttpRequestConfigBase(
    override val proxy: Proxy?,
    override val retryCount: Int,
    requestHeaderMap: Map<String, Any>?
) : HttpRequestConfig {

    override val requestHeaderMap: Map<String, Any> =
        if (null == requestHeaderMap) emptyMap() else Collections.unmodifiableMap(requestHeaderMap)
}
