package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.response.sink

import java.io.IOException

interface SinkLifeCycle {
    @Throws(IOException::class)
    fun init(reInit: Boolean) {
    }

    @Throws(IOException::class)
    fun dispose() {
    }
}
