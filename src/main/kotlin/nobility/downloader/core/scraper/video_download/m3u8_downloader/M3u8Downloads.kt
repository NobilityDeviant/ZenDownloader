package nobility.downloader.core.scraper.video_download.m3u8_downloader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nobility.downloader.core.Core
import nobility.downloader.core.scraper.video_download.m3u8_downloader.core.*
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.HttpRequestManager
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.config.HttpRequestManagerConfig
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.response.FileDownloadOptions
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.CollUtil
import nobility.downloader.utils.FrogLog
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.function.IntSupplier
import java.util.function.LongSupplier
import kotlin.math.ceil

object M3u8Downloads {

    val log: Logger = LoggerFactory.getLogger(M3u8Downloads::class.java)

    suspend fun download(
        managerConfig: HttpRequestManagerConfig,
        download: M3u8Download
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val optionsSelector = FixedDownloadNumberOptionsSelector(
                download,
                managerConfig
            )
            var m3u8Executor: M3u8Executor? = null
            var m3u8Complete: CompletableFuture<*>? = null
            var stopped = false
            try {
                m3u8Executor = executorInstance(managerConfig, optionsSelector)
                launch {
                    while (!m3u8Executor.executor.isTerminated) {
                        if (!Core.child.isRunning) {
                            stopped = true
                            try {
                                m3u8Complete?.cancel(true)
                                FrogLog.info("Shutdown m3u8Executor gracefully.")
                            } catch (e: Exception) {
                                FrogLog.error(
                                    "Failed to shutdown m3u8Executor.",
                                    e
                                )
                            }
                            break
                        }
                    }
                }
                m3u8Complete = m3u8Executor.execute(download)
                m3u8Complete.await()
            } catch (ex: Exception) {
                log.error(ex.message, ex)
            } finally {
                delay(1000)
                m3u8Executor?.shutdownAwaitMills(2000)
            }
            return@withContext stopped
        }
    }

    suspend fun download(
        managerConfig: HttpRequestManagerConfig,
        downloads: List<M3u8Download>
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val optionsSelector = FixedDownloadNumberOptionsSelector(
                downloads,
                managerConfig
            )
            var m3u8Executor: M3u8Executor? = null
            var m3u8Complete: CompletableFuture<*>? = null
            var stopped = false
            try {
                m3u8Executor = executorInstance(managerConfig, optionsSelector)
                launch {
                    while (!m3u8Executor.executor.isTerminated) {
                        if (!Core.child.isRunning) {
                            stopped = true
                            try {
                                m3u8Complete?.cancel(true)
                                FrogLog.info("Shutdown m3u8Executor gracefully.")
                            } catch (e: Exception) {
                                FrogLog.error(
                                    "Failed to shutdown m3u8Executor.",
                                    e
                                )
                            }
                            break
                        }
                    }
                }
                m3u8Complete = m3u8Executor.execute(downloads)
                m3u8Complete.await()
            } catch (ex: Exception) {
                log.error(ex.message, ex)
            } finally {
                delay(1000)
                m3u8Executor?.shutdownAwaitMills(2000)
            }
            return@withContext stopped
        }
    }

    private fun executorInstance(
        managerConfig: HttpRequestManagerConfig = HttpRequestManagerConfig.DEFAULT,
        optionsSelector: TsDownloadOptionsSelector = TsDownloadOptionsSelector
            .PlainTsDownloadOptionsSelector.optionsSelector(
                ifAsyncSink = true,
                useBufferPool = true
        )
    ): M3u8Executor {
        return M3u8Executor(
            HttpRequestManager.getInstance(managerConfig),
            optionsSelector
        )
    }

    /**
     * fixed number of M3u8Downloads(especially when there's only one) can optimize parameters
     */
    private class FixedDownloadNumberOptionsSelector(
        downloads: List<M3u8Download>,
        managerConfig: HttpRequestManagerConfig
    ) : TsDownloadOptionsSelector {

        constructor(
            download: M3u8Download,
            managerConfig: HttpRequestManagerConfig
        ): this(
            listOf(download),
            managerConfig
        )

        private var optionsSnapshot: OptionsSnapshot
        private val managerConfig: HttpRequestManagerConfig

        init {
            val asyncSinkBoundary = 8
            val downloadSize = downloads.size
            if (downloadSize >= asyncSinkBoundary) {
                log.info("downloadSize >= asyncSinkBoundary, use AsyncSink: {}", downloadSize)
                this.optionsSnapshot = OptionsSnapshot(
                    downloads,
                    ifAsyncSink = true,
                    useBufferPool = false
                )
            } else {
                this.optionsSnapshot = OptionsSnapshot(downloads)
            }
            this.managerConfig = managerConfig
        }

        override fun getDownloadOptionsInternal(
            m3u8Download: M3u8Download,
            tsDownloads: List<TsDownload>
        ): FileDownloadOptions {
            val optionsSnapshot = this.optionsSnapshot
            var ifAsyncSink = optionsSnapshot.ifAsyncSink
            var useBufferPool = optionsSnapshot.useBufferPool
            if (ifAsyncSink && useBufferPool) {
                return FileDownloadOptions.getInstance(
                    ifAsyncSink = true,
                    useBufferPool = true
                )
            }

            synchronized(this) {
                ifAsyncSink = optionsSnapshot.ifAsyncSink
                useBufferPool = optionsSnapshot.useBufferPool
                if (ifAsyncSink && useBufferPool) {
                    return FileDownloadOptions.getInstance(
                        ifAsyncSink = true,
                        useBufferPool = true
                    )
                }

                ifAsyncSink = ifAsyncSink || worthAsyncSink(optionsSnapshot.allRouteCount,
                    { optionsSnapshot.instantReadingAndRemainedCount },
                    m3u8Download,
                    tsDownloads
                )

                useBufferPool = useBufferPool || worthUseBuffer(
                    { optionsSnapshot.remainedRouteCount },
                    { optionsSnapshot.remainedDownloads },
                    { optionsSnapshot.maxRouteSizeOfRemainedDownloads.size },
                    m3u8Download, tsDownloads
                )

                val result = FileDownloadOptions.getInstance(
                    ifAsyncSink,
                    useBufferPool
                )
                optionsSnapshot.record(m3u8Download, result)
                return result
            }
        }

        private fun worthAsyncSink(
            allRouteCount: Int,
            instantRemainedTsCount: LongSupplier,
            m3u8Download: M3u8Download,
            tsDownloads: List<TsDownload>
        ): Boolean {
            val tsCount = tsDownloads.size
            val identity = m3u8Download.identity
            val ioThreads = managerConfig.ioThreads
            val maxConnPerRoute = managerConfig.maxConnPerRoute

            val maxConnPerThread = allRouteCount * ceil(maxConnPerRoute * 1.0 / ioThreads).toInt()
            val threadNetworkIOIsBusy = maxConnPerThread >= 16
            if (threadNetworkIOIsBusy) {
                log.info(
                    "worthAsyncSink because of threadNetworkIOIsBusy, " +
                            "identity={} maxConnPerThread={}", identity, maxConnPerThread
                )
                return true
            }

            val remainedTsCountAsLong = instantRemainedTsCount.asLong
            val tooManyDiskIOTimes = tsCount + remainedTsCountAsLong >= maxConnPerRoute * 10L
            if (tooManyDiskIOTimes) {
                log.info(
                    "worthAsyncSink because of tooManyDiskIOTimes, " +
                            "identity={} tsCount={} remainedTsCount={}", identity, tsCount, remainedTsCountAsLong
                )
                return true
            }

            return false
        }

        private fun worthUseBuffer(
            remainedRouteCount: IntSupplier,
            remainedDownloadSize: IntSupplier,
            maxRouteSizeOfRemainedDownloads: IntSupplier,
            m3u8Download: M3u8Download, tsDownloads: List<TsDownload>
        ): Boolean {
            val tsCount = tsDownloads.size
            val identity = m3u8Download.identity
            val maxConnPerRoute = managerConfig.maxConnPerRoute
            val poolConfig = managerConfig.objectPoolConfig
            val remainedDownloadSizeAsInt = remainedDownloadSize.asInt

            val bufferReusable: Boolean
            val cheaperMemory: Boolean
            if (remainedDownloadSizeAsInt == 1) {
                val atLeastAllocatedSlots: Int = poolConfig.atLeastAllocatedSlots(maxConnPerRoute)

                bufferReusable = tsCount > maxConnPerRoute
                cheaperMemory = tsCount > atLeastAllocatedSlots

                val res = bufferReusable && cheaperMemory
                if (res) {
                    log.info(
                        "worthUseBuffer in the last download, identity={} tsCount={} " +
                                "maxConnPerRoute={}, atLeastAllocatedSlots={}",
                        identity,
                        tsCount,
                        maxConnPerRoute,
                        atLeastAllocatedSlots
                    )
                }
                return res
            }

            val remainedRouteCountAsInt = remainedRouteCount.asInt
            val maxRouteSizeOfRemainedDownloadsAsInt = maxRouteSizeOfRemainedDownloads.asInt

            bufferReusable = tsCount > maxConnPerRoute || maxRouteSizeOfRemainedDownloadsAsInt > 1
            cheaperMemory = (remainedDownloadSizeAsInt / remainedRouteCountAsInt) >= 2

            val res = bufferReusable && cheaperMemory
            if (res) {
                log.info(
                    "worthUseBuffer, identity={} tsCount={} maxConnPerRoute={}, " +
                            "maxRouteSizeOfRemainedDownloads={}, remainedDownloadSize={}, remainedRouteCount={}",
                    identity,
                    tsCount,
                    maxConnPerRoute,
                    maxRouteSizeOfRemainedDownloadsAsInt,
                    remainedDownloadSizeAsInt,
                    remainedRouteCountAsInt
                )
            }
            return res
        }

        private class OptionsSnapshot(
            allDownloads: List<M3u8Download>,
            ifAsyncSink: Boolean,
            useBufferPool: Boolean
        ) {
            val allRouteCount: Int
            @Volatile
            var ifAsyncSink: Boolean
            @Volatile
            var useBufferPool: Boolean
            private val downloadSnapshots: MutableList<M3u8Download>
            private val downloadRouteGroup: MutableMap<RouteGroupKey, MutableSet<M3u8Download>>

            constructor(
                allDownloads: List<M3u8Download>
            ) : this(allDownloads, false, false)

            init {
                // consider master list variantStreamInf ？

                val downloadRouteGroup = linkedMapOf<RouteGroupKey, MutableSet<M3u8Download>>()
                for (download in allDownloads) {
                    val key = RouteGroupKey(download.uri)
                    downloadRouteGroup.computeIfAbsent(key) {
                        mutableSetOf()
                    }.add(download)
                }

                this.ifAsyncSink = ifAsyncSink
                this.useBufferPool = useBufferPool
                this.downloadRouteGroup = downloadRouteGroup
                this.allRouteCount = downloadRouteGroup.size
                this.downloadSnapshots = CollUtil.newArrayList()

            }

            fun record(m3u8Download: M3u8Download, options: FileDownloadOptions?) {
                if (options == null) {
                    return
                }
                if (!this.ifAsyncSink) {
                    this.ifAsyncSink = options.ifAsyncSink()
                }
                if (!this.useBufferPool) {
                    this.useBufferPool = options.useBufferPool()
                }

                downloadSnapshots.add(m3u8Download)

                val routeGroupKey = RouteGroupKey(m3u8Download.uri)
                val m3u8Downloads = downloadRouteGroup[routeGroupKey]
                if (!m3u8Downloads.isNullOrEmpty()) {
                    m3u8Downloads.remove(m3u8Download)
                    if (m3u8Downloads.isEmpty()) {
                        downloadRouteGroup.remove(routeGroupKey)
                    }
                }
            }

            val instantReadingAndRemainedCount: Long
                get() {
                    var count: Long = 0
                    val iterator: MutableIterator<M3u8Download> = downloadSnapshots.iterator()
                    while (iterator.hasNext()) {
                        val download: M3u8Download = iterator.next()
                        val instantReadingAndRemainedCount: Long = download.instantReadingAndRemainedCount
                        if (instantReadingAndRemainedCount <= 0) {
                            iterator.remove()
                            continue
                        }
                        count += instantReadingAndRemainedCount
                    }
                    return count
                }

            val remainedDownloads: Int
                get() = downloadRouteGroup.values.filter {
                    it.isNotEmpty()
                }.sumOf { it.size }

            val remainedRouteCount: Int
                get() = downloadRouteGroup.entries
                    .filter { it.value.isNotEmpty() }
                    .map { 1 }
                    .sum()

            val maxRouteSizeOfRemainedDownloads: Set<Any>
                get() = if (downloadRouteGroup.isNotEmpty()) downloadRouteGroup.values
                    .maxBy { it.size } else mutableSetOf()

            @Suppress("UNUSED")
            private class RouteGroupKey(uri: URI) {
                private val port = uri.port
                private val host = uri.host
                private val schema = uri.scheme
            }

        }
    }

    private class BasicM3u8HttpHeader(
        override val name: String,
        override val value: String,
        override val requestType: M3u8HttpRequestType
    ) : M3u8HttpHeader

    interface M3u8HttpHeader {

        val name: String
        val value: String
        val requestType: M3u8HttpRequestType

        companion object {
            @Suppress("UNUSED")
            fun instance(
                name: String,
                value: String,
                requestType: M3u8HttpRequestType = M3u8HttpRequestType.REQ_FOR_M3U8_CONTENT
            ): M3u8HttpHeader {
                return BasicM3u8HttpHeader(name, value, requestType)
            }
        }
    }

}
