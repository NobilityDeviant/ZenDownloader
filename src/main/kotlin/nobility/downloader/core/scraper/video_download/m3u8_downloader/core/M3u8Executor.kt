package nobility.downloader.core.scraper.video_download.m3u8_downloader.core

import nobility.downloader.core.scraper.video_download.m3u8_downloader.core.TsDownloadOptionsSelector.PlainTsDownloadOptionsSelector.Companion.optionsSelector
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.DecryptionKey
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.HttpRequestManager
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.config.HttpRequestConfig
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.response.FileDownloadOptions
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.response.FileDownloadPostProcessor
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.CollUtil
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.FutureUtil
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.FutureUtil.disinterest
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.ThreadUtil.newFixedScheduledThreadPool
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.ThreadUtil.newFixedThreadPool
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.function.CheckedRunnable
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.function.Try
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

class M3u8Executor(
    private val requestManager: HttpRequestManager,
    val optionsSelector: TsDownloadOptionsSelector =
        optionsSelector(ifAsyncSink = true, useBufferPool = true)
) {
    val executor: ExecutorService
    private val scheduler: ScheduledExecutorService
    private val progressScheduler: M3u8ExecutorProgress

    init {
        val queueSize = 1000
        val executorNameFormat = "m3u8-executor"
        val schedulerNameFormat = "m3u8-scheduler"
        val nThreads = Runtime.getRuntime().availableProcessors()

        this.progressScheduler = M3u8ExecutorProgress()
        this.executor = newFixedThreadPool(
            nThreads,
            queueSize,
            executorNameFormat,
            false
        )
        this.scheduler = newFixedScheduledThreadPool(
            1,
            schedulerNameFormat,
            true
        )

        scheduler.scheduleWithFixedDelay(
            progressScheduler,
            1,
            1,
            TimeUnit.SECONDS
        )

        log.info("{} threads={}, queueSize={}", executorNameFormat, nThreads, queueSize)
    }

    fun shutdownAwaitMills(awaitMills: Long) {
        log.info("shutdown M3u8Executor")

        val logExceptionConsumer = Consumer<CheckedRunnable> { r: CheckedRunnable ->
            Try.run(r).onFailure {
                th -> log.error(th?.message, th)
            }
        }

        logExceptionConsumer.accept(CheckedRunnable { executor.shutdown() })

        logExceptionConsumer.accept(CheckedRunnable { scheduler.shutdown() })

        logExceptionConsumer.accept(CheckedRunnable { requestManager.shutdown() })

        logExceptionConsumer.accept(CheckedRunnable { executor.awaitTermination(awaitMills, TimeUnit.MILLISECONDS) })

        logExceptionConsumer.accept(CheckedRunnable { scheduler.awaitTermination(awaitMills, TimeUnit.MILLISECONDS) })

        logExceptionConsumer.accept(CheckedRunnable {
            requestManager.awaitTermination(
                awaitMills,
                TimeUnit.MILLISECONDS
            )
        })
    }

    @Suppress("UNUSED")
    fun execute(downloads: List<M3u8Download>): CompletableFuture<Void> {
        if (downloads.isEmpty()) {
            return CompletableFuture.completedFuture(null)
        }
        val futures = CollUtil.newArrayListWithCapacity<CompletableFuture<*>>(downloads.size)
        downloads.forEach {
            futures.add(execute(it))
        }
        return disinterest(FutureUtil.allOfColl(futures))
    }

    fun execute(m3u8Download: M3u8Download): CompletableFuture<M3u8Download> {
        val future = CompletableFuture<M3u8Download>()
        executor.execute(M3u8DownloadRunner(m3u8Download, future))
        return future
    }

    private fun downloadTs(
        tsDownloads: List<TsDownload>,
        options: FileDownloadOptions?
    ): CompletableFuture<Void> {
        val downloadFileFutureList =
            CollUtil.newArrayListWithCapacity<CompletableFuture<Path>>(tsDownloads.size)

        tsDownloads.forEach(Consumer { d: TsDownload ->
            downloadFileFutureList.add(
                downloadTs(
                    d,
                    options
                )
            )
        })

        return disinterest(FutureUtil.allOfColl(downloadFileFutureList))
    }

    private fun convertKey(m3u8SecretKey: M3u8SecretKey?): DecryptionKey? {
        if (m3u8SecretKey == null || m3u8SecretKey == M3u8SecretKey.NONE
            || m3u8SecretKey.method == M3u8SecretKey.NONE.method) {
            return null
        }
        return DecryptionKey(
            m3u8SecretKey.key!!,
            m3u8SecretKey.method,
            m3u8SecretKey.initVector!!
        )
    }

    private fun downloadTs(
        tsDownload: TsDownload,
        options: FileDownloadOptions?
    ): CompletableFuture<Path> {
        val uri = tsDownload.uri
        val filePath= tsDownload.filePath
        val m3u8Download: M3u8Download = tsDownload.m3u8Download
        val decryptionKey = convertKey(tsDownload.m3u8SecretKey)
        val m3u8DownloadOptions = m3u8Download.getM3u8DownloadOptions()
        val requestConfig = m3u8DownloadOptions.m3u8HttpRequestConfigStrategy.getConfig(
            M3u8HttpRequestType.REQ_FOR_TS,
            uri
        )
        val fileDownloadPostProcessor: FileDownloadPostProcessor = object : FileDownloadPostProcessor {
            override fun startDownload(contentLength: Long?, reStart: Boolean) {
                tsDownload.startRead(contentLength?: -1L, reStart)
            }

            override fun afterReadBytes(size: Int, end: Boolean) {
                tsDownload.readBytes(size)
            }

            override fun afterDownloadComplete() {
                tsDownload.complete()
            }

            override fun afterDownloadFailed() {
                tsDownload.failed()
            }
        }

        return requestManager.downloadFile(
            uri,
            filePath,
            m3u8Download.identity,
            options,
            decryptionKey,
            requestConfig,
            fileDownloadPostProcessor
        )
    }

    private fun bytesResponseGetter(): (URI, HttpRequestConfig?) -> ByteBuffer {
        val s: (URI, HttpRequestConfig?) -> ByteBuffer = { uri, cfg ->
            requestManager.getBytes(
                uri,
                cfg
            ).get()
        }
        return s
    }

    private inner class M3u8DownloadRunner(
        val m3u8Download: M3u8Download,
        val future: CompletableFuture<M3u8Download>
    ) : Runnable {

        override fun run() {
            try {
                // resolve m3u8

                val tsDownloads = m3u8Download.resolveTsDownloads(bytesResponseGetter())

                val options = optionsSelector.getDownloadOptions(m3u8Download, tsDownloads)

                log.info("identity={} downloadOptions={}", m3u8Download.identity, options)

                // download ts
                val downloadTsFuture = downloadTs(tsDownloads, options)

                // process scheduler
                progressScheduler.addM3u8(m3u8Download, downloadTsFuture)

                // merge ts
                downloadTsFuture.whenCompleteAsync({ _: Void?, th: Throwable? ->
                    if (null != th) {
                        log.error(th.message, th)
                        m3u8Download.downloadListener?.downloadFinished(
                            m3u8Download,
                            false
                        )
                    } else {
                        m3u8Download.downloadListener?.downloadFinished(
                            m3u8Download,
                            true
                        )
                        m3u8Download.mergeIntoVideo()
                    }
                }, executor).whenComplete { _: Void?, th: Throwable? ->
                    if (null != th) {
                        log.error(th.message, th)
                        future.completeExceptionally(th)
                    } else {
                        future.complete(m3u8Download)
                    }
                }
            } catch (th: Throwable) {
                log.error(th.message, th)
                future.completeExceptionally(th)
            }
        }
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(M3u8Executor::class.java)
    }
}







