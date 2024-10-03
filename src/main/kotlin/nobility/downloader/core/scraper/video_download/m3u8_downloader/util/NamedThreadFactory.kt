package nobility.downloader.core.scraper.video_download.m3u8_downloader.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicLong

class NamedThreadFactory(
    daemon: Boolean,
    group: ThreadGroup?,
    namePrefix: String,
    uncaughtExceptionHandler: Thread.UncaughtExceptionHandler?
) : ThreadFactory {
    private val daemon: Boolean

    private val count: AtomicLong

    private val group: ThreadGroup?

    private val namePrefix: String

    private val uncaughtExceptionHandler: Thread.UncaughtExceptionHandler

    constructor(namePrefix: String) : this(false, namePrefix)

    @JvmOverloads
    constructor(
        daemon: Boolean,
        namePrefix: String,
        uncaughtExceptionHandler: Thread.UncaughtExceptionHandler? = null
    ) : this(daemon, null, namePrefix, uncaughtExceptionHandler)

    init {
        this.group = group
        this.daemon = daemon
        this.namePrefix = namePrefix
        this.count = AtomicLong()
        this.uncaughtExceptionHandler = uncaughtExceptionHandler ?: DefaultUncaughtExceptionHandler.INSTANCE
    }

    override fun newThread(runnable: Runnable): Thread {
        Objects.requireNonNull(runnable)
        val thread = Thread(this.group, runnable, this.namePrefix + "-" + count.incrementAndGet())
        thread.uncaughtExceptionHandler = uncaughtExceptionHandler
        thread.isDaemon = daemon

        return thread
    }

    private class DefaultUncaughtExceptionHandler : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            log.error(String.format("Caught an exception in %s", t), e)
        }

        companion object {
            internal val INSTANCE: DefaultUncaughtExceptionHandler = DefaultUncaughtExceptionHandler()
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(NamedThreadFactory::class.java)
    }
}
