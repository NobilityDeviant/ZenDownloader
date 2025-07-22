package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.pool

import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.CollUtil
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Preconditions
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.Volatile

open class GlobalPool<T>(
    private val identity: ScopedIdentity,
    private val pooledObjFactory: PooledObjFactory<T>,
    private val poolConfig: PoolConfig,
    private val poolMetric: PoolMetric
) {
    // normal = 0, destroyed = -1
    @Volatile
    private var state = 0

    private val lock: Lock = ReentrantLock()

    // --------------------- pooled obj refer --------------------- //
    private val chunkList = ChunkList<T>()

    private val allChunks: ArrayList<Chunk<T>> = ArrayList()

    private val linkQueue: ConcurrentLinkedDeque<Link<T>> = ConcurrentLinkedDeque<Link<T>>()

    init {

        val initialChunks: Int = poolConfig.chunksOfInitialGlobalPool()
        for (i in 0 until initialChunks) {
            val chunk = newChunk()
            allChunks.add(chunk)
        }
        chunkList.addChunks(allChunks)
        //if (log.isDebugEnabled()) {
          //  log.debug("init new chunk, initialChunks={}: {}", initialChunks, getIdentity())
        //}
    }

    fun claim(): Link<T>? {
        return linkQueue.pollFirst()
    }

    fun release(link: Link<T>?) {
        // validate obj ?
        linkQueue.offerFirst(link)
    }

    fun allocateBlock(expect: Int): List<Block<T>> {
        Preconditions.checkState(0 == state, "GlobalPool is destroyed: %s", getIdentity())
        lock.lock()
        var makeChunk = false
        try {
            val blocks: List<Block<T>> = chunkList.getBlock(expect)
            val diff = expect - blocks.size
            if (diff <= 0) {
                return blocks
            }

            val newChunk = newChunk()
            chunkList.addChunk(newChunk)
            makeChunk = allChunks.add(newChunk)

            val res: MutableList<Block<T>> = ArrayList(expect)
            res.addAll(blocks)
            res.addAll(chunkList.getBlock(diff))
            return res
        } finally {
            lock.unlock()
            if (makeChunk) {
                //if (log.isDebugEnabled()) {
                  //  log.debug("new chunk: {}", getIdentity())
                //}
                poolMetric.recordNewChunk(identity)
            }
        }
    }

    private fun newChunk(): Chunk<T> {
        val slotsPerBlock = poolConfig.slotsPerBlock()
        val blocksPerChunk = poolConfig.blocksPerChunk()
        val blocks: ArrayList<Block<T>> = CollUtil.newArrayListWithCapacity(
            blocksPerChunk
        )

        for (i in 0 until blocksPerChunk) {
            val objs: List<T> = pooledObjFactory.newInstance(slotsPerBlock)
            val slots: ArrayList<Slot<T>> = ArrayList(objs.size)
            for (obj in objs) {
                slots.add(Slot(obj))
            }
            blocks.add(Block(slots))
        }
        return Chunk(blocks)
    }

    @Synchronized
    fun destroy() {
        if (-1 == state) {
            return
        }
        state = -1
        //log.info("free globalPool: {}", getIdentity())

        poolMetric.recordDestroyGlobalPool(identity, allChunks.size, chunkList.size(), chunkList.blockSizeOfFirstChunk)

        for (chunk in allChunks) {
            val blocks = chunk.andRemove
            for (block in blocks) {
                val slots: List<Slot<T>> = block.andRemove
                val objs: MutableList<T> = ArrayList(slots.size)
                for (slot in slots) {
                    val o: T? = slot.andRemove
                    if (null != o) {
                        objs.add(o)
                    }
                }
                pooledObjFactory.free(objs)
            }
        }

        allChunks.clear()
        linkQueue.clear()
        chunkList.clear()
    }

    private fun getIdentity(): String {
        return identity.fullIdentity
    }

    val scopedIdentity: ScopedIdentity
        get() = identity
}
