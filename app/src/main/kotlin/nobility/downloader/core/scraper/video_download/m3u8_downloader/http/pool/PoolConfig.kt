package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.pool

import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Preconditions
import java.util.function.IntUnaryOperator
import kotlin.math.ceil

class PoolConfig private constructor(
    private val slotsPerLink: Int,
    private val slotsPerBlock: Int,
    private val blocksPerChunk: Int,
    private val printMetric: Boolean,
    private val globalPoolCount: Int,
    private val blocksPerReallocate: Int,
    private val slotsOfInitialCoterie: Int,
    private val blocksOfInitialLocalPool: Int,
    private val chunksOfInitialGlobalPool: Int,
    private val slotsClaimPlannerOfCoterie: IntUnaryOperator
) {
    fun slotsPerLink(): Int {
        return this.slotsPerLink
    }

    fun slotsPerBlock(): Int {
        return this.slotsPerBlock
    }

    fun blocksPerChunk(): Int {
        return this.blocksPerChunk
    }

    fun ifPrintMetric(): Boolean {
        return printMetric
    }

    fun globalPoolCount(): Int {
        return this.globalPoolCount
    }

    fun blocksPerReallocate(): Int {
        return this.blocksPerReallocate
    }

    fun slotsOfInitialCoterie(): Int {
        return this.slotsOfInitialCoterie
    }

    fun blocksOfInitialLocalPool(): Int {
        return this.blocksOfInitialLocalPool
    }

    fun chunksOfInitialGlobalPool(): Int {
        return this.chunksOfInitialGlobalPool
    }

    fun slotsClaimPlannerOfCoterie(): IntUnaryOperator {
        return this.slotsClaimPlannerOfCoterie
    }

    private fun slotsPerChunk(): Int {
        return this.slotsPerBlock * this.blocksPerChunk
    }

    fun atLeastAllocatedSlots(atLeastCount: Int): Int {
        val slotsPerChunk = slotsPerChunk()
        val globalPoolCount = this.globalPoolCount
        val chunksOfInitialGlobalPool = this.chunksOfInitialGlobalPool
        val atLeastAllocatedSlots = if (chunksOfInitialGlobalPool > 0) {
            globalPoolCount * chunksOfInitialGlobalPool * slotsPerChunk
        } else {
            globalPoolCount * slotsPerChunk
        }

        if (atLeastAllocatedSlots >= atLeastCount) {
            return atLeastAllocatedSlots
        }

        return (atLeastAllocatedSlots + ceil(((atLeastCount - atLeastAllocatedSlots) * 1.0) / slotsPerChunk) * slotsPerChunk).toInt()
    }

    override fun toString(): String {
        return "PoolConfig{" +
                "slotsPerLink=" + slotsPerLink +
                ", slotsPerBlock=" + slotsPerBlock +
                ", blocksPerChunk=" + blocksPerChunk +
                ", printMetric=" + printMetric +
                ", globalPoolCount=" + globalPoolCount +
                ", blocksPerReallocate=" + blocksPerReallocate +
                ", slotsOfInitialCoterie=" + slotsOfInitialCoterie +
                ", blocksOfInitialLocalPool=" + blocksOfInitialLocalPool +
                ", chunksOfInitialGlobalPool=" + chunksOfInitialGlobalPool +
                ", slotsClaimPlannerOfCoterie=" + slotsClaimPlannerOfCoterie +
                '}'
    }

    class Builder {

        private var slotsPerLink = 8
        private var slotsPerBlock = 8
        private var blocksPerChunk = 8
        private var printMetric = false
        private var globalPoolCount = 1
        private var blocksPerReallocate = 1
        private var slotsOfInitialCoterie = 2
        private var blocksOfInitialLocalPool = 1
        private var chunksOfInitialGlobalPool = 0
        private var slotsClaimPlannerOfCoterie: IntUnaryOperator

        init {
            this.slotsClaimPlannerOfCoterie = IntUnaryOperator { 2 }
        }

        /**
         * Suggestion: slotsPerLink should be a little smaller or a little bigger than slotsPerBlock
         */
        fun slotsPerLink(slotsPerLink: Int): Builder {
            Preconditions.checkPositive(slotsPerLink, "slotsPerLink")
            this.slotsPerLink = slotsPerLink
            return this
        }

        fun slotsPerBlock(slotsPerBlock: Int): Builder {
            Preconditions.checkPositive(slotsPerBlock, "slotsPerBlock")
            this.slotsPerBlock = slotsPerBlock
            return this
        }

        fun blocksPerChunk(blocksPerChunk: Int): Builder {
            Preconditions.checkPositive(blocksPerChunk, "blocksPerChunk")
            this.blocksPerChunk = blocksPerChunk
            return this
        }

        fun printMetric(printMetric: Boolean): Builder {
            this.printMetric = printMetric
            return this
        }

        fun globalPoolCount(globalPoolCount: Int): Builder {
            Preconditions.checkPositive(globalPoolCount, "globalPoolCount")
            Preconditions.checkArgument(
                (globalPoolCount and -globalPoolCount) == globalPoolCount,
                "globalPoolCount is not a power of 2: %d", globalPoolCount
            )
            this.globalPoolCount = globalPoolCount
            return this
        }

        fun blocksPerReallocate(blocksPerReallocate: Int): Builder {
            Preconditions.checkPositive(blocksPerReallocate, "blocksPerReallocate")
            this.blocksPerReallocate = blocksPerReallocate
            return this
        }

        fun slotsOfInitialCoterie(slotsOfInitialCoterie: Int): Builder {
            Preconditions.checkNonNegative(slotsOfInitialCoterie, "slotsOfInitialCoterie")
            this.slotsOfInitialCoterie = slotsOfInitialCoterie
            return this
        }

        fun blocksOfInitialLocalPool(blocksOfInitialLocalPool: Int): Builder {
            Preconditions.checkNonNegative(blocksOfInitialLocalPool, "blocksOfInitialLocalPool")
            this.blocksOfInitialLocalPool = blocksOfInitialLocalPool
            return this
        }

        fun chunksOfInitialGlobalPool(chunksOfInitialGlobalPool: Int): Builder {
            Preconditions.checkNonNegative(chunksOfInitialGlobalPool, "chunksOfInitialGlobalPool")
            this.chunksOfInitialGlobalPool = chunksOfInitialGlobalPool
            return this
        }

        /**
         * slotsClaimPlannerOfCoterie's function value must be <= slotsPerLink and <= slotsPerBlock
         */
        fun slotsClaimPlannerOfCoterie(slotsClaimPlannerOfCoterie: IntUnaryOperator): Builder {
            this.slotsClaimPlannerOfCoterie = slotsClaimPlannerOfCoterie
            return this
        }

        fun build(): PoolConfig {
            val slotsPerLink = this.slotsPerLink
            val slotsPerBlock = this.slotsPerBlock
            val blocksPerChunk = this.blocksPerChunk
            val blocksPerReallocate = this.blocksPerReallocate
            val slotsOfInitialCoterie = this.slotsOfInitialCoterie
            val blocksOfInitialLocalPool = this.blocksOfInitialLocalPool

            // slotsClaimPlannerOfCoterie's function value also should be <= slotsPerLink and <= slotsPerBlock
            Preconditions.checkArgument(
                slotsOfInitialCoterie <= slotsPerLink && slotsOfInitialCoterie <= slotsPerBlock,
                "slotsOfInitialCoterie illegal, must be <= slotsPerLink and <= slotsPerBlock"
            )

            Preconditions.checkArgument(
                blocksPerReallocate <= blocksPerChunk,
                "blocksPerReallocate illegal, must be <= blocksPerChunk"
            )

            Preconditions.checkArgument(
                blocksOfInitialLocalPool <= blocksPerChunk,
                "blocksOfInitialLocalPool illegal, must be <= blocksPerChunk"
            )

            return PoolConfig(
                this.slotsPerLink,
                this.slotsPerBlock,
                this.blocksPerChunk,
                this.printMetric,
                this.globalPoolCount,
                this.blocksPerReallocate,
                this.slotsOfInitialCoterie,
                this.blocksOfInitialLocalPool,
                this.chunksOfInitialGlobalPool,
                this.slotsClaimPlannerOfCoterie
            )
        }
    }

    companion object {

        val DEFAULT: PoolConfig = custom().build()

        private fun custom(): Builder {
            return Builder()
        }

        fun copy(config: PoolConfig): Builder {
            return Builder()
                .slotsPerLink(config.slotsPerLink())
                .printMetric(config.ifPrintMetric())
                .slotsPerBlock(config.slotsPerBlock())
                .blocksPerChunk(config.blocksPerChunk())
                .globalPoolCount(config.globalPoolCount())
                .blocksPerReallocate(config.blocksPerReallocate())
                .slotsOfInitialCoterie(config.slotsOfInitialCoterie())
                .blocksOfInitialLocalPool(config.blocksOfInitialLocalPool())
                .chunksOfInitialGlobalPool(config.chunksOfInitialGlobalPool())
                .slotsClaimPlannerOfCoterie(config.slotsClaimPlannerOfCoterie())
        }
    }
}
