package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.pool

import java.util.*
import kotlin.concurrent.Volatile

class Slot<T>(var value: T?) {

    @Volatile
    private var recycler: Recycler<T>? = null

    fun get(): T? {
        if (isRecycled) {
            //if (log.isDebugEnabled()) {
              //  log.debug("found {} isRecycled, invoke in {}", this, Utils.getPreviousStackTrace(1))
            //}
            return null
        }
        return value
    }

    fun recycle() {
        if (isRecycled) {
            return
        }
        val r = recycler
        this.recycler = null
        r?.recycle(this)
    }

    private val isRecycled: Boolean
        get() = null == recycler

    // ----------------- internal method -------------------- //
    fun setRecycler(recycler: Recycler<T>?) {
        this.recycler = Objects.requireNonNull(recycler)
    }

    fun internalGet(): T? {
        return this.value
    }

    val andRemove: T?
        get() {
            val v = this.value
            this.value = null
            this.recycler = null
            return v
        }
}
