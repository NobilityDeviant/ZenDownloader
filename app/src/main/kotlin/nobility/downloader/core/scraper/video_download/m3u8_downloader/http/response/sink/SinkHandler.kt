package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.response.sink

import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture

interface SinkHandler {
    @Throws(IOException::class)
    fun init(sinkFutures: MutableList<CompletableFuture<Void?>>, reInit: Boolean)

    @Throws(IOException::class)
    fun doSink(data: ByteBuffer, endData: Boolean)

    @Throws(IOException::class)
    fun dispose()
}


