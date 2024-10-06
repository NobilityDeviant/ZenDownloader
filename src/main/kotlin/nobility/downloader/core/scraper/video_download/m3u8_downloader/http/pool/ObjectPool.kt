package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.pool

import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Preconditions
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.IntUnaryOperator

open class ObjectPool<T>(
    poolIdentity: String,
    private val poolConfig: PoolConfig,
    private val poolMetric: PoolMetric,
    private val pooledObjFactory: PooledObjFactory<T>
) {
    private val globalPools: Array<GlobalPool<T>>
    private val lastIdx: AtomicInteger = AtomicInteger(-1)
    private val localPoolTLS: ThreadLocal<LocalPool<T>?> = ThreadLocal<LocalPool<T>?>()

    init {

        val type: Class<T> = pooledObjFactory.type
        val objectPoolIdentity = ScopedIdentity(poolIdentity)
        val globalPoolCount: Int = poolConfig.globalPoolCount()
        Preconditions.checkArgument(
            (globalPoolCount and -globalPoolCount) == globalPoolCount,
            "globalPoolCount is not a power of 2: %d", globalPoolCount
        )

        @Suppress("UNCHECKED_CAST")
        val pools: Array<GlobalPool<T>> = java.lang.reflect.Array.newInstance(
            GlobalPool::class.java,
            globalPoolCount
        ) as Array<GlobalPool<T>>
        for (i in 0 until globalPoolCount) {
            val identity = type.simpleName + "-GlobalPool-" + i
            val scopedIdentity = ScopedIdentity(identity, objectPoolIdentity)
            pools[i] = GlobalPool(
                scopedIdentity,
                pooledObjFactory,
                poolConfig,
                this.poolMetric
            )
        }
        this.globalPools = pools
    }

    private fun localPoolInitial(): LocalPool<T> {
        val slotsPerLink: Int = poolConfig.slotsPerLink()
        val globalPool: GlobalPool<T> = selectGlobalPool()
        val blocksPerReallocate: Int = poolConfig.blocksPerReallocate()
        val slotsOfInitialCoterie: Int = poolConfig.slotsOfInitialCoterie()
        val identity = Thread.currentThread().name + "-LocalPool"

        // lock op
        val blocks: List<Block<T>> = globalPool.allocateBlock(poolConfig.blocksOfInitialLocalPool())

        return LocalPool(
            ScopedIdentity(identity, globalPool.scopedIdentity), globalPool,
            blocksPerReallocate, slotsPerLink, slotsOfInitialCoterie, pooledObjFactory, blocks, poolMetric
        )
    }

    private fun selectGlobalPool(): GlobalPool<T> {
        // round-robin
        val cur: Int = lastIdx.incrementAndGet()
        return globalPools[cur and (globalPools.size - 1)]
    }

    fun allocateCoterie(identity: ScopedIdentity): CoteriePool<T> {
        val localPool: LocalPool<T> = localPool

        val claimPlannerOfCoterie: IntUnaryOperator = poolConfig.slotsClaimPlannerOfCoterie()
        return localPool.allocateCoterie(identity, claimPlannerOfCoterie)
    }

    val localPool: LocalPool<T>
        get() {
            var localPool: LocalPool<T>? = localPoolTLS.get()
            if (null == localPool) {
                localPool = localPoolInitial()
                localPoolTLS.set(localPool)
            }
            return localPool
        }

    fun destroyLocalPool() {
        val localPool: LocalPool<T>? = localPoolTLS.get()
        if (null != localPool) {
            try {
                localPool.destroy()
            } finally {
                localPoolTLS.remove()
            }
        }
    }


    fun destroy() {
        for (globalPool in this.globalPools) {
            globalPool.destroy()
        }
    }

    fun printMetrics() {
        //poolMetric.printMetrics()
    }
}
