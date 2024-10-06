package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.pool

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicLong
import java.util.function.BiFunction

class PoolMetric {

    private val localPoolMetrics: ConcurrentMap<ScopedIdentity, LocalPoolMetric> =
        ConcurrentHashMap()

    private val globalPoolMetrics: ConcurrentMap<ScopedIdentity, GlobalPoolMetric> =
        ConcurrentHashMap()

    private val coteriePoolMetrics: ConcurrentMap<ScopedIdentity, CoteriePoolMetric> =
        ConcurrentHashMap()

    fun recordClaimFromLocalPool(
        identity: ScopedIdentity?
    ) {
        (coteriePoolMetrics.computeIfAbsent(
            identity
        ) { CoteriePoolMetric() } as CoteriePoolMetric)
            .claimSlotsCount.getAndIncrement()
    }


    fun recordReleaseToLocalPool(
        identity: ScopedIdentity,
        totalSlots: Int
    ) {
        // alert one, after invoke recordClaimFromLocalPool
        coteriePoolMetrics.computeIfPresent(
            identity,
            BiFunction<ScopedIdentity, CoteriePoolMetric, CoteriePoolMetric> {
                    _: ScopedIdentity?, v: CoteriePoolMetric ->
                v.alertTotalSlots = totalSlots
                return@BiFunction v
            })
    }

    fun recordClaimLink(identity: ScopedIdentity) {
        (localPoolMetrics.computeIfAbsent(
            identity
        ) { LocalPoolMetric() } as LocalPoolMetric)
            .claimLinkCount.getAndIncrement()
    }

    fun recordReallocateBlock(identity: ScopedIdentity) {
        (localPoolMetrics.computeIfAbsent(
            identity
        ) { LocalPoolMetric() } as LocalPoolMetric)
            .reallocateBlockCount.getAndIncrement()
    }

    fun recordDestroyLocalPool(
        identity: ScopedIdentity,
        totalBlocks: Int?,
        idleBlocks: Int?,
        slotsOfIdleFirstBlock: Int?
    ) {
        (localPoolMetrics.computeIfAbsent(
            identity
        ) { LocalPoolMetric() } as LocalPoolMetric).apply {
            this.idleBlocks = idleBlocks
            this.totalBlocks = totalBlocks
            this.slotsOfIdleFirstBlock = slotsOfIdleFirstBlock
        }
    }

    fun recordNewChunk(identity: ScopedIdentity) {
        (globalPoolMetrics.computeIfAbsent(
            identity
        ) { GlobalPoolMetric() } as GlobalPoolMetric)
            .newChunkCount
            .getAndIncrement()
    }

    fun recordDestroyGlobalPool(
        identity: ScopedIdentity,
        totalChunks: Int?,
        idleChunks: Int?,
        blocksOfIdleFirstChunk: Int?
    ) {
        // actually, this metric is no synchronization required
        globalPoolMetrics.computeIfAbsent(
            identity
        ) { GlobalPoolMetric() }.apply {
            this.idleChunks = idleChunks
            this.totalChunks = totalChunks
            this.blocksOfIdleFirstChunk = blocksOfIdleFirstChunk
        }

    }

    private class GlobalPoolMetric {
        var idleChunks: Int? = null
        var totalChunks: Int? = null
        var blocksOfIdleFirstChunk: Int? = null
        var newChunkCount = AtomicLong()
    }

    private class LocalPoolMetric {
        var idleBlocks: Int? = null
        var totalBlocks: Int? = null
        var slotsOfIdleFirstBlock: Int? = null
        val claimLinkCount = AtomicLong()
        val reallocateBlockCount = AtomicLong()
    }

    private class CoteriePoolMetric {
        var alertTotalSlots: Int? = null
        val claimSlotsCount = AtomicLong()
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(PoolMetric::class.java)
    }
}
