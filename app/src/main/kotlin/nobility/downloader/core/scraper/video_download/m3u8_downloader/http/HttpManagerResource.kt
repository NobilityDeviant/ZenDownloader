package nobility.downloader.core.scraper.video_download.m3u8_downloader.http

import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.component.CustomHttpRequestRetryStrategy
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.config.HttpRequestManagerConfig
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.CollUtil
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Preconditions
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.ThreadUtil
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.function.Try
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.collections4.ListUtils
import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.DefaultClientConnectionReuseStrategy
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient
import org.apache.hc.client5.http.impl.async.HttpAsyncClients
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder
import org.apache.hc.core5.concurrent.DefaultThreadFactory
import org.apache.hc.core5.function.Callback
import org.apache.hc.core5.http.config.Http1Config
import org.apache.hc.core5.pool.PoolConcurrencyPolicy
import org.apache.hc.core5.pool.PoolReusePolicy
import org.apache.hc.core5.reactor.IOReactorConfig
import org.apache.hc.core5.reactor.IOReactorStatus
import org.apache.hc.core5.util.TimeValue
import org.apache.hc.core5.util.Timeout
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.Volatile

internal class HttpManagerResource(
    private val managerConfig: HttpRequestManagerConfig,
    ioReactorTerminateCallBacks: List<IOReactorTerminateCallBack?>? = null
) {
    val bufferSize: Int = 8192 * 4

    private val lock = Any()

    private val ioReactorTerminateCallBacks: MutableList<IOReactorTerminateCallBack>

    @Volatile
    private var executor: ExecutorService? = null

    @Volatile
    private var heapBufferPool: ByteBufferPool? = null

    @Volatile
    private var directBufferPool: ByteBufferPool? = null

    @Volatile
    private var httpAsyncClientScope: HttpAsyncClientScope? = null

    init {
        this.ioReactorTerminateCallBacks = CollUtil.newArrayList(
            ListUtils.emptyIfNull<IOReactorTerminateCallBack>(ioReactorTerminateCallBacks)
        )

        this.ioReactorTerminateCallBacks.add { this.destroyByteBuffLocalPool() }
    }

    @Throws(Exception::class)
    fun shutdown() {
        synchronized(lock) {
            Optional.ofNullable<ExecutorService>(this.executor)
                .ifPresent { obj: ExecutorService -> obj.shutdown() }
            Optional.ofNullable<HttpAsyncClientScope>(this.httpAsyncClientScope).ifPresent { s: HttpAsyncClientScope ->
                Try.run { s.httpAsyncClient.close() }
                    .get()
            }

            Optional.ofNullable<ByteBufferPool>(this.heapBufferPool)
                .ifPresent { obj: ByteBufferPool -> obj.destroy() }
            Optional.ofNullable<ByteBufferPool>(this.directBufferPool)
                .ifPresent { obj: ByteBufferPool -> obj.destroy() }
        }

        Optional.ofNullable<ByteBufferPool>(this.heapBufferPool)
            .filter {
                managerConfig.objectPoolConfig.ifPrintMetric()
            }
            .ifPresent { obj: ByteBufferPool -> obj.printMetrics() }

        Optional.ofNullable<ByteBufferPool>(this.directBufferPool)
            .filter {
                managerConfig.objectPoolConfig.ifPrintMetric()
            }
            .ifPresent { obj: ByteBufferPool -> obj.printMetrics() }
    }

    @Throws(InterruptedException::class)
    fun awaitTermination(timeout: Long, unit: TimeUnit) {
        synchronized(lock) {
            Optional.ofNullable<ExecutorService>(this.executor)
                .ifPresent { e: ExecutorService ->
                    Try.of {
                        e.awaitTermination(
                            timeout,
                            unit
                        )
                    }.get()
                }
            Optional.ofNullable<HttpAsyncClientScope>(this.httpAsyncClientScope).ifPresent { s: HttpAsyncClientScope ->
                Try.run { s.httpAsyncClient.awaitShutdown(millsTimeValue(unit.toMillis(timeout))) }
                    .get()
            }
        }
    }

    private fun destroyByteBuffLocalPool() {
        val directBufferPool: ByteBufferPool? = this.directBufferPool

        directBufferPool?.destroyLocalPool()
        this.heapBufferPool?.destroyLocalPool()
    }

    private fun getHttpAsyncClientScope(): HttpAsyncClientScope {
        if (null == httpAsyncClientScope) {
            synchronized(lock) {
                if (null == httpAsyncClientScope) {
                    httpAsyncClientScope = buildClient()
                }
            }
        }
        return httpAsyncClientScope!!
    }

    val defaultRequestConfig: RequestConfig?
        get() = getHttpAsyncClientScope().defaultRequestConfig

    val httpClient: CloseableHttpAsyncClient
        get() {
            val httpAsyncClient: CloseableHttpAsyncClient = getHttpAsyncClientScope().httpAsyncClient
            if (IOReactorStatus.INACTIVE == httpAsyncClient.status) {
                httpAsyncClient.start()
            }
            return httpAsyncClient
        }

    fun getExecutor(): Executor? {
        if (null == executor) {
            synchronized(lock) {
                if (null == executor) {
                    executor = Executors.newFixedThreadPool(
                        managerConfig.executorThreads,
                        ThreadUtil.getThreadFactory("httpManager-executor", true)
                    )
                }
            }
        }
        return executor
    }

    fun getDirectBufferPool(): ByteBufferPool {
        if (null == directBufferPool) {
            synchronized(lock) {
                if (null == directBufferPool) {
                    this.directBufferPool = ByteBufferPool.newDirectBufferPool(
                        "httpManager",
                        bufferSize,
                        managerConfig.objectPoolConfig
                    )
                }
            }
        }
        return directBufferPool!!
    }

    fun getHeapBufferPool(): ByteBufferPool {
        if (null == heapBufferPool) {
            synchronized(lock) {
                if (null == heapBufferPool) {
                    this.heapBufferPool = ByteBufferPool.newHeapBufferPool(
                        "httpManager",
                        bufferSize,
                        managerConfig.objectPoolConfig
                    )
                }
            }
        }
        return heapBufferPool!!
    }

    private fun millsTimeOut(mills: Long): Timeout {
        return Timeout.ofMilliseconds(mills)
    }

    private fun millsTimeValue(mills: Long): TimeValue {
        return TimeValue.ofMilliseconds(mills)
    }

    private fun buildClient(): HttpAsyncClientScope {
        val schemePortResolver = DefaultSchemePortResolver.INSTANCE

        val connectionConfig = ConnectionConfig.custom()
            .setSocketTimeout(millsTimeOut(managerConfig.socketTimeoutMills))
            .setConnectTimeout(millsTimeOut(managerConfig.connectTimeoutMills))
            .build()

        val connectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
            .useSystemProperties()
            .setConnPoolPolicy(PoolReusePolicy.LIFO)
            .setSchemePortResolver(schemePortResolver)
            .setDefaultConnectionConfig(connectionConfig)
            .setMaxConnTotal(managerConfig.maxConnTotal)
            .setPoolConcurrencyPolicy(PoolConcurrencyPolicy.LAX)
            .setMaxConnPerRoute(managerConfig.maxConnPerRoute)
            .build()

        val ioReactorConfig = IOReactorConfig.custom()
            .setTcpNoDelay(true)
            .setSoKeepAlive(true)
            .setIoThreadCount(managerConfig.ioThreads)
            .setSoTimeout(millsTimeOut(managerConfig.socketTimeoutMills))
            .setSelectInterval(millsTimeValue(managerConfig.selectIntervalMills))
            .build()

        val reuseStrategy = DefaultClientConnectionReuseStrategy.INSTANCE

        val ioReactorExceptionCallback =
            Callback { _: Exception -> /*log.error("uncaught exception: " + ex.message, ex)*/ }

        val retryStrategy = CustomHttpRequestRetryStrategy(
            managerConfig.defaultMaxRetries, millsTimeValue(managerConfig.defaultRetryIntervalMills)
        )

        val userAgent = managerConfig.userAgent

        val requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(millsTimeOut(managerConfig.connectionRequestTimeoutMills))
            .build()

        //val routePlanner: HttpRoutePlanner
        //if (managerConfig.overrideSystemProxy()) {
          //  routePlanner = ContextualHttpRoutePlanner(DefaultRoutePlanner(schemePortResolver))
        //} else {
          //  val defaultProxySelector: ProxySelector = AccessController.doPrivileged<ProxySelector>(PrivilegedAction { ProxySelector.getDefault() } as PrivilegedAction<ProxySelector?>?)
            //routePlanner =
              //  ContextualHttpRoutePlanner(SystemDefaultRoutePlanner(schemePortResolver, defaultProxySelector))
        //}

        val http1Config = Http1Config.copy(Http1Config.DEFAULT).setBufferSize(bufferSize).build()

        val client = HttpAsyncClients.custom()
            .useSystemProperties()
            .setUserAgent(userAgent)
            .disableCookieManagement()
            .setHttp1Config(http1Config)
            //.setRoutePlanner(routePlanner)
            .setRetryStrategy(retryStrategy)
            .setIOReactorConfig(ioReactorConfig)
            .setDefaultRequestConfig(requestConfig)
            .setConnectionManager(connectionManager)
            .setSchemePortResolver(schemePortResolver)
            .setConnectionReuseStrategy(reuseStrategy)
            .setIoReactorExceptionCallback(ioReactorExceptionCallback)
            .setThreadFactory(
                if (CollectionUtils.isEmpty(ioReactorTerminateCallBacks)) null else ThreadFactoryWithCallback(
                    "httpclient-dispatch",
                    true
                )
            )
            .evictIdleConnections(millsTimeValue(managerConfig.connectionMaxIdleMills))
            .build()

        client.start()

        return HttpAsyncClientScope(requestConfig, client)
    }

    private data class HttpAsyncClientScope(
        val defaultRequestConfig: RequestConfig?,
        val httpAsyncClient: CloseableHttpAsyncClient
    )

    fun interface IOReactorTerminateCallBack {
        fun doFinally()
    }

    private class RunnableWithCallback(
        runnable: Runnable?,
        ioReactorTerminateCallBacks: List<IOReactorTerminateCallBack?>?
    ) : Runnable {
        private val runnable: Runnable = Preconditions.checkNotNull(runnable)

        private val ioReactorTerminateCallBacks: List<IOReactorTerminateCallBack> =
            ListUtils.emptyIfNull<IOReactorTerminateCallBack>(ioReactorTerminateCallBacks)

        override fun run() {
            try {
                runnable.run()
            } finally {
                for (callBack in ioReactorTerminateCallBacks) {
                    try {
                        callBack.doFinally()
                    } catch (_: Exception) {
                        //log.error("$callBack exec doFinally error", ex)
                    }
                }
            }
        }
    }

    private inner class ThreadFactoryWithCallback(
        namePrefix: String?,
        daemon: Boolean
    ) : DefaultThreadFactory(namePrefix, daemon) {

        override fun newThread(runnable: Runnable): Thread {
            val target = RunnableWithCallback(runnable, ioReactorTerminateCallBacks)
            return super.newThread(target)
        }
    }
}
