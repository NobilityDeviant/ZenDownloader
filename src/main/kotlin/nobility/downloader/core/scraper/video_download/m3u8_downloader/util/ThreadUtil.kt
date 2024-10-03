package nobility.downloader.core.scraper.video_download.m3u8_downloader.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.*
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy

object ThreadUtil {
    fun safeSleep(mills: Long) {
        try {
            TimeUnit.MILLISECONDS.sleep(mills)
        } catch (ignored: InterruptedException) {
        }
    }

    fun getThreadFactory(namePrefix: String?, daemon: Boolean): ThreadFactory {
        return NamedThreadFactory(daemon, namePrefix!!)
    }

    fun newFixedThreadPool(nThreads: Int, queueSize: Int, nameFormat: String?, daemon: Boolean): ExecutorService {
        return ThreadPoolExecutor(
            nThreads, nThreads, 0L, TimeUnit.MILLISECONDS,
            LinkedBlockingQueue(queueSize), getThreadFactory(nameFormat, daemon), callerRunsPolicy(nameFormat)
        )
    }

    fun newFixedScheduledThreadPool(nThreads: Int, nameFormat: String?, daemon: Boolean): ScheduledExecutorService {
        return ScheduledThreadPoolExecutor(nThreads, getThreadFactory(nameFormat, daemon))
    }

    fun callerRunsPolicy(identity: String?): CallerRunsPolicy {
        return object : CallerRunsPolicy() {
            private val log: Logger = LoggerFactory.getLogger(CallerRunsPolicy::class.java)

            override fun rejectedExecution(r: Runnable, e: ThreadPoolExecutor) {
                log.warn("{} rejected execution: now caller runs", identity)
                super.rejectedExecution(r, e)
            }
        }
    }
}
