package nobility.downloader.core.scraper.video_download.m3u8_downloader.core

import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.CollUtil
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Utils
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Utils.secondsFormat
import nobility.downloader.utils.Tools
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.Volatile
import kotlin.math.ceil
import kotlin.math.max

class M3u8ExecutorProgress : Runnable {

    private val out = StringBuilder()
    private val seconds = AtomicLong(0)
    private val m3u8Progresses = CollUtil.newCopyOnWriteArrayList<M3u8Progress>()

    override fun run() {
        try {
            doProgress()
        } catch (ex: Exception) {
            //log.error(ex.message, ex)
        }
    }

    private fun doProgress() {
        val seconds = seconds.incrementAndGet()
        if (m3u8Progresses.isEmpty()) {
            return
        }
        var idx = 0
        var readBytes = 0L
        val limitTableSize = 10
        val completes = CollUtil.newArrayDeque<M3u8Progress>()
        val removeItems = mutableListOf<M3u8Progress>()
        for (progress in m3u8Progresses) {
            if (progress.doProgress(++idx, seconds)) {
                completes.offer(progress)
            }
            if (idx > limitTableSize) {
                completes.poll()?.let {
                    removeItems.add(it)
                }
            }

            readBytes += progress.readBytes

        }

        if (removeItems.isNotEmpty()) {
            m3u8Progresses.removeIf { p ->
                removeItems.any { it === p }
            }
        }
    }

    fun addM3u8(
        m3u8Download: M3u8Download,
        downloadFuture: Future<*>
    ) {
        m3u8Progresses.add(
            M3u8Progress(
                m3u8Download,
                downloadFuture,
                seconds.get()
            )
        )
    }

    private class M3u8Progress(
        val m3u8Download: M3u8Download,
        val downloadFuture: Future<*>,
        val startSeconds: Long
    ) {
        @Volatile
        private var endSeconds: Long = 0
        private val nowBytes = AtomicLong(0)
        private val lastBytes = AtomicLong(0)

        fun alreadyEnd(): Boolean {
            return endSeconds > 0
        }

        val readBytes: Long
            get() {
                if (alreadyEnd()) {
                    return 0
                }
                return max((nowBytes.get() - lastBytes.get()).toDouble(), 0.0).toLong()
            }

        /**
         * @return if complete
         */
        fun doProgress(
            idx: Int,
            sec: Long
        ): Boolean {

            if (alreadyEnd()) {
                val endSeconds = this.endSeconds
                val failedCount = m3u8Download.getFailedTsDownloads().toInt()
                val finishedCount = m3u8Download.getFinishedTsDownloads().toInt()

                val nowBytes = nowBytes.get()
                val avgSpeed = avgSpeed(nowBytes, endSeconds)

                return true
            }

            // timeliness param start
            var remainBytesInReading = 0
            val readingTsDownloads = m3u8Download.getReadingTsDownloads()
            var readingCount = readingTsDownloads.size
            for (tsDownload in readingTsDownloads) {
                val remainingBytes: Long = tsDownload.remainingBytes()
                if (remainingBytes <= 0) {
                    readingCount--
                } else {
                    remainBytesInReading += remainingBytes.toInt()
                }
            }

            val nowBytes = m3u8Download.getDownloadBytes()
            val failedCount = m3u8Download.getFailedTsDownloads().toInt()
            val finishedCount = m3u8Download.getFinishedTsDownloads().toInt()

            // timeliness param end
            val seconds = sec - startSeconds
            val lastBytes = this.nowBytes.get()

            this.nowBytes.set(nowBytes)
            this.lastBytes.set(lastBytes)
            val readBytes = max(0.0, (nowBytes - lastBytes).toDouble()).toLong()

            val speed = speed(readBytes)
            val avgSpeed = avgSpeed(nowBytes, seconds)

            // "idx", "name", "seconds", "speed", "avgSpeed", "progress", "downloadSize", "estimatedTime",
            // "completed", "failed", "reading", "remained"
            val isDone = downloadFuture.isDone
            if (isDone) {
                val endSeconds = if ((endSeconds.also { endSeconds = it }) > 0) endSeconds else (seconds.also {
                    this.endSeconds = it
                })
                return true
            }

            val count = m3u8Download.tsDownloadsCount
            val remainedCount = count - finishedCount - failedCount - readingCount
            val progressPercent = Tools.percentFormat.format((finishedCount + failedCount) / count.toDouble())
            //rate((finishedCount + failedCount).toLong(), count.toLong())

            val remainingSeconds: Long
            val conCount = finishedCount + readingCount
            remainingSeconds = if (readBytes > 0 && conCount > 0) {
                ceil(
                    (remainedCount * readBytes * seconds + remainedCount.toLong() * remainBytesInReading + conCount.toLong() * remainBytesInReading) /
                            (conCount * readBytes * 1.0)
                ).toLong()
            } else {
                if (nowBytes <= 0 || conCount <= 0) {
                    -1
                } else {
                    ceil(
                        (seconds * conCount * remainBytesInReading + seconds * remainedCount * nowBytes + seconds * remainedCount * remainBytesInReading) /
                                (conCount * nowBytes * 1.0)
                    ).toLong()
                }
            }

            val estimatedTime = if (remainingSeconds > 0) secondsFormat(remainingSeconds) else null

            m3u8Download.downloadListener?.downloadProgress(
                progressPercent,
                remainingSeconds.toInt(),
                finishedCount,
                count
            )

            return false
        }

        private fun fullRate(): String {
            return rate(1, 1)
        }

        private fun rate(a: Long, b: Long): String {
            return if (0L == a || 0L == b) {
                "0.00%"
            } else {
                Utils.rate(a, b).toString() + "%"
            }
        }

        private fun bytesFormat(bytes: Long): String {
            return Utils.bytesFormat(bytes, 3)
        }

        private fun speed(bytes: Long): String {
            return Utils.bytesFormat(bytes, 3) + "/s"
        }

        private fun avgSpeed(nowBytes: Long, seconds: Long): String {
            val bigDecimal = BigDecimal.valueOf(nowBytes)
                .divide(
                    BigDecimal.valueOf(seconds),
                    4,
                    RoundingMode.HALF_UP
                )
            return Utils.bytesFormat(bigDecimal, 3) + "/s"
        }

    }
}
