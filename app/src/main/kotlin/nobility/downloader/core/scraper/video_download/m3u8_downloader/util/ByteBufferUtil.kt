package nobility.downloader.core.scraper.video_download.m3u8_downloader.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

object ByteBufferUtil {

    private val log: Logger = LoggerFactory.getLogger(ByteBufferUtil::class.java)

    fun allocateDirect(bufferSize: Int): ByteBuffer {
        var buffer: ByteBuffer
        try {
            buffer = ByteBuffer.allocateDirect(bufferSize)
        } catch (error: OutOfMemoryError) {
            // note: -XX:MaxDirectMemorySize=<size>
            log.warn("allocateDirect OOM, {}: {}", error.message, Utils.getPreviousStackTrace(1))
            // if OOM occurs, have to take the second best
            buffer = allocate(bufferSize)
        }
        return buffer
    }

    fun allocate(bufferSize: Int): ByteBuffer {
        return ByteBuffer.allocate(bufferSize)
    }
}
