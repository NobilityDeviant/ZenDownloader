package nobility.downloader.core.scraper.video_download.m3u8_downloader.core

import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.config.HttpRequestConfig
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.config.PureHttpRequestConfig
import java.net.Proxy
import java.net.URI

interface M3u8HttpRequestConfigStrategy {
    fun getConfig(
        requestType: M3u8HttpRequestType,
        uri: URI
    ): HttpRequestConfig

    class DefaultM3u8HttpRequestConfigStrategy(
        private val proxy: Proxy?,
        private val retryCount: Int,
        requestTypeHeaderMap: Map<M3u8HttpRequestType, Map<String, Any>>?
    ) : M3u8HttpRequestConfigStrategy {
        private val requestTypeHeaderMap: Map<out M3u8HttpRequestType, Map<String, Any>> =
            requestTypeHeaderMap ?: emptyMap()

        override fun getConfig(requestType: M3u8HttpRequestType, uri: URI): HttpRequestConfig {
            val headerMap = requestTypeHeaderMap[requestType]
            return PureHttpRequestConfig(this.proxy, this.retryCount, headerMap)
        }
    }
}
