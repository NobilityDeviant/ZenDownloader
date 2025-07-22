package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.pool

import java.nio.ByteBuffer

interface PooledObjFactory<T> {
    fun newInstance(): T

    val type: Class<T>

    fun newInstance(size: Int): List<T>

    fun validate(buffer: ByteBuffer?): Boolean {
        return true
    }

    fun activate(obj: T) {
    }

    fun passivate(obj: T) {
    }

    fun free(obj: T) {
    }

    fun free(objs: List<T>) {
    }
}
