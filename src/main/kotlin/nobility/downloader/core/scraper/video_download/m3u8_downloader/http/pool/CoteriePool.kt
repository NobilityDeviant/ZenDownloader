package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.pool

import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.CollUtil
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Preconditions
import org.apache.commons.collections4.CollectionUtils
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.function.IntUnaryOperator

class CoteriePool<T>(
    val identity: ScopedIdentity,
    val localPool: LocalPool<T>,
    val claimPlaner: IntUnaryOperator,
    val pooledObjFactory: PooledObjFactory<T>,
    slots: Collection<Slot<T>?>,
    val poolMetric: PoolMetric
) {
    private var claimCount = 0
    private val recycler: Recycler<T>
    private val freeList: ConcurrentLinkedDeque<Slot<T>>

    init {

        val freeSlots: ConcurrentLinkedDeque<Slot<T>>
        if (slots.isEmpty()) {
            freeSlots = ConcurrentLinkedDeque<Slot<T>>()
        } else {
            freeSlots = ConcurrentLinkedDeque<Slot<T>>(slots)
        }
        this.freeList = freeSlots
        this.recycler = Recycler { slot: Slot<T> -> this.deallocate(slot) }

        //if (log.isDebugEnabled()) {
          //  log.debug("new coteriePool: {}", getIdentity())
        //}
    }

    fun allocate(): Slot<T> {

        var slot = freeList.pollFirst()

        if (null == slot) {
            val execClaim = ++claimCount
            val expect = claimPlaner.applyAsInt(execClaim)

            Preconditions.checkPositive(expect, "expect")

            val slots = localPool.claim(expect)

            // allow empty ?
            Preconditions.checkArgument(CollectionUtils.isNotEmpty(slots))

            val actual = slots.size

            //if (log.isDebugEnabled()) {
              //  log.debug("claim slots from localPool, expect={}, actual={}: {}", expect, actual, getIdentity())
            //}

            poolMetric.recordClaimFromLocalPool(identity, execClaim, expect, actual)
            slot = slots.removeAt(actual - 1)
            freeList.addAll(slots)
        }

        slot.setRecycler(recycler)
        val internalGet = slot.internalGet()
        if (internalGet != null) {
            pooledObjFactory.activate(internalGet)
        }

        return slot
    }

    fun deallocate(slot: Slot<T>) {
        val internalGet = slot.internalGet()
        if (internalGet != null) {
            pooledObjFactory.passivate(internalGet)
        }
        // validate obj or check if destroyed ? depends on normalized use
        // LIFO, otherwise use fifo JCT spsc
        freeList.offerFirst(slot)
    }

    fun destroy() {
        val slots = freeList.toTypedArray<Slot<T>>()

        val slotSize = slots.size
        freeList.clear()

        localPool.release(CollUtil.newArrayList(*slots))

        //if (log.isDebugEnabled()) {
          //  log.debug("release slots to localPool, slots={} : {}", slotSize, getIdentity())
        //}

        poolMetric.recordReleaseToLocalPool(identity, slotSize)
    }

    fun getIdentity(): String {
        return identity.fullIdentity
    }
}
