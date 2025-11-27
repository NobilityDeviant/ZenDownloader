package nobility.downloader.core.scraper.video_download.m3u8_downloader.http

import kotlinx.coroutines.*
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.config.HttpRequestConfig
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.config.HttpRequestManagerConfig
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.response.FileDownloadPostProcessor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.buffer
import okio.sink
import okio.source
import java.net.URI
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import kotlin.io.use
import kotlin.math.min
import kotlin.random.Random

class HttpRequestManager(
    private val managerConfig: HttpRequestManagerConfig = HttpRequestManagerConfig.Companion.DEFAULT
) {

    private val state: AtomicReference<State> = AtomicReference(State.ACTIVE)

    private val client: OkHttpClient
    private val scope: CoroutineScope

    init {
        val cfg = managerConfig
        client = OkHttpClient.Builder()
            .connectTimeout(cfg.connectTimeoutMills, TimeUnit.MILLISECONDS)
            .readTimeout(cfg.socketTimeoutMills, TimeUnit.MILLISECONDS)
            .callTimeout((cfg.connectTimeoutMills + cfg.socketTimeoutMills), TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()

        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    fun shutdown() {
        if (state.compareAndSet(State.ACTIVE, State.SHUTDOWN)) {
            scope.cancel()
            try {
                client.dispatcher.executorService.shutdownNow()
            } catch (_: Exception) {}
            try {
                client.connectionPool.evictAll()
            } catch (_: Exception) {}
        }
    }

    private fun isShutdown(): Boolean {
        return state.get() == State.SHUTDOWN
    }

    suspend fun getBytes(
        uri: URI,
        requestConfig: HttpRequestConfig?
    ): ByteBuffer = withContext(Dispatchers.IO) {

        if (isShutdown()) {
            return@withContext ByteBuffer.allocate(0)
        }
        val headers = requestConfig?.requestHeaderMap
        val retries = requestConfig?.retryCount ?: managerConfig.defaultMaxRetries
        val backoffBase = managerConfig.defaultRetryIntervalMills

        retryWithBackoff(retries, backoffBase) {
            val rb = Request.Builder().url(uri.toString()).get()
            headers?.forEach { (k, v) -> rb.header(k, v.toString()) }
            rb.header(
                "User-Agent",
                managerConfig.userAgent
            )

            client.newCall(rb.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw RuntimeException("HTTP ${resp.code}")
                }
                val body: ResponseBody = resp.body ?: throw RuntimeException(
                    "Empty response body"
                )
                body.source().use { src ->
                    val buffer = Buffer()
                    src.readAll(buffer)
                    val arr = buffer.readByteArray()
                    return@retryWithBackoff arr
                }
            }
        }.let { byteArray ->
            ByteBuffer.wrap(byteArray)
        }
    }

    suspend fun downloadFile(
        uri: URI,
        filePath: Path,
        decryptionKey: DecryptionKey?,
        requestConfig: HttpRequestConfig?,
        postProcessor: FileDownloadPostProcessor = FileDownloadPostProcessor.Companion.NOP
    ): Path = withContext(Dispatchers.IO) {

        if (isShutdown()) {
            filePath.toFile().parentFile?.mkdirs()
            return@withContext filePath
        }

        val headers = requestConfig?.requestHeaderMap ?: emptyMap()
        val retries = requestConfig?.retryCount ?: managerConfig.defaultMaxRetries
        val backoffBase = managerConfig.defaultRetryIntervalMills

        val file = filePath.toFile()
        file.parentFile?.mkdirs()
        var existingSize = if (file.exists()) file.length() else 0L
        var restart = existingSize > 0L

        postProcessor.startDownload(null, restart)

        var remoteSize: Long? = null
        try {
            val headReqB = Request.Builder().url(uri.toString()).head()
            headers.forEach { (k, v) ->
                headReqB.header(k, v.toString())
            }
            headReqB.header(
                "User-Agent",
                managerConfig.userAgent
            )
            client.newCall(headReqB.build()).execute().use { headResp ->
                if (headResp.isSuccessful) {
                    headResp.header("Content-Length")?.let { v ->
                        v.toLongOrNull()?.let { remoteSize = it }
                    }
                }
            }
        } catch (_: Exception) {}

        if (remoteSize != null && existingSize == remoteSize) {
            postProcessor.afterReadBytes(0, true)
            postProcessor.afterDownloadComplete()
            return@withContext filePath
        }

        var cipher: Cipher? = null
        if (decryptionKey != null) {
            cipher = decryptionKey.andInitCipher
        }

        var lastEx: Throwable? = null

        for (attempt in 0 until retries) {
            try {
                val reqB = Request.Builder().url(uri.toString()).get()
                headers.forEach { (k, v) ->
                    reqB.header(k, v.toString())
                }
                reqB.header(
                    "User-Agent",
                    managerConfig.userAgent
                )

                if (existingSize > 0L) {
                    reqB.header("Range", "bytes=$existingSize-")
                }

                client.newCall(reqB.build()).execute().use { resp ->
                    val code = resp.code

                    if (existingSize > 0L && code == 200) {
                        existingSize = 0L
                        restart = false
                        try {
                            file.delete()
                        } catch (_: Exception) {}
                    }

                    if (code >= 400) {
                        throw RuntimeException("HTTP $code")
                    }

                    val contentLenHdr = resp.header("Content-Length")
                    val contentRangeHdr = resp.header("Content-Range")
                    val resolvedRemote = when {
                        contentRangeHdr != null -> {
                            val parts = contentRangeHdr.split("/", limit = 2)
                            parts.getOrNull(1)?.toLongOrNull()
                        }

                        contentLenHdr != null -> contentLenHdr.toLongOrNull()?.let { len ->
                            if (code == 206) {
                                if (existingSize > 0L) {
                                    existingSize + len
                                } else {
                                    len
                                }
                            } else {
                                len
                            }
                        }

                        else -> null
                    }
                    resolvedRemote?.let { remoteSize = it }

                    postProcessor.startDownload(remoteSize, restart)

                    val fs = FileSystem.Companion.SYSTEM
                    val okioPath = file.toPath().toOkioPath()
                    val sink: BufferedSink = try {
                        if (existingSize > 0L && file.exists()) {
                            fs.appendingSink(okioPath, true).buffer()
                        } else {
                            fs.sink(okioPath, true).buffer()
                        }
                    } catch (_: Exception) {
                        val fallback = file.sink().buffer()
                        fallback
                    }

                    val body: ResponseBody = resp.body ?: throw RuntimeException(
                        "Empty body"
                    )
                    val src: BufferedSource = body.byteStream().source().buffer()

                    val chunk = ByteArray(8192)
                    var readTotalThisAttempt = 0L

                    try {
                        while (true) {

                            ensureActive()

                            val read = src.read(chunk, 0, chunk.size)

                            if (read == -1) {
                                break
                            }

                            if (cipher != null) {
                                val out = try {
                                    cipher.update(chunk, 0, read)
                                } catch (ex: Exception) {
                                    throw ex
                                }
                                if (out != null && out.isNotEmpty()) {
                                    sink.write(out)
                                }
                            } else {
                                sink.write(chunk, 0, read)
                            }

                            readTotalThisAttempt += read
                            postProcessor.afterReadBytes(read, false)
                        }

                        if (cipher != null) {
                            try {
                                val finalBytes = cipher.doFinal()
                                if (finalBytes != null && finalBytes.isNotEmpty()) {
                                    sink.write(finalBytes)
                                }
                            } catch (ex: Exception) {
                                throw ex
                            }
                        }

                        sink.flush()
                        sink.close()
                        postProcessor.afterReadBytes(0, true)
                        postProcessor.afterDownloadComplete()
                        return@withContext filePath
                    } catch (ex: Throwable) {
                        try {
                            sink.close()
                        } catch (_: Exception) {}
                        throw ex
                    }
                }
            } catch (t: Throwable) {
                lastEx = t
                if (attempt == retries - 1) {
                    try {
                        file.delete()
                    } catch (_: Exception) {}
                    postProcessor.afterDownloadFailed()
                    throw t
                } else {
                    val backoff = exponentialBackoffMillis(backoffBase, attempt)
                    delay(backoff)
                    continue
                }
            }
        }

        throw (lastEx ?: RuntimeException("Unknown error"))
    }

    private suspend fun <T> retryWithBackoff(
        retries: Int,
        baseDelayMs: Long,
        block: suspend () -> T
    ): T {
        var last: Throwable? = null
        for (i in 0 until retries) {
            try {
                return block()
            } catch (t: Throwable) {
                last = t
                if (i == retries - 1) break
                val backoff = exponentialBackoffMillis(baseDelayMs, i)
                delay(backoff)
            }
        }
        throw last ?: RuntimeException("retry failed")
    }

    private fun exponentialBackoffMillis(baseMs: Long, attempt: Int): Long {
        val multiplier = 1L shl min(attempt, 10)
        val jitter = Random.Default.nextLong(baseMs / 4 + 1)
        return baseMs * multiplier + jitter
    }

    internal enum class State {
        ACTIVE, SHUTDOWN,
    }
}