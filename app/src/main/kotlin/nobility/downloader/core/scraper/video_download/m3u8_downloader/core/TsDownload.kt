package nobility.downloader.core.scraper.video_download.m3u8_downloader.core

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.concurrent.atomic.AtomicLong

class TsDownload(
    val uri: URI,
    val filePath: Path,
    val sequence: Int?,
    val finalFilePath: Path,
    val durationInSeconds: Double?,
    val m3u8Download: M3u8Download,
    val m3u8SecretKey: M3u8SecretKey?
) {

    private val readBytes = AtomicLong(0)
    @Volatile
    private var contentLength = -1L
    @Volatile
    private var downloadStage = TsDownloadStage.NEW

    fun complete() {
        downloadStage = TsDownloadStage.COMPLETED
        m3u8Download.onFinishTsDownload(this, false)

        if (this.filePath == finalFilePath) {
            return
        }

        if (Files.exists(filePath)) {
            try {
                Files.move(
                    filePath,
                    finalFilePath,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: Exception) {
                //log.error(String.format("move %s to %s error: %s", filePath, finalFilePath, ex.message), ex)
            }
        }
    }

    fun completeInCache() {
        downloadStage = TsDownloadStage.COMPLETED_IN_CACHE
        m3u8Download.onFinishTsDownload(this, false)
    }

    fun failed() {
        downloadStage = TsDownloadStage.FAILED
        m3u8Download.onFinishTsDownload(this, true)
    }

    fun startRead(contentLength: Long, reRead: Boolean) {
        if (contentLength > 0 && contentLength != this.contentLength) {
            this.contentLength = contentLength
        }
        if (reRead) {
            readBytes.set(0)
        } else {
            downloadStage = TsDownloadStage.READING
            m3u8Download.startReadTs(this)
        }
    }

    fun readBytes(size: Int) {
        readBytes.getAndAdd(size.toLong())
        m3u8Download.downloadBytes(size)
    }

    fun remainingBytes(): Long {
        when (downloadStage) {
            TsDownloadStage.READING -> {
                val contentLength = this.contentLength
                return if (contentLength < 0) {
                    contentLength
                } else {
                    contentLength - readBytes.get()
                }
            }
            TsDownloadStage.NEW -> {
                return contentLength
            }
            else -> {
                return 0
            }
        }
    }

    val isNew: Boolean
        get() = downloadStage == TsDownloadStage.NEW

    fun unCompleted(): Boolean {
        return downloadStage != TsDownloadStage.COMPLETED
                && downloadStage != TsDownloadStage.COMPLETED_IN_CACHE
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is TsDownload) {
            return false
        }
        return uri == other.uri
                && sequence == other.sequence
                && m3u8Download == other.m3u8Download
    }

    override fun hashCode(): Int {
        return Objects.hash(uri, sequence)
    }

    enum class TsDownloadStage {
        NEW,
        READING,
        FAILED,
        COMPLETED_IN_CACHE,
        COMPLETED
    }

    companion object {
        fun getInstance(
            uri: URI,
            filePath: Path,
            sequence: Int?,
            finalFilePath: Path,
            durationInSeconds: Double?,
            m3u8Download: M3u8Download,
            m3u8SecretKey: M3u8SecretKey?
        ): TsDownload {
            return TsDownload(
                uri,
                filePath,
                sequence,
                finalFilePath,
                durationInSeconds,
                m3u8Download,
                m3u8SecretKey
            )
        }
    }
}
