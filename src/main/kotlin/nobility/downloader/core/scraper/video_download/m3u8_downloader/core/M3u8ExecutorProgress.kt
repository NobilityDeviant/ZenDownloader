package nobility.downloader.core.scraper.video_download.m3u8_downloader.core

import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.CollUtil
import nobility.downloader.utils.Tools
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil
import kotlin.math.max

class M3u8ExecutorProgress : Runnable {

    private val seconds = AtomicLong(0)
    private val m3u8Progresses = CollUtil.newCopyOnWriteArrayList<M3u8Progress>()

    override fun run() {
        try {
            doProgress()
        } catch (_: Exception) {
            //log.error(ex.message, ex)
        }
    }

    @Suppress("warnings")
    private fun doProgress() {
        val seconds = seconds.incrementAndGet()
        if (m3u8Progresses.isEmpty) {
            return
        }
        var idx = 0
        var readBytes = 0L
        val limitTableSize = 10
        val completes = CollUtil.newArrayDeque<M3u8Progress>()
        val removeItems = mutableListOf<M3u8Progress>()
        for (progress in m3u8Progresses) {
            idx++
            val doProgress = progress.doProgress(seconds)
            if (doProgress) {
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

    companion object {
        const val VIDEO_TAG = "(VIDEO)"
        const val AUDIO_TAG = "(AUDIO)"
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
        private val tag = if (m3u8Download.fileName.endsWith("mp4"))
            VIDEO_TAG
        else if (m3u8Download.fileName.endsWith("m4a"))
            AUDIO_TAG
        else ""

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

        fun doProgress(
            sec: Long
        ): Boolean {

            if (alreadyEnd()) {
                return true
            }
            // timeliness param start
            val count = m3u8Download.tsDownloadsCount
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

            val remainedCount = count - (finishedCount - failedCount - readingCount)
            val progressPercent = Tools.percentFormat.format((finishedCount + failedCount) / count.toDouble())

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

            val isDone = downloadFuture.isDone
            if (isDone) {
                if ((endSeconds.also { endSeconds = it }) > 0) endSeconds else (seconds.also {
                    this.endSeconds = it
                })
                m3u8Download.downloadListener?.downloadProgress?.invoke(
                    "$tag 100%",
                    0
                )
                return true
            }

            m3u8Download.downloadListener?.downloadProgress?.invoke(
                "$tag $progressPercent",
                remainingSeconds.toInt()
            )

            return false
        }

        //private fun speed(bytes: Long): String {
          //  return Utils.bytesFormat(bytes, 3) + "/s"
        //}

        /*private fun avgSpeed(nowBytes: Long, seconds: Long): String {
            val bigDecimal = BigDecimal.valueOf(nowBytes)
                .divide(
                    BigDecimal.valueOf(seconds),
                    4,
                    RoundingMode.HALF_UP
                )
            return Utils.bytesFormat(bigDecimal, 3) + "/s"
        }*/

    }
}
