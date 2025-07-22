package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.component

import org.apache.hc.client5.http.HttpRequestRetryStrategy
import org.apache.hc.client5.http.protocol.HttpClientContext
import org.apache.hc.client5.http.utils.DateUtils
import org.apache.hc.core5.concurrent.CancellableDependency
import org.apache.hc.core5.http.*
import org.apache.hc.core5.http.protocol.HttpContext
import org.apache.hc.core5.util.Args
import org.apache.hc.core5.util.TimeValue
import java.io.IOException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.util.*

open class CustomHttpRequestRetryStrategy protected constructor(
    maxRetries: Int,
    defaultRetryInterval: TimeValue,
    clazzes: Collection<Class<out IOException>>,
    codes: Collection<Int>
) : HttpRequestRetryStrategy {
    private val maxRetries: Int

    private val retriableCodes: Set<Int>

    private val defaultRetryInterval: TimeValue

    private val nonRetriableIOExceptionClasses: Set<Class<out IOException>>

    init {
        Args.notNegative(maxRetries, "maxRetries")
        Args.notNegative(defaultRetryInterval.duration, "defaultRetryInterval")
        this.maxRetries = maxRetries
        this.retriableCodes = HashSet(codes)
        this.defaultRetryInterval = defaultRetryInterval
        this.nonRetriableIOExceptionClasses = HashSet(clazzes)
    }

    constructor(
        maxRetries: Int = 1,
        defaultRetryInterval: TimeValue = TimeValue.ofSeconds(1L)
    ) : this(
        maxRetries, defaultRetryInterval,
        listOf(
            ExplicitlyTerminateIOException::class.java,
            UnknownHostException::class.java,
            NoRouteToHostException::class.java
        ),
        listOf<Int>(
            HttpStatus.SC_REQUEST_TIMEOUT, HttpStatus.SC_TOO_MANY_REQUESTS,
            HttpStatus.SC_BAD_GATEWAY, HttpStatus.SC_GATEWAY_TIMEOUT
        )
    )

    override fun retryRequest(
        request: HttpRequest,
        exception: IOException,
        execCount: Int,
        context: HttpContext
    ): Boolean {
        //val identity = genIdentity(request)
        //if (StringUtils.isBlank(identity)) {
            //identity = "request"
        //}

        val finalMaxRetries = getMaxRetries(context)

        if (execCount > finalMaxRetries) {
            //log.info("{} retry over max retries({})", identity, finalMaxRetries)
            return false
        }

        if (nonRetriableIOExceptionClasses.contains(exception.javaClass)) {
            //log.info("{} match nonRetriableIOExceptionClasses({})", identity, exception.javaClass.toString())
            return false
        } else {
            for (rejectException in this.nonRetriableIOExceptionClasses) {
                if (rejectException.isInstance(exception)) {
                    //log.info("{} match nonRetriableIOExceptionClasses({})", identity, exception.javaClass.toString())
                    return false
                }
            }
        }

        if (request is CancellableDependency && (request as CancellableDependency).isCancelled) {
            //log.info("{} is cancelled", identity)
            return false
        }

        val idempotent = handleAsIdempotent(request)
        //if (idempotent) {
            //log.info(String.format("第%d次重试 %s", execCount, identity), exception)
        //}
        return idempotent
    }

    override fun retryRequest(
        response: HttpResponse,
        execCount: Int,
        context: HttpContext
    ): Boolean {

        //var identity: String?
        //val clientContext = HttpClientContext.adapt(context)
        //val request = clientContext.request
        //identity = genIdentity(request)
        //if (identity?.isBlank() == true) {
            //identity = "response"
        //}
        val finalMaxRetries = getMaxRetries(context)

        if (execCount > finalMaxRetries) {
            //log.info("{} retry over max retries({})", identity, finalMaxRetries)
            return false
        }

        //val responseCode = response.code
        //if (responseCode != HttpStatus.SC_SUCCESS) {
            //log.warn("{} statusCode={}", identity, responseCode)
        //}

        if (retriableCodes.contains(response.code)) {
            //log.info("{} statusCode={} match retriableCodes", identity, responseCode)
            //log.info("第{}次重试 {}", execCount, identity)
            return true
        }

        // eg. HttpStatus.SC_SERVICE_UNAVAILABLE, HttpStatus.SC_REQUEST_TOO_LONG
        val retryAfter = getRetryAfterFormHeader(response)
        if (TimeValue.isPositive(retryAfter)) {
            context.setAttribute(retryAfterKey, retryAfter)
            //log.info("{} retryAfter={}", identity, retryAfter)
            //log.info("第{}次重试 {}", execCount, identity)
            return true
        }

        return false
    }

    override fun getRetryInterval(
        response: HttpResponse,
        execCount: Int,
        context: HttpContext
    ): TimeValue {
        var retryAfter: TimeValue
        val clientContext = HttpClientContext.castOrCreate(context)
        retryAfter = clientContext.getAttribute(retryAfterKey, TimeValue::class.java)
        clientContext.removeAttribute(retryAfterKey)
        if (TimeValue.isPositive(retryAfter)) {
            return retryAfter
        }
        retryAfter = getRetryAfterFormHeader(response)!!
        if (TimeValue.isPositive(retryAfter)) {
            return retryAfter
        }
        return this.defaultRetryInterval
    }

    private fun getRetryAfterFormHeader(response: HttpResponse): TimeValue? {
        Objects.requireNonNull(response)

        var retryAfter: TimeValue? = null
        val header = response.getFirstHeader(HttpHeaders.RETRY_AFTER)
        if (header != null) {
            val value = header.value
            try {
                retryAfter = TimeValue.ofSeconds(value.toLong())
            } catch (_: NumberFormatException) {
                val retryAfterDate = DateUtils.parseStandardDate(value)
                if (retryAfterDate != null) {
                    retryAfter = TimeValue.ofMilliseconds(retryAfterDate.toEpochMilli() - System.currentTimeMillis())
                }
            }

            if (TimeValue.isPositive(retryAfter)) {
                return retryAfter
            }
        }
        return null
    }

    private fun getMaxRetries(context: HttpContext?): Int {
        var maxRetries = 20
        if (null != context) {
            val clientContext = HttpClientContext.castOrCreate(context)
            maxRetries = clientContext.getAttribute(
                maxRetriesKey, Int::class.javaObjectType
            )
        }
        return if (maxRetries >= 0) maxRetries else this.maxRetries
    }

    private fun handleAsIdempotent(request: HttpRequest): Boolean {
        return Method.isIdempotent(request.method)
    }

    companion object {

        @Suppress("ConstPropertyName")
        const val maxRetriesKey: String = "http.maxRetries"

        @Suppress("ConstPropertyName")
        const val retryAfterKey: String = "http.retryAfter"

        fun setMaxRetries(context: HttpContext, maxRetries: Int) {
            context.setAttribute(maxRetriesKey, maxRetries)
        }
    }
}
