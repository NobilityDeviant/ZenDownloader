package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.response

import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.component.UnexpectedHttpStatusException
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.response.sink.SinkHandler
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.CollUtil
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Utils.EMPTY_BIN
import org.apache.commons.lang3.StringUtils
import org.apache.hc.core5.concurrent.FutureCallback
import org.apache.hc.core5.http.*
import org.apache.hc.core5.http.nio.AsyncResponseConsumer
import org.apache.hc.core5.http.nio.CapacityChannel
import org.apache.hc.core5.http.protocol.HttpContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class FileResponseConsumer(
    private val filePath: Path,
    var identity: String,
    private val sinkHandler: SinkHandler,
    private val fileDownloadPostProcessor: FileDownloadPostProcessor
) : AsyncResponseConsumer<Path> {

    private val readBytes = AtomicLong(0)
    private val started = AtomicBoolean(false)
    private val selfCompleteFuture = CompletableFuture<Void?>()
    // ------- non-final  ------//
    private var contentLength: Long? = null

    private var futureCallback: FutureCallback<Path>? = null
    private var sinkFutures: MutableList<CompletableFuture<Void?>>

    init {
        identity = StringUtils.defaultIfBlank(
            identity,
            String.format("download %s", filePath.fileName)
        )
        this.sinkFutures = CollUtil.newArrayList(selfCompleteFuture)
    }

    @Throws(HttpException::class, IOException::class)
    override fun consumeResponse(
        response: HttpResponse,
        entityDetails: EntityDetails?,
        context: HttpContext,
        resultCallback: FutureCallback<Path>
    ) {
        this.futureCallback = resultCallback

        if (null == entityDetails) {
            resultCallback.completed(this.filePath)
            log.warn("{} entityDetails is null", identity)
            return
        }

        val code = response.code
        if (code >= HttpStatus.SC_CLIENT_ERROR) {
            UnexpectedHttpStatusException.throwException(
                String.format(
                    "UnexpectedHttpStatus: %s code=%s",
                    identity,
                    code
                )
            )
        }

        val contentLenHeader = response.getFirstHeader(HttpHeaders.CONTENT_LENGTH)
        if (null != contentLenHeader) {
            val value = contentLenHeader.value
            if (StringUtils.isNumeric(value)) {
                this.contentLength = value.toLong()
            }
        }

        if (started.get()) {
            log.warn("consumeResponse retry: identity={}, readBytes={}", identity, readBytes.get())
            this.sinkFutures = CollUtil.newArrayList(selfCompleteFuture)
            sinkHandler.init(this.sinkFutures, true)
            readBytes.set(0)
            fileDownloadPostProcessor.startDownload(contentLength, true)
        } else {
            sinkHandler.init(this.sinkFutures, false)

            started.set(true)
            fileDownloadPostProcessor.startDownload(contentLength, false)
        }
    }

    @Throws(HttpException::class, IOException::class)
    override fun informationResponse(response: HttpResponse, context: HttpContext) {
        log.info("{} get informationResponse: {}", identity, response.code)
    }

    override fun failed(cause: Exception) {
        selfCompleteFuture.completeExceptionally(cause)
    }

    @Throws(IOException::class)
    override fun updateCapacity(capacityChannel: CapacityChannel) {
        capacityChannel.update(Int.MAX_VALUE)
    }

    @Throws(IOException::class)
    override fun consume(src: ByteBuffer) {
        var size: Int
        if ((src.remaining().also { size = it }) <= 0) {
            return
        }
        sinkFutures.removeIf { f: CompletableFuture<Void?> -> f.isDone && !f.isCompletedExceptionally }
        sinkHandler.doSink(src, false)
        readBytes.getAndAdd(size.toLong())
        fileDownloadPostProcessor.afterReadBytes(size, false)
    }

    @Throws(HttpException::class, IOException::class)
    override fun streamEnd(trailers: List<Header>?) {
        val bytes = readBytes.get()
        if (null != contentLength && contentLength != bytes) {
            log.warn("writtenBytes({}) != contentLength({}): {} ", bytes, contentLength, identity)
        }
        sinkHandler.doSink(EMPTY_BIN, true)
        fileDownloadPostProcessor.afterReadBytes(0, true)
        selfCompleteFuture.complete(null)
        futureCallback?.completed(this.filePath)
    }

    override fun releaseResources() {
    }

    @Throws(IOException::class)
    fun dispose() {
        sinkHandler.dispose()
    }

    fun getSinkFutures(): MutableList<CompletableFuture<Void?>> {
        return this.sinkFutures
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(FileResponseConsumer::class.java)
    }
}
