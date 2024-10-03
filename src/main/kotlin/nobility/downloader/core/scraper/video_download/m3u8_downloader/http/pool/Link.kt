package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.pool

import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.CollUtil
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Preconditions
import java.util.*
import kotlin.math.min

class Link<T>(slots: ArrayDeque<Slot<T>>) {

    private val slots = Preconditions.checkNotEmpty(slots) as ArrayDeque<Slot<T>>

    fun size(): Int {
        return slots.size
    }

    val isEmpty: Boolean
        get() = slots.isEmpty()

    fun getSlot(expect: Int): List<Slot<T>> {
        var size = 0
        if (expect <= 0 || 0 == (slots.size.also { size = it })) {
            return emptyList()
        }

        val actual = min(size.toDouble(), expect.toDouble()).toInt()
        val list: MutableList<Slot<T>> = CollUtil.newArrayListWithCapacity(actual)

        var c = 0
        var slot: Slot<T>? = null
        while (c++ < actual && null != (slots.pollFirst().also { slot = it })) {
            list.add(slot!!)
        }

        return list
    }

    val andRemove: List<Slot<T>>
        // ----------------- internal method -------------------- //
        get() {
            val copy: List<Slot<T>> = CollUtil.newArrayList<Slot<T>>(
                this.slots
            )
            slots.clear()
            return copy
        }
}
