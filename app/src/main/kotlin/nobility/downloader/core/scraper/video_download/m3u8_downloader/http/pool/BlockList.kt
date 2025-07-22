package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.pool

import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.CollUtil.newArrayDeque
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.CollUtil.newArrayListWithCapacity
import org.apache.commons.collections4.CollectionUtils
import java.util.*

class BlockList<T>(blocks: Collection<Block<T>>) {

    private val blockList: ArrayDeque<Block<T>>

    init {
        val blockList = if (blocks.isEmpty()) {
            newArrayDeque()
        } else {
            newArrayDeque(
                blocks
            )
        }
        this.blockList = blockList
    }

    fun size(): Int {
        return blockList.size
    }

    val isEmpty: Boolean
        get() = blockList.isEmpty()

    fun getSlot(expect: Int): List<Slot<T>> {
        var block: Block<T>? = blockList.peekFirst()
            ?: return emptyList()

        var diff = expect
        val slots: MutableList<Slot<T>> = newArrayListWithCapacity(expect)

        do {
            // assert expect < slotsPerBlock ?
            slots.addAll(block!!.getSlot(diff))
            if (block.isEmpty) {
                blockList.pollFirst()
            }
            diff = expect - slots.size
            if (diff <= 0) {
                return slots
            }
        } while (null != (blockList.peekFirst().also { block = it }))

        return slots
    }

    fun addBlocks(blocks: List<Block<T>>?) {
        for (block in CollectionUtils.emptyIfNull(blocks)) {
            if (null != block) {
                blockList.offerLast(block)
            }
        }
    }

    fun clear() {
        blockList.clear()
    }

    val slotSizeOfFirstBlock: Int
        get() {
            val block = blockList.peekFirst() ?: return 0
            return block.size()
        }
}
