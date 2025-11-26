package nobility.downloader.core.scraper.video_download.m3u8_downloader.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.CollUtil
import nobility.downloader.utils.Tools
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil
import kotlin.math.max

class M3u8Progress {

    private val elapsedSeconds = AtomicLong(0)
    private val m3u8Progresses = CollUtil.newCopyOnWriteArrayList<M3u8Progress>()

    suspend fun run() = withContext(Dispatchers.Default) {

        while (isActive) {
            delay(1000)
            elapsedSeconds.andIncrement
            val snapshot = m3u8Progresses.toList()
            if (snapshot.isEmpty()) {
                break
            }
            snapshot.forEach { progress ->
                val isDone = progress.doProgress()

                if (isDone) {
                    m3u8Progresses.remove(progress)
                }
            }
        }
    }


    fun addM3u8(
        m3u8Download: M3u8Download
    ) {
        m3u8Progresses.add(
            M3u8Progress(
                m3u8Download,
                elapsedSeconds.get()
            )
        )
    }

    private inner class M3u8Progress(
        val m3u8Download: M3u8Download,
        val startSeconds: Long
    ) {
        @Volatile
        private var endSeconds = 0L
        private val nowBytes = AtomicLong(0)
        private val lastBytes = AtomicLong(0)

        fun alreadyEnd(): Boolean {
            return endSeconds > 0
        }

        fun doProgress(): Boolean {

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
                    remainBytesInReading = remainingBytes.toInt()
                }
            }

            val nowBytes = m3u8Download.getDownloadBytes()
            val failedCount = m3u8Download.getFailedTsDownloads().toInt()
            val finishedCount = m3u8Download.getFinishedTsDownloads().toInt()

            // timeliness param end
            val seconds = elapsedSeconds.get() - startSeconds
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
                    ((remainedCount * readBytes * seconds) +
                            (remainedCount.toLong() * remainBytesInReading) +
                            (conCount.toLong() * remainBytesInReading)) /
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

            val isDone = m3u8Download.completed
            if (isDone) {
                if ((endSeconds.also { endSeconds = it }) > 0) endSeconds else (seconds.also {
                    this.endSeconds = it
                })
                //m3u8Download.downloadListener?.downloadProgress?.invoke(
                   // "100%",
                   // 0
                //)
                return true
            }

            m3u8Download.downloadListener?.downloadProgress?.invoke(
                "$progressPercent",
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
