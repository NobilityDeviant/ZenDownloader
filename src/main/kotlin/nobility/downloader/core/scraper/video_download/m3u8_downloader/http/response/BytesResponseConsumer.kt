package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.response

import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.component.UnexpectedHttpStatusException
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Utils.EMPTY_BIN
import org.apache.commons.lang3.StringUtils
import org.apache.hc.core5.concurrent.FutureCallback
import org.apache.hc.core5.http.*
import org.apache.hc.core5.http.nio.AsyncResponseConsumer
import org.apache.hc.core5.http.nio.CapacityChannel
import org.apache.hc.core5.http.protocol.HttpContext
import org.apache.hc.core5.util.ByteArrayBuffer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.min

class BytesResponseConsumer(identity: String) : AsyncResponseConsumer<ByteBuffer> {

    private val identity: String = StringUtils.defaultIfBlank(identity, "bytesConsume")
    private var buffer: ByteArrayBuffer? = null
    private var futureCallback: FutureCallback<ByteBuffer>? = null

    @Throws(HttpException::class, IOException::class)
    override fun consumeResponse(
        response: HttpResponse,
        entityDetails: EntityDetails?,
        context: HttpContext,
        resultCallback: FutureCallback<ByteBuffer>
    ) {
        this.futureCallback = resultCallback

        if (null == entityDetails) {
            resultCallback.completed(EMPTY_BIN)
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

        var contentLength = 8192L
        val header = response.getFirstHeader(HttpHeaders.CONTENT_LENGTH)
        if (null != header) {
            val value = header.value
            if (StringUtils.isNumeric(value)) {
                contentLength = value.toLong()
            }
        }

        var arrayBuffer: ByteArrayBuffer? = null
        val buffer = buffer
        if (buffer != null) {
            // retry
            val curBuffer = buffer
            curBuffer.clear()
            if (curBuffer.capacity() >= contentLength) {
                arrayBuffer = curBuffer
            }
        }

        if (arrayBuffer == null) {
            contentLength = min(contentLength.toDouble(), (16 * 1024).toDouble()).toLong()
            arrayBuffer = ByteArrayBuffer(contentLength.toInt())
        }

        this.buffer = arrayBuffer
    }

    @Throws(HttpException::class, IOException::class)
    override fun informationResponse(response: HttpResponse, context: HttpContext) {
        log.info("{} get informationResponse: {}", identity, response.code)
    }

    override fun failed(cause: Exception) {
    }

    @Throws(IOException::class)
    override fun updateCapacity(capacityChannel: CapacityChannel) {
        capacityChannel.update(Int.MAX_VALUE)
    }

    @Throws(IOException::class)
    override fun consume(src: ByteBuffer) {
        if (src.hasArray()) {
            buffer?.append(src.array(), src.arrayOffset() + src.position(), src.remaining())
            src.position(src.limit())
        } else {
            while (src.hasRemaining()) {
                buffer?.append(src.get().toInt())
            }
        }
    }

    @Throws(HttpException::class, IOException::class)
    override fun streamEnd(trailers: List<Header>?) {
        val buffer = buffer
        if (buffer != null) {
            futureCallback?.completed(
                ByteBuffer.wrap(
                    buffer.array(),
                    0,
                    buffer.length()
                )
            )
        } else {
            futureCallback?.failed(Exception("ByteArrayBuffer is null."))
        }
    }

    override fun releaseResources() {
        buffer?.clear()
        buffer = null
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(BytesResponseConsumer::class.java)
    }
}
