package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.pool

import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.CollUtil
import kotlin.math.min

class Chunk<T>(private val blocks: ArrayList<Block<T>>) {

    private var size: Int

    init {
        this.size = blocks.size
    }

    fun size(): Int {
        return this.size
    }

    val isEmpty: Boolean
        get() = this.size == 0

    fun getBlock(expect: Int): List<Block<T>> {
        if (expect <= 0 || isEmpty) {
            return emptyList()
        }

        val actual = min(size.toDouble(), expect.toDouble()).toInt()
        val list: MutableList<Block<T>> = ArrayList(actual)

        for (i in 0 until actual) {
            list.add(blocks[--size])
        }

        return list
    }

    val andRemove: List<Block<T>>
        // ----------------- internal method -------------------- //
        get() {
            val copy: List<Block<T>> = CollUtil.newArrayList(
                this.blocks
            )
            this.size = 0
            blocks.clear()
            return copy
        }
}
