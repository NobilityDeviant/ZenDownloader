package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.pool

import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.CollUtil
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.CollUtil.newArrayDeque
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.CollUtil.newArrayDequeWithCapacity
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Preconditions
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.LongAdder
import java.util.function.Consumer
import java.util.function.IntUnaryOperator


class LocalPool<T>(
    val identity: ScopedIdentity,
    private val globalPool: GlobalPool<T>,
    private val blocksPerReallocate: Int,
    private val slotsPerLink: Int,
    slotsOfInitialCoterie: Int,
    private val pooledObjFactory: PooledObjFactory<T>,
    blocks: List<Block<T>>,
    private val poolMetric: PoolMetric
) {
    private var totalBlocks: Int

    private val owner: Thread

    private val releaseLinkThreshold = slotsPerLink + slotsOfInitialCoterie

    //this.blocksPerReallocate = Preconditions.checkPositive(blocksPerReallocate, "blocksPerReallocate")
    private val slotsOfInitialCoterie = Preconditions.checkNonNegative(
        slotsOfInitialCoterie,
        "slotsOfInitialCoterie"
    )

    private val recycler: Recycler<T>

    private val freeListSize: LongAdder

    private val freeListLinkState: AtomicBoolean

    private val blockList: BlockList<T>

    private val localStack: ArrayDeque<Slot<T>?>

    private val freeList: ConcurrentLinkedDeque<Slot<T>>

    init {

        this.recycler = Recycler { slot: Slot<T> -> this.deallocate(slot) }
        this.freeListSize = LongAdder()
        this.owner = Thread.currentThread()
        this.localStack = ArrayDeque()
        this.blockList = BlockList(blocks)
        this.totalBlocks = blockList.size()
        this.freeList = ConcurrentLinkedDeque<Slot<T>>()
        this.freeListLinkState = AtomicBoolean(false)
        //if (log.isDebugEnabled()) {
          //  log.debug("new localPool: {}", getIdentity())
        //}
    }

    fun allocateCoterie(
        identity: ScopedIdentity,
        claimPlaner: IntUnaryOperator
    ): CoteriePool<T> {
        val slots = claim(slotsOfInitialCoterie)
        return CoteriePool(
            identity,
            this,
            claimPlaner,
            pooledObjFactory,
            slots,
            poolMetric
        )
    }

    fun allocate(): Slot<T> {
        val slots = claim(1)
        val slot = slots[0]

        slot.setRecycler(recycler)
        val internalGet = slot.internalGet()
        if (internalGet != null) {
            pooledObjFactory.activate(internalGet)
        }

        return slot
    }

    private fun deallocate(slot: Slot<T>) {
        val internalGet = slot.internalGet()
        if (internalGet != null) {
            pooledObjFactory.passivate(internalGet)
        }
        release(listOf(slot))
    }

    fun getIdentity(): String {
        return identity.fullIdentity
    }

    fun destroy() {
        //if (log.isDebugEnabled()) {
          //  log.debug("destroy localPool: {}", getIdentity())
        //}

        poolMetric.recordDestroyLocalPool(
            identity,
            totalBlocks,
            blockList.size(),
            blockList.slotSizeOfFirstBlock
        )

        this.totalBlocks = 0
        freeListSize.reset()

        freeList.clear()
        blockList.clear()
        localStack.clear()
    }

    fun claim(expect: Int): MutableList<Slot<T>> {

        val slots = CollUtil.newArrayListWithCapacity<Slot<T>>(expect)

        var c = 0
        var slot: Slot<T>? = null
        while (c < expect && null != (localStack.pollFirst().also {
                if (it != null) {
                    slot = it
                }
            })) {
            slots.add(slot!!)
            c++
        }
        var diff = expect - slots.size
        if (diff <= 0) {
            return slots
        }

        val p = slots.size
        while (c < expect && null != (freeList.pollFirst().also { slot = it })) {
            slots.add(slot!!)
            c++
        }
        freeListSize.add((p - slots.size).toLong())
        diff = expect - slots.size
        if (diff <= 0) {
            return slots
        }

        val link = globalPool.claim()
        if (null != link) {
            //if (log.isDebugEnabled()) {
              //  log.debug("claim link from globalPool: {}", getIdentity())
            //}
            poolMetric.recordClaimLink(identity)
            // assert expect < link.size ?
            slots.addAll(link.getSlot(diff))
            val newSlots = link.andRemove
            newSlots.forEach(Consumer { e: Slot<T> -> localStack.offerFirst(e) })
            return slots
        }

        // form block
        slots.addAll(blockList.getSlot(diff))
        diff = expect - slots.size
        if (diff <= 0) {
            return slots
        }

        // from new block
        val newBlocks: List<Block<T>> = globalPool.allocateBlock(blocksPerReallocate)
        blockList.addBlocks(newBlocks)
        //if (log.isDebugEnabled()) {
          //  log.debug("allocate block from globalPool: {}", getIdentity())
        //}
        totalBlocks += newBlocks.size
        poolMetric.recordReallocateBlock(identity)
        slots.addAll(blockList.getSlot(diff))

        return slots
    }

    fun release(c: List<Slot<T>>) {
        val localStack = this.localStack
        val freeList: ConcurrentLinkedDeque<Slot<T>> = this.freeList
        val isLocalThread = Thread.currentThread() === owner
        val releaseSize = c.size
        var stackSize = localStack.size

        if (isLocalThread) {
            // try to reduce call freeListSize.sum()
            if ((!freeListLinkState.get() && releaseSize + stackSize < releaseLinkThreshold) && (releaseSize + stackSize + freeListSize.sum()) < releaseLinkThreshold) {
                c.forEach(Consumer { e: Slot<T> ->
                    localStack.offerFirst(e)
                })
                return
            }
        } else {
            if (freeListLinkState.get() || (releaseSize + freeListSize.sum()) < releaseLinkThreshold) {
                if (1 == releaseSize) {
                    c.forEach(Consumer { e: Slot<T>? -> freeList.offerFirst(e) })
                } else {
                    freeList.addAll(c)
                }
                freeListSize.add(releaseSize.toLong())
                return
            }
        }

        // try release link
        val slotsPerLink = this.slotsPerLink
        if (isLocalThread) {
            var slot: Slot<T>
            var linkSlots: ArrayDeque<Slot<T>> = newArrayDequeWithCapacity(slotsPerLink)
            if (!freeListLinkState.get() && freeListLinkState.compareAndSet(false, true)) {
                var releaseFreeSize = 0
                while (null != (freeList.pollLast().also { slot = it })) {
                    linkSlots.add(slot)
                    releaseFreeSize++
                    if (slotsPerLink == linkSlots.size) {
                        globalPool.release(Link(linkSlots))
                        //if (log.isDebugEnabled()) {
                        //  log.debug("release link to globalPool: {}", getIdentity())
                        //}
                        linkSlots = newArrayDequeWithCapacity(slotsPerLink)
                    }
                }
                freeListSize.add(-releaseFreeSize.toLong())
                freeListLinkState.compareAndSet(true, false)
            }

            c.forEach(Consumer { e: Slot<T> -> localStack.offerFirst(e) })
            linkSlots.forEach(Consumer { e: Slot<T> -> localStack.offerLast(e) })
            stackSize = localStack.size
            if (stackSize >= slotsPerLink) {
                var count = stackSize
                val remainingBreak = stackSize % slotsPerLink
                linkSlots = newArrayDequeWithCapacity(slotsPerLink)
                while (null != (freeList.pollLast().also { slot = it })) {
                    linkSlots.add(slot)
                    count--
                    if (slotsPerLink == linkSlots.size) {
                        globalPool.release(Link(linkSlots))
                        //if (log.isDebugEnabled()) {
                        //  log.debug("release link to globalPool: {}", getIdentity())
                        //}
                        if (count <= remainingBreak) {
                            break
                        }
                        linkSlots = newArrayDequeWithCapacity(slotsPerLink)
                    }
                }
            }
        } else {
            if (!freeListLinkState.get() && freeListLinkState.compareAndSet(false, true)) {
                var slot: Slot<T>
                var releaseFreeSize = 0
                var linkSlots: ArrayDeque<Slot<T>> = newArrayDequeWithCapacity(slotsPerLink)
                while (null != (freeList.pollLast().also { slot = it })) {
                    linkSlots.add(slot)
                    releaseFreeSize++
                    if (slotsPerLink == linkSlots.size) {
                        globalPool.release(Link(linkSlots))
                        linkSlots = newArrayDequeWithCapacity(slotsPerLink)
                    }
                }
                freeListSize.add(-releaseFreeSize.toLong())
                freeListLinkState.compareAndSet(true, false)
                if (linkSlots.size + releaseSize >= slotsPerLink) {
                    val deque = newArrayDeque<Slot<T>>(linkSlots)
                    c.forEach(Consumer { e: Slot<T> -> deque.addFirst(e) })

                    linkSlots = newArrayDequeWithCapacity(slotsPerLink)
                    while (null != (deque.pollLast().also { slot = it })) {
                        linkSlots.add(slot)
                        if (slotsPerLink == linkSlots.size) {
                            globalPool.release(Link(linkSlots))
                            //if (log.isDebugEnabled()) {
                            //  log.debug("release link to globalPool: {}", getIdentity())
                            //}
                            linkSlots = newArrayDequeWithCapacity(slotsPerLink)
                        }
                    }

                    val remaining = linkSlots.size
                    if (remaining > 0) {
                        if (remaining == 1) {
                            freeList.offerFirst(linkSlots.pollFirst())
                        } else {
                            freeList.addAll(linkSlots)
                        }
                    }
                } else {
                    linkSlots.addAll(c)
                    freeList.addAll(linkSlots)
                    freeListSize.add(linkSlots.size.toLong())
                }
            } else {
                if (1 == releaseSize) {
                    c.forEach(Consumer { e: Slot<T>? -> freeList.offerFirst(e) })
                } else {
                    freeList.addAll(c)
                }
                freeListSize.add(releaseSize.toLong())
            }
        }
    }
}
