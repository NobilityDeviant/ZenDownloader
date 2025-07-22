package nobility.downloader.core.scraper.video_download.m3u8_downloader.http

import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.component.CustomHttpRequestRetryStrategy
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.config.HttpRequestConfig
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.config.HttpRequestManagerConfig
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.pool.ScopedIdentity
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.response.BytesResponseConsumer
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.response.FileDownloadOptions
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.response.FileDownloadPostProcessor
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.response.FileResponseConsumer
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.response.sink.*
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Utils.genIdentity
import org.apache.commons.lang3.StringUtils
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient
import org.apache.hc.client5.http.protocol.HttpClientContext
import org.apache.hc.core5.concurrent.FutureCallback
import org.apache.hc.core5.http.Method
import org.apache.hc.core5.http.nio.AsyncRequestProducer
import java.net.URI
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@Suppress("UNUSED")
class HttpRequestManager(
    private var managerConfig: HttpRequestManagerConfig? = null
) {
    private val state: AtomicReference<State>
    private val managerResource: HttpManagerResource

    init {
        if (null == managerConfig) {
            managerConfig = HttpRequestManagerConfig.DEFAULT
        }
        this.state = AtomicReference(State.ACTIVE)
        this.managerResource = HttpManagerResource(managerConfig!!)
        //log.info("managerConfig={}", managerConfig)
    }

    @Throws(Exception::class)
    fun shutdown() {
        if (state.compareAndSet(State.ACTIVE, State.SHUTDOWN)) {
            //log.info("shutdown HttpRequestManager")
            managerResource.shutdown()
        }
    }

    @Throws(InterruptedException::class)
    fun awaitTermination(timeout: Long, unit: TimeUnit) {
        managerResource.awaitTermination(timeout, unit)
    }

    fun getBytes(
        uri: URI,
        requestConfig: HttpRequestConfig?
    ): CompletableFuture<ByteBuffer> {

        checkState()
        val uriIdentity: String = genIdentity(uri)

        val clientContext = HttpClientContext.create()

        val requestProducer: AsyncRequestProducer =
            SimpleRequestProducer.create(getRequest(uri, requestConfig, clientContext))

        val responseConsumer = BytesResponseConsumer(uriIdentity)

        val future = CompletableFuture<ByteBuffer>()

        val futureCallback: FutureCallback<ByteBuffer> = object : FutureCallback<ByteBuffer> {
            override fun completed(result: ByteBuffer) {
                future.complete(result)
            }

            override fun failed(ex: Exception) {
                future.completeExceptionally(ex)
            }

            override fun cancelled() {
                future.cancel(false)
            }
        }
        httpClient.execute(
            requestProducer,
            responseConsumer,
            clientContext,
            futureCallback
        )
        return future
    }

    @JvmOverloads
    fun downloadFile(
        uri: URI,
        filePath: Path,
        parentIdentity: String? = null,
        requestConfig: HttpRequestConfig? = null,
        postProcessor: FileDownloadPostProcessor = FileDownloadPostProcessor.NOP
    ): CompletableFuture<Path> {
        return downloadFile(
            uri,
            filePath,
            parentIdentity,
            requestConfig,
            null,
            postProcessor
        )
    }

    @Suppress("SameParameterValue")
    private fun downloadFile(
        uri: URI,
        filePath: Path,
        parentIdentity: String?,
        requestConfig: HttpRequestConfig?,
        decryptionKey: DecryptionKey? = null,
        postProcessor: FileDownloadPostProcessor = FileDownloadPostProcessor.NOP
    ): CompletableFuture<Path> {
        return downloadFile(
            uri,
            filePath,
            parentIdentity,
            null,
            decryptionKey,
            requestConfig,
            postProcessor
        )
    }

    fun downloadFile(
        uri: URI,
        filePath: Path,
        parentIdentity: String?,
        options: FileDownloadOptions?,
        requestConfig: HttpRequestConfig?,
        postProcessor: FileDownloadPostProcessor = FileDownloadPostProcessor.NOP
    ): CompletableFuture<Path> {
        return downloadFile(
            uri,
            filePath,
            parentIdentity,
            options,
            null,
            requestConfig,
            postProcessor
        )
    }

    fun downloadFile(
        uri: URI,
        filePath: Path,
        parentIdentity: String?,
        options: FileDownloadOptions?,
        decryptionKey: DecryptionKey?,
        requestConfig: HttpRequestConfig?,
        postProcessor: FileDownloadPostProcessor = FileDownloadPostProcessor.NOP
    ): CompletableFuture<Path> {
        var mOptions = options
        var parentScope: ScopedIdentity? = null
        val uriIdentity: String = genIdentity(uri)
        if (StringUtils.isNotBlank(parentIdentity)) {
            parentScope = ScopedIdentity(parentIdentity!!)
        }

        val scopedIdentity = ScopedIdentity(uriIdentity, parentScope)
        val identity: String = scopedIdentity.fullIdentity

        var asyncSink: AsyncSink? = null
        val bufferProvider: BufferProvider
        var decipherable: Decipherable? = null
        mOptions = FileDownloadOptions.defaultOptionsIfNull(mOptions)

        if (mOptions.ifAsyncSink()) {
            asyncSink = AsyncSink(
                identity
            ) { executor!!.execute(it) }
        }

        if (null != decryptionKey) {
            decipherable = Decipherable(identity, decryptionKey)
        }

        if (mOptions.useBufferPool()) {
            val byteBufferPool: ByteBufferPool = if (null != decipherable) {
                heapBufferPool
            } else {
                directBufferPool
            }

            bufferProvider = if (null != asyncSink) {
                BufferProvider.coteriePoolBuffer(byteBufferPool, scopedIdentity)
            } else {
                BufferProvider.localPoolBuffer(byteBufferPool)
            }
        } else {
            bufferProvider = if (null != decipherable) {
                BufferProvider.plainHeapBuffer(managerResource.bufferSize)
            } else {
                BufferProvider.plainDirectBuffer(managerResource.bufferSize)
            }
        }

        val utilitySinkHandler = UtilitySinkHandler(
            filePath,
            bufferProvider,
            asyncSink,
            decipherable
        )
        return downloadFile(
            uri,
            filePath,
            identity,
            postProcessor,
            utilitySinkHandler,
            requestConfig
        )
    }

    private fun downloadFile(
        uri: URI,
        filePath: Path,
        identity: String,
        postProcessor: FileDownloadPostProcessor = FileDownloadPostProcessor.NOP,
        sinkHandler: SinkHandler,
        requestConfig: HttpRequestConfig?
    ): CompletableFuture<Path> {
        checkState()
        val clientContext = HttpClientContext.create()
        val requestProducer: AsyncRequestProducer =
            SimpleRequestProducer.create(getRequest(uri, requestConfig, clientContext))
        val responseConsumer = FileResponseConsumer(
            filePath,
            identity,
            sinkHandler,
            postProcessor
        )
        val downloadCompletedFuture = CompletableFuture<Path>()
        val futureCallback: FutureCallback<Path> = object : FutureCallback<Path> {
            override fun completed(result: Path) {
                val actionFutures = responseConsumer.getSinkFutures()
                actionFutures.removeIf { f: CompletableFuture<Void?> -> f.isDone && !f.isCompletedExceptionally }
                val future = CompletableFuture.allOf(*actionFutures.toTypedArray<CompletableFuture<*>>())
                future.whenComplete { _: Void?, th: Throwable? ->
                    var mThrowable = th
                    try {
                        responseConsumer.dispose()
                    } catch (ex: Exception) {
                        if (null != mThrowable) {
                            mThrowable.addSuppressed(ex)
                        } else {
                            mThrowable = ex
                        }
                    }
                    if (null != mThrowable) {
                        postProcessor.afterDownloadFailed()
                        downloadCompletedFuture.completeExceptionally(mThrowable)
                        //log.error(th.message, th)
                    } else {
                        postProcessor.afterDownloadComplete()
                        downloadCompletedFuture.complete(result)
                    }
                }
            }

            override fun failed(ex: Exception) {
                try {
                    responseConsumer.dispose()
                } catch (e: Exception) {
                    ex.addSuppressed(e)
                }
                //log.error(ex.message, ex)
                postProcessor.afterDownloadFailed()
                downloadCompletedFuture.completeExceptionally(ex)
            }

            override fun cancelled() {
                try {
                    responseConsumer.dispose()
                } catch (ex: Exception) {
                    //log.error(ex.message, ex)
                }
                postProcessor.afterDownloadFailed()
                downloadCompletedFuture.cancel(false)
            }
        }

        httpClient.execute(requestProducer, responseConsumer, clientContext, futureCallback)

        return downloadCompletedFuture
    }

    private fun checkState() {
        check(state.get() != State.SHUTDOWN) { "httpRequestManager already shutdown" }
    }

    private fun getRequest(
        uri: URI,
        requestConfig: HttpRequestConfig?,
        context: HttpClientContext
    ): SimpleHttpRequest {
        val request = SimpleHttpRequest.create(Method.GET, uri)
        if (null == requestConfig) {
            return request
        }

        val retryCount = requestConfig.retryCount
        CustomHttpRequestRetryStrategy.setMaxRetries(context, retryCount)
        val requestHeaderMap = requestConfig.requestHeaderMap
        if (!requestHeaderMap.isNullOrEmpty()) {
            for ((key, value) in requestHeaderMap) {
                request.addHeader(key, value)
            }
        }

        return request
    }

    private val directBufferPool: ByteBufferPool
        get() = managerResource.getDirectBufferPool()

    private val heapBufferPool: ByteBufferPool
        get() = managerResource.getHeapBufferPool()

    private val executor: Executor?
        get() = managerResource.getExecutor()

    private val defaultRequestConfig: RequestConfig?
        get() = managerResource.defaultRequestConfig

    private val httpClient: CloseableHttpAsyncClient
        get() = managerResource.httpClient

    internal enum class State {
        ACTIVE, SHUTDOWN,
    }

    companion object {
        val instance: HttpRequestManager
            get() = HttpRequestManager()

        fun getInstance(managerConfig: HttpRequestManagerConfig?): HttpRequestManager {
            return HttpRequestManager(managerConfig)
        }
    }
}
