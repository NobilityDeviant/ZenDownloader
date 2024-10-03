package nobility.downloader.core.scraper.video_download.m3u8_downloader.http

import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.pool.ObjectPool
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.pool.PoolConfig
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.pool.PoolMetric
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.pool.PooledObjFactory
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.ByteBufferUtil
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.CollUtil
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Preconditions
import java.nio.ByteBuffer

/**
 * A special byteBuffer object pool, not universal.
 */
class ByteBufferPool(
    poolIdentity: String,
    poolConfig: PoolConfig,
    poolMetric: PoolMetric,
    pooledObjFactory: PooledObjFactory<ByteBuffer>
) : ObjectPool<ByteBuffer>(
    poolIdentity,
    poolConfig,
    poolMetric,
    pooledObjFactory
) {
    private abstract class ByteBufferPoolFactory : PooledObjFactory<ByteBuffer> {
        override fun newInstance(size: Int): List<ByteBuffer> {
            val byteBuffers: MutableList<ByteBuffer> = CollUtil.newArrayListWithCapacity(size)
            for (i in 0 until size) {
                byteBuffers.add(newInstance())
            }
            return byteBuffers
        }

        override fun validate(buffer: ByteBuffer?): Boolean {
            if (null == buffer) {
                return false
            }
            if (buffer.hasArray()) {
                return buffer.capacity() > 0
            } //else if (buffer.isDirect) {
                //return 0L != (buffer as DirectBuffer).address()
            //}
            return false
        }

        override fun activate(obj: ByteBuffer) {
        }

        override fun passivate(obj: ByteBuffer) {
            obj.clear()
        }

        override fun free(obj: ByteBuffer) {
            //if (obj.isDirect) {
              //  (obj as DirectBuffer).cleaner().clean()
            //}
            obj.clear()
        }


        override fun free(objs: List<ByteBuffer>) {
            for (buffer in objs) {
                free(buffer)
            }
        }
    }

    companion object {
        fun newDirectBufferPool(
            poolIdentity: String,
            bufferSize: Int,
            poolConfig: PoolConfig
        ): ByteBufferPool {
            Preconditions.checkPositive(bufferSize, "bufferSize")
            val pooledObjFactory: PooledObjFactory<ByteBuffer> = object : ByteBufferPoolFactory() {
                override val type: Class<ByteBuffer> = ByteBufferUtil.allocateDirect(0).javaClass

                override fun newInstance(): ByteBuffer {
                    return ByteBufferUtil.allocateDirect(bufferSize)
                }
            }

            val poolMetric = PoolMetric(poolConfig)
            return ByteBufferPool(poolIdentity, poolConfig, poolMetric, pooledObjFactory)
        }

        fun newHeapBufferPool(
            poolIdentity: String,
            bufferSize: Int,
            poolConfig: PoolConfig
        ): ByteBufferPool {
            Preconditions.checkPositive(bufferSize, "bufferSize")
            val pooledObjFactory: PooledObjFactory<ByteBuffer> = object : ByteBufferPoolFactory() {
                override val type: Class<ByteBuffer> = ByteBufferUtil.allocate(0).javaClass

                override fun newInstance(): ByteBuffer {
                    return ByteBufferUtil.allocate(bufferSize)
                }
            }

            val poolMetric = PoolMetric(poolConfig)
            return ByteBufferPool(
                poolIdentity,
                poolConfig,
                poolMetric,
                pooledObjFactory
            )
        }
    }
}
