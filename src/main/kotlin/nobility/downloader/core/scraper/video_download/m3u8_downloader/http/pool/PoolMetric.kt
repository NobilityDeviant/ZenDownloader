package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.pool

import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Utils
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicLong
import java.util.function.BiFunction

class PoolMetric(private val poolConfig: PoolConfig?) {

    private val localPoolMetrics: ConcurrentMap<ScopedIdentity, LocalPoolMetric> =
        ConcurrentHashMap()

    private val globalPoolMetrics: ConcurrentMap<ScopedIdentity, GlobalPoolMetric> =
        ConcurrentHashMap()

    private val coteriePoolMetrics: ConcurrentMap<ScopedIdentity, CoteriePoolMetric> =
        ConcurrentHashMap()

    fun recordClaimFromLocalPool(
        identity: ScopedIdentity?,
        execCount: Int,
        expect: Int,
        actual: Int
    ) {
        (coteriePoolMetrics.computeIfAbsent(
            identity
        ) { CoteriePoolMetric(it) } as CoteriePoolMetric)
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
                k: ScopedIdentity?, v: CoteriePoolMetric ->
                v.alertTotalSlots = totalSlots
                return@BiFunction v
            })
    }

    fun recordClaimLink(identity: ScopedIdentity) {
        (localPoolMetrics.computeIfAbsent(
            identity
        ) { LocalPoolMetric(identity) } as LocalPoolMetric)
            .claimLinkCount.getAndIncrement()
    }

    fun recordReallocateBlock(identity: ScopedIdentity) {
        (localPoolMetrics.computeIfAbsent(
            identity
        ) { LocalPoolMetric(identity) } as LocalPoolMetric)
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
        ) { LocalPoolMetric(identity) } as LocalPoolMetric).apply {
            this.idleBlocks = idleBlocks
            this.totalBlocks = totalBlocks
            this.slotsOfIdleFirstBlock = slotsOfIdleFirstBlock
        }
    }

    fun recordNewChunk(identity: ScopedIdentity) {
        (globalPoolMetrics.computeIfAbsent(
            identity
        ) { GlobalPoolMetric(identity) } as GlobalPoolMetric)
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
        ) { GlobalPoolMetric(identity) }.apply {
            this.idleChunks = idleChunks
            this.totalChunks = totalChunks
            this.blocksOfIdleFirstChunk = blocksOfIdleFirstChunk
        }

    }

    private fun rate(a: Long, b: Long): String {
        return if (0L == a || 0L == b) {
            "0.000%"
        } else {
            Utils.rate(a, b).toString() + "%"
        }
    }

    /*fun printMetrics() {
        var combinedTotalSlots = 0
        var combinedIdleSlots = 0
        val slotsPerBlock: Int = poolConfig.slotsPerBlock()
        val slotsPerChunk: Int = poolConfig.slotsPerChunk()
        val blocksPerChunk: Int = poolConfig.blocksPerChunk()
        val marker: Marker = WhiteboardMarkers.getWhiteboardMarker()
        val comparator: Comparator<ScopedIdentity> =
            Comparator.nullsLast<ScopedIdentity>(Comparator.comparing<Any, Any>(ScopedIdentity::getFullIdentity))

        val globalPoolFormat: TextTableFormat = textTableFormat(log, marker)
            .setTitles("identity", "newChunkCount", "totalChunks", "idleChunks", "idleRate")
        val globalEntryList: List<Map.Entry<ScopedIdentity, GlobalPoolMetric>> = globalPoolMetrics.entries.stream()
            .sorted(java.util.Map.Entry.comparingByKey<ScopedIdentity, GlobalPoolMetric>(comparator))
            .collect<List<Map.Entry<ScopedIdentity, GlobalPoolMetric>>, Any>(
                Collectors.toList<Map.Entry<ScopedIdentity, GlobalPoolMetric>>()
            )
        for ((identity, metric) in globalEntryList) {
            var idleChunks: String?
            var idleRate: String? = null
            val blocksOfIdleFirstChunk: Int = metric.getBlocksOfIdleFirstChunk()
            val totalChunks: String = mapToStrIfNull(metric.getTotalChunks(), null)
            val newChunkCount: String = mapToStrIfNull(metric.getNewChunkCount(), null)

            if (null != blocksOfIdleFirstChunk && blocksOfIdleFirstChunk > 0 && blocksOfIdleFirstChunk < blocksPerChunk) {
                val fullIdleChunks = ObjectUtils.defaultIfNull(metric.getIdleChunks(), 0) - 1
                idleChunks =
                    (if (fullIdleChunks <= 0) "" else fullIdleChunks.toString() + "chunks + ") + blocksOfIdleFirstChunk + "blocks"
                if (Objects.nonNull(metric.getTotalChunks())) {
                    val totalBlocks = metric.getTotalChunks() as Long * blocksPerChunk
                    val idleBlocks: Long = blocksOfIdleFirstChunk + blocksPerChunk.toLong() *
                            (if (Objects.nonNull(metric.getIdleChunks())) metric.getIdleChunks() - 1 else 0)
                    idleRate = rate(idleBlocks, totalBlocks)

                    combinedIdleSlots += (idleBlocks * slotsPerBlock).toInt()
                    combinedTotalSlots += (totalBlocks * slotsPerBlock).toInt()
                }
            } else {
                idleChunks = mapToStrIfNull(metric.getIdleChunks(), null)
                if (Objects.nonNull(metric.getTotalChunks()) && Objects.nonNull(metric.getIdleChunks())) {
                    idleRate = rate(metric.getIdleChunks(), metric.getTotalChunks())

                    combinedIdleSlots += metric.getIdleChunks() * slotsPerChunk
                    combinedTotalSlots += metric.getTotalChunks() * slotsPerChunk
                }
            }
            globalPoolFormat.addData(identity.getFullIdentity(), newChunkCount, totalChunks, idleChunks, idleRate)
        }

        val localPoolFormat: TextTableFormat = textTableFormat(log, marker)
            .setTitles("identity", "claimLinkCount", "reallocateBlockCount", "totalBlocks", "idleBlocks", "idleRate")
        val localEntryList: List<Map.Entry<ScopedIdentity, LocalPoolMetric>> = localPoolMetrics.entries.stream()
            .sorted(java.util.Map.Entry.comparingByKey<ScopedIdentity, LocalPoolMetric>(comparator))
            .collect<List<Map.Entry<ScopedIdentity, LocalPoolMetric>>, Any>(
                Collectors.toList<Map.Entry<ScopedIdentity, LocalPoolMetric>>()
            )
        for ((identity, metric) in localEntryList) {
            var idleBlocks: String?
            var idleRate: String? = null
            val slotsOfIdleFirstBlock: Int = metric.getSlotsOfIdleFirstBlock()
            val totalBlocks: String = mapToStrIfNull(metric.getTotalBlocks(), null)
            val claimLinkCount: String = mapToStrIfNull(metric.getClaimLinkCount(), null)
            val reallocateBlockCount: String = mapToStrIfNull(metric.getReallocateBlockCount(), null)

            if (null != slotsOfIdleFirstBlock && slotsOfIdleFirstBlock > 0) {
                val fullIdleBlocks = ObjectUtils.defaultIfNull(metric.getIdleBlocks(), 0) - 1
                idleBlocks =
                    (if (fullIdleBlocks <= 0) "" else fullIdleBlocks.toString() + "blocks + ") + slotsOfIdleFirstBlock + "slots"
                if (Objects.nonNull(metric.getTotalBlocks())) {
                    val totalSlots = metric.getTotalBlocks() as Long * slotsPerBlock
                    val idleSlots: Long = slotsOfIdleFirstBlock + slotsPerBlock.toLong() *
                            (if (Objects.nonNull(metric.getIdleBlocks())) metric.getIdleBlocks() - 1 else 0)
                    idleRate = rate(idleSlots, totalSlots)

                    combinedIdleSlots += idleSlots.toInt()
                }
            } else {
                idleBlocks = mapToStrIfNull(metric.getIdleBlocks(), null)
                if (Objects.nonNull(metric.getTotalBlocks()) && Objects.nonNull(metric.getIdleBlocks())) {
                    idleRate = rate(metric.getIdleBlocks(), metric.getTotalBlocks())

                    combinedIdleSlots += metric.id * slotsPerBlock
                }
            }
            localPoolFormat.addData(
                identity.fullIdentity,
                claimLinkCount,
                reallocateBlockCount,
                totalBlocks,
                idleBlocks,
                idleRate
            )
        }

        val coteriePoolFormat: TextTableFormat = textTableFormat(log, marker)
            .setTitles("identity", "claimSlotsCount", "alertTotalSlots").setFullWidthGate(0)
        val coterieEntryList: List<Map.Entry<ScopedIdentity, CoteriePoolMetric>> = coteriePoolMetrics.entries.stream()
            .sorted(java.util.Map.Entry.comparingByKey(comparator))
            .collect<List<Map.Entry<ScopedIdentity, CoteriePoolMetric>>, Any>(
                Collectors.toList()
            )
        for ((identity, metric) in coterieEntryList) {
            val claimSlotsCount = mapToStrIfNull(metric.claimSlotsCount, null)
            val alertTotalSlots = mapToStrIfNull(metric.alertTotalSlots, null)

            coteriePoolFormat.addData(
                identity.fullIdentity,
                claimSlotsCount,
                alertTotalSlots
            )
        }

        log.info(marker, "poolMetric report start: +=================================================+")

        log.info(marker, "\nglobalPoolMetric: ")
        globalPoolFormat.print()

        log.info(marker, "\nlocalPoolMetric: ")
        localPoolFormat.print()

        log.info(marker, "\ncoteriePoolMetric: ")
        coteriePoolFormat.print()

        log.info(
            marker, "\nstrict slot usage: combinedTotalSlots={}, combinedIdleSlots={}, combinedIdleRate={}",
            combinedTotalSlots, combinedIdleSlots, rate(combinedIdleSlots.toLong(), combinedTotalSlots.toLong())
        )

        log.info(marker, "poolMetric report end: +===================================================+")
    }*/

    private class GlobalPoolMetric(val identity: ScopedIdentity) {
        var idleChunks: Int? = null
        var totalChunks: Int? = null
        var blocksOfIdleFirstChunk: Int? = null
        var newChunkCount = AtomicLong()
    }

    private class LocalPoolMetric(private val identity: ScopedIdentity) {
        var idleBlocks: Int? = null
        var totalBlocks: Int? = null
        var slotsOfIdleFirstBlock: Int? = null
        val claimLinkCount = AtomicLong()
        val reallocateBlockCount = AtomicLong()
    }

    private class CoteriePoolMetric(private val identity: ScopedIdentity) {
        var alertTotalSlots: Int? = null
        val claimSlotsCount = AtomicLong()
    }

    companion object {
        val log = LoggerFactory.getLogger(PoolMetric::class.java)
    }
}
