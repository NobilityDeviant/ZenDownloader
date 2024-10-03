package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.pool

import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.CollUtil
import kotlin.math.min

class Block<T>(
    private val slots: ArrayList<Slot<T>>
) {

    private var size: Int

    init {
        this.size = slots.size
    }

    fun size(): Int {
        return this.size
    }

    val isEmpty: Boolean
        get() = this.size == 0

    fun getSlot(expect: Int): List<Slot<T>> {
        if (expect <= 0 || isEmpty) {
            return emptyList()
        }

        val actual = min(size.toDouble(), expect.toDouble()).toInt()
        val list: MutableList<Slot<T>> = CollUtil.newArrayListWithCapacity(actual)

        for (i in 0 until actual) {
            list.add(slots[--size])
        }

        return list
    }

    val andRemove: List<Slot<T>>
        get() {
            val copy: List<Slot<T>> = CollUtil.newArrayList(this.slots)
            this.size = 0
            slots.clear()
            return copy
        }
}
