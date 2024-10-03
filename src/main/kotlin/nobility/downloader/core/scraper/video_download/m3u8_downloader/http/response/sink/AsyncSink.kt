package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.response.sink

import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.CollUtil
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Preconditions
import org.apache.commons.collections4.CollectionUtils
import org.jctools.queues.SpscUnboundedArrayQueue
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

class AsyncSink(
    val identity: String,
    val executor: Consumer<Runnable>
) : SinkLifeCycle {

    private val sinkEventRunner = SinkEventRunner()
    private val asyncExceptions = CollUtil.newCopyOnWriteArrayList<Throwable>()

    @Throws(IOException::class)
    override fun init(reInit: Boolean) {
        if (reInit) {
            sinkEventRunner.clearEvent()
            asyncExceptions.clear()
        }
    }

    @Throws(IOException::class)
    fun submitAsyncSinkTask(sinkTask: SinkTask?) {
        checkAsyncIOException()
        //val size = sinkEventRunner.submitSinkTask(sinkTask)
        //if (size >= 100) {
            // maybe there's something wrong, log for clues
            //log.warn("too much pending event, size={}, identity={}", size, identity)
        //}
        if (sinkEventRunner.tryReady()) {
            executor.accept(this.sinkEventRunner)
        }
    }

    @Throws(IOException::class)
    override fun dispose() {
        checkAsyncIOException()
        Preconditions.checkState(
            !sinkEventRunner.havePendingTasks(),
            "have pending tasks: %s",
            identity
        )
    }

    @Throws(IOException::class)
    private fun checkAsyncIOException() {
        if (CollectionUtils.isNotEmpty(this.asyncExceptions)) {
            val ioException: IOException = IOException(String.format("async write catch IOException: %s", identity))
            val exceptions: List<Throwable?> = CollUtil.newArrayList<Throwable>(this.asyncExceptions)
            asyncExceptions.removeAll(exceptions)
            exceptions.forEach(Consumer { exception: Throwable? -> ioException.addSuppressed(exception) })
            throw ioException
        }
    }

    interface SinkTask {
        fun endData(): Boolean

        @Throws(IOException::class)
        fun doSink()

        fun completableFuture(): CompletableFuture<Void?>
    }

    private inner class SinkEventRunner : Runnable {
        private val status = AtomicReference(State.IDLE)

        private val sinkTaskQueue: SpscUnboundedArrayQueue<SinkTask>

        init {
            this.sinkTaskQueue = SpscUnboundedArrayQueue<SinkTask>(1 shl 4)
        }

        override fun run() {
            try {
                if (!status.compareAndSet(State.READY, State.RUNNING)) {
                    //log.warn("withdraw execute, update stata failed")
                    return
                }
                doSink()
            } catch (th: Throwable) {
                //log.error(th.message, th)
            } finally {
                status.set(State.IDLE)
            }
        }

        private fun doSink() {
            var sinkTask: SinkTask
            var endThrowable: Throwable? = null
            while ((sinkTaskQueue.poll().also { sinkTask = it }) != null) {
                try {
                    sinkTask.doSink()
                } catch (th: Throwable) {
                    if (sinkTask.endData()) {
                        endThrowable = th
                    } else {
                        asyncExceptions.add(th)
                    }
                } finally {
                    if (sinkTask.endData() && null != endThrowable) {
                        sinkTask.completableFuture().completeExceptionally(endThrowable)
                    } else {
                        sinkTask.completableFuture().complete(null)
                    }
                }
            }
        }

        fun clearEvent() {
            sinkTaskQueue.clear()
        }

        fun tryReady(): Boolean {
            return status.compareAndSet(State.IDLE, State.READY)
        }

        fun submitSinkTask(sinkTask: SinkTask?): Int {
            sinkTaskQueue.offer(checkNotNull(sinkTask))
            return sinkTaskQueue.size
        }

        fun havePendingTasks(): Boolean {
            return !sinkTaskQueue.isEmpty()
        }
    }

    enum class State {
        IDLE, READY, RUNNING
    }
}
