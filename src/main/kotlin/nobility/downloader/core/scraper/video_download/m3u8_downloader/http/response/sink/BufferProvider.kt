package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.response.sink

import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.ByteBufferPool
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.pool.CoteriePool
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.pool.LocalPool
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.pool.ScopedIdentity
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.pool.Slot
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.ByteBufferUtil
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Preconditions
import java.io.IOException
import java.nio.ByteBuffer
import java.util.function.Supplier

/**
 * note: thread unsafe
 */
interface BufferProvider : SinkLifeCycle {
    fun newBuffer(): BufferWrapper?

    class PlainDirectBufferProvider internal constructor(initBufferSize: Int) : BufferProvider {
        private val initBufferSize: Int = Preconditions.checkPositive(initBufferSize, "initBufferSize")

        override fun newBuffer(): BufferWrapper {
            return BufferWrapper.wrap(ByteBufferUtil.allocateDirect(initBufferSize))
        }
    }

    class PlainHeapBufferProvider internal constructor(initBufferSize: Int) : BufferProvider {
        private val initBufferSize = Preconditions.checkPositive(initBufferSize, "initBufferSize")

        override fun newBuffer(): BufferWrapper {
            return BufferWrapper.wrap(ByteBufferUtil.allocate(initBufferSize))
        }
    }

    class CoteriePoolBufferProvider internal constructor(
        private val identity: String,
        private val coteriePoolSupplier: Supplier<CoteriePool<ByteBuffer>>
    ) : BufferProvider {

        private var coteriePool: CoteriePool<ByteBuffer>? = null

        override fun newBuffer(): BufferWrapper {
            if (null == coteriePool) {
                coteriePool = coteriePoolSupplier.get()
                //if (log.isDebugEnabled()) {
                  //  log.debug("init coteriePool: {}", identity)
                //}
            }
            val slot = coteriePool!!.allocate()
            return BufferWrapper.wrap(slot)
        }

        @Throws(IOException::class)
        override fun dispose() {
            coteriePool?.destroy()
        }
    }

    class LocalPoolBufferProvider(
        private val localPoolSupplier: Supplier<LocalPool<ByteBuffer>>
    ) : BufferProvider {

        private var localPool: LocalPool<ByteBuffer>? = null

        override fun newBuffer(): BufferWrapper {
            if (null == localPool) {
                localPool = localPoolSupplier.get()
            }
            val slot: Slot<ByteBuffer> = localPool!!.allocate()
            return BufferWrapper.wrap(slot)
        }
    }

    companion object {
        fun plainDirectBuffer(initBufferSize: Int): BufferProvider {
            return PlainDirectBufferProvider(initBufferSize)
        }

        fun plainHeapBuffer(initBufferSize: Int): BufferProvider {
            return PlainHeapBufferProvider(initBufferSize)
        }

        fun coteriePoolBuffer(
            bufferPool: ByteBufferPool,
            identity: ScopedIdentity
        ): BufferProvider {
            Preconditions.checkNotNull(identity)
            return CoteriePoolBufferProvider(
                identity.fullIdentity
            ) { bufferPool.allocateCoterie(identity) }
        }

        fun localPoolBuffer(bufferPool: ByteBufferPool): BufferProvider {
            return LocalPoolBufferProvider(bufferPool::localPool)
        }
    }
}
