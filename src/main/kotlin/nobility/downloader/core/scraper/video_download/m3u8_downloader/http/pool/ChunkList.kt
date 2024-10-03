package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.pool

import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.CollUtil.newArrayDeque
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.CollUtil.newArrayListWithCapacity
import org.apache.commons.collections4.CollectionUtils
import java.util.*

class ChunkList<T>(
    chunks: Collection<Chunk<T>> = newArrayDeque()
) {

    private val chunkList: ArrayDeque<Chunk<T>>

    init {
        val chunkList = if (CollectionUtils.isEmpty(chunks)) {
            newArrayDeque()
        } else {
            newArrayDeque(
                chunks
            )
        }
        this.chunkList = chunkList
    }

    fun size(): Int {
        return chunkList.size
    }

    val isEmpty: Boolean
        get() = chunkList.isEmpty()

    fun getBlock(expect: Int): List<Block<T>> {
        var chunk: Chunk<T>? = chunkList.peekFirst()
            ?: return emptyList()

        var diff = expect
        val blocks: MutableList<Block<T>> = newArrayListWithCapacity(expect)

        do {
            // assert expect <= blocksPerChunk ?
            blocks.addAll(chunk!!.getBlock(diff))
            if (chunk.isEmpty) {
                chunkList.pollFirst()
            }
            diff = expect - blocks.size
            if (diff <= 0) {
                return blocks
            }
        } while (null != (chunkList.peekFirst().also { chunk = it }))

        return blocks
    }

    fun addChunk(chunk: Chunk<T>) {
        addChunks(listOf(chunk))
    }

    fun addChunks(chunks: List<Chunk<T>>?) {
        for (chunk in CollectionUtils.emptyIfNull(chunks)) {
            if (null != chunk) {
                chunkList.offerLast(chunk)
            }
        }
    }

    fun clear() {
        chunkList.clear()
    }

    val blockSizeOfFirstChunk: Int
        get() {
            val chunk = chunkList.peekFirst() ?: return 0
            return chunk.size()
        }
}