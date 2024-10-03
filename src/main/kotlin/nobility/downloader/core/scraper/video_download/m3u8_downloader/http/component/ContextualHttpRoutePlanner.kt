package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.component

import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Preconditions
import org.apache.hc.client5.http.HttpRoute
import org.apache.hc.client5.http.protocol.HttpClientContext
import org.apache.hc.client5.http.routing.HttpRoutePlanner
import org.apache.hc.core5.http.HttpException
import org.apache.hc.core5.http.HttpHost
import org.apache.hc.core5.http.protocol.HttpContext

class ContextualHttpRoutePlanner(routePlanner: HttpRoutePlanner?) : HttpRoutePlanner {
    private val routePlanner: HttpRoutePlanner = Preconditions.checkNotNull(routePlanner)

    @Throws(HttpException::class)
    override fun determineRoute(target: HttpHost, context: HttpContext): HttpRoute {
        val clientContext = HttpClientContext.adapt(context)
        val routeInfo = clientContext.httpRoute
        if (routeInfo is HttpRoute) {
            return routeInfo
        }
        return routePlanner.determineRoute(target, context)
    }
}
