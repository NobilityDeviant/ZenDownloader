package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.response.sink

import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.pool.Slot
import java.nio.ByteBuffer

interface BufferWrapper {
    fun release()

    fun unWrap(): ByteBuffer

    class PlainBufferWrapper internal constructor(
        private val buffer: ByteBuffer
    ) : BufferWrapper {

        override fun unWrap(): ByteBuffer {
            return this.buffer
        }

        override fun release() {
            buffer.clear()
        }
    }

    class PooledBufferWrapper internal constructor(
        private val slot: Slot<ByteBuffer>
    ) : BufferWrapper {

        override fun release() {
            slot.recycle()
        }

        override fun unWrap(): ByteBuffer {
            return slot.get()!!
        }
    }

    companion object {
        fun wrap(buffer: ByteBuffer): PlainBufferWrapper {
            return PlainBufferWrapper(buffer)
        }

        fun wrap(slot: Slot<ByteBuffer>): PooledBufferWrapper {
            return PooledBufferWrapper(slot)
        }
    }
}
