package nobility.downloader.core.scraper.video_download.m3u8_downloader.core

import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.config.HttpRequestConfig
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.CollUtil
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Preconditions.checkPositive
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Preconditions.m3u8Check
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Utils
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Utils.checkAndCreateDir
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.VideoUtil
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.function.Try
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.lang.String.format
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.net.URI
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.LongAdder
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

class M3u8Download(
    var uri: URI,
    var fileName: String,
    private var workHome: String?,
    private var targetFileDir: String,
    val downloadListener: M3u8DownloadListener?,
    private var m3u8DownloadOptions: M3u8DownloadOptions
) {

    var tsDir: Path
    var identity: String
    private val downloadBytes = LongAdder()
    private val failedTsDownloads = LongAdder()
    private val finishedTsDownloads = LongAdder()
    private val tsDownloads = mutableListOf<TsDownload>()
    private val readingTsDownloads = CollUtil.newConcurrentHashSet<TsDownload>()

    init {
        m3u8Check(Utils.isValidURL(uri), "uri is invalid: %s", uri)
        checkAndCreateDir(targetFileDir, "targetFileDir")
        //FrogLog.logInfo("Created m3u8 directory: $targetFileDir")
        //val finalFileName = getFinalFileName(fileName)
        val file = if (targetFileDir.endsWith(File.separator)) {
            //File(targetFileDir + finalFileName)
            File(targetFileDir + fileName)
        } else {
            //File("$targetFileDir${File.separator}$finalFileName")
            File("$targetFileDir${File.separator}$fileName")
        }
        //FrogLog.logInfo("Final m3u8 file: ${file.absolutePath}")
        m3u8Check(!Utils.isFileNameTooLong(file.toString()), "fileName too long: %s", file)

        if (workHome == null) {
            workHome = targetFileDir
        } else {
            checkAndCreateDir(workHome!!, "workHome")
        }
        val workHome = workHome!!
        val saveFolderName = if (fileName.endsWith("mp4")) {
            "video"
        } else if (fileName.endsWith("m4a")) {
            "audio"
        } else {
            "ts"
        }
        val workFile = File(workHome)
        val tsSavePath = checkAndCreateDir(
            workFile.absolutePath + File.separator + saveFolderName,
            "tsDir"
        )
        val tsFileTest = tsSavePath.resolve(
            "1234567890-1234567890-1234567890.$UNF_TS_EXTENSION"
        )
        m3u8Check(
            !Utils.isFileNameTooLong(tsFileTest.toString()),
            "tsDir too long: %s",
            tsSavePath
        )
        this.tsDir = tsSavePath.toPath()

        this.identity = this.fileName + "@" + this.hashCode()

        log.info(
            "download m3u8, uri={}, fileName={}, targetFileDir={}, identity={}",
            this.uri, this.fileName, this.targetFileDir, this.identity
        )

    }

    fun resolveTsDownloads(
        bytesResponseGetter: (URI, HttpRequestConfig?) -> ByteBuffer
    ): List<TsDownload> {
        notifyDownloadStart()

        val tsDownloadPlanner = TsDownloadPlanner(
            this,
            bytesResponseGetter
        )

        val downloads = tsDownloadPlanner.plan()
        val newDownloads = downloads.filter { it.isNew }
        tsDownloads.clear()
        tsDownloads.addAll(downloads)

        //log.info("resolved {} ts downloads, {} need to download: {}", downloads.size, newDownloads.size, this.identity)

        return newDownloads
    }

    private fun notifyDownloadStart() {
        downloadBytes.reset()
        failedTsDownloads.reset()
        readingTsDownloads.clear()
        finishedTsDownloads.reset()
        downloadListener?.downloadStarted?.invoke(this)
    }

    @OptIn(ExperimentalPathApi::class)
    fun mergeIntoVideo() {

        val identity = this.identity
        val downloadList = this.tsDownloads

        // check uncompletedTs
        val uncompletedTs = downloadList.filter { it.unCompleted() }
            .map { it.finalFilePath.fileName.toString() }

        if (uncompletedTs.isNotEmpty()) {
            downloadListener?.onMergeFinished?.invoke(
                this,
                Exception("Incomplete ts files found.")
            )
            log.error(
                String.format(
                    "ts unCompleted, identity=%s: %s",
                    identity,
                    uncompletedTs
                )
            )
            return
        }

        // cal fileSize
        val totalSizeOfAllTsFiles = downloadList.sumOf {
            Try.ofCallable {
                checkPositive(
                    Files.size(it.finalFilePath),
                    format("file(%s) size", it.finalFilePath)
                )
            }.get()
        }

        downloadListener?.downloadSizeUpdated?.invoke(totalSizeOfAllTsFiles)

        val totalSize = Utils.bytesFormat(totalSizeOfAllTsFiles, 3)

        // cal duration
        val sumOfDuration = downloadList.stream()
            .map { d: TsDownload -> BigDecimal.valueOf(d.durationInSeconds ?: 0.0) }
            .reduce(BigDecimal.ZERO, BigDecimal::add)
        val seconds = BigDecimal.valueOf(sumOfDuration.toBigInteger().toLong())
        val nanos = sumOfDuration.subtract(seconds)
            .multiply(
                BigDecimal.TEN.pow(9),
                MathContext(0, RoundingMode.DOWN)
            )
        val duration = Duration.ofSeconds(seconds.toLong(), nanos.toLong())

        log.info(
            "download finished, {} ts, totalSizeOfAllTsFiles={}, duration={}, identity={}",
            downloadList.size, totalSize, Utils.secondsFormat(duration.seconds), identity
        )

        val targetFile = if (targetFileDir.endsWith("/"))
            File(targetFileDir + fileName)
        else
            File("$targetFileDir/$fileName")
        if (targetFile.exists()) {
            targetFile.delete()
        }
        //FrogLog.logInfo("Merging M3U8 files into ${targetFile.absolutePath}")
        val tsFiles = downloadList.sortedBy {
            it.sequence
        }.map { it.finalFilePath }

        downloadListener?.onMergeStarted?.invoke(this)

        if (m3u8DownloadOptions.mergeWithoutConvertToMp4) {
            // merge into large ts
            try {
                FileChannel.open(
                    targetFile.toPath(),
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
                ).use { fileChannel ->
                    for (ts in tsFiles) {
                        FileChannel.open(
                            ts,
                            StandardOpenOption.READ
                        ).use { inputStreamChannel ->
                            inputStreamChannel.transferTo(0, inputStreamChannel.size(), fileChannel)
                        }
                    }
                }
            } catch (e: Exception) {
                log.error("merge ts error(file={" + targetFile + "}): " + e.message, e)
                downloadListener?.onMergeFinished?.invoke(
                    this,
                    e
                )
                throw RuntimeException(e)
            }
        } else {
            // merge into mp4
            val success = VideoUtil.convertToMp4(
                targetFile,
                tsFiles
            )
            if (!success) {
                downloadListener?.onMergeFinished?.invoke(
                    this,
                    Exception("TS Convert to MP4 failed with no error.")
                )
                throw M3u8Exception("Merge failed.")
            }

        }

        // delete ts
        if (m3u8DownloadOptions.deleteTsOnComplete) {
            try {
                tsDir.deleteRecursively()
            } catch (e: IOException) {
                log.error("delete tsDir(" + tsDir + ") error" + e.message, e)
            }
        }

        downloadListener?.onMergeFinished?.invoke(this, null)
        //downloadListener?.downloadFinished(this)

        log.info("merge complete path={}", targetFile)
    }

    override fun equals(other: Any?): Boolean {
        return this === other
    }

    /**
     * Strict hashcode should be used for this class
     */
    override fun hashCode(): Int {
        return System.identityHashCode(this)
    }

    fun getM3u8DownloadOptions(): M3u8DownloadOptions {
        return this.m3u8DownloadOptions
    }

    fun startReadTs(tsDownload: TsDownload) {
        readingTsDownloads.add(tsDownload)
    }

    fun downloadBytes(bytes: Int) {
        downloadBytes.add(bytes.toLong())
    }

    val tsDownloadsCount: Int
        get() = tsDownloads.size

    fun getDownloadBytes(): Long {
        return downloadBytes.sum()
    }

    fun getFinishedTsDownloads(): Long {
        return finishedTsDownloads.sum()
    }

    fun getFailedTsDownloads(): Long {
        return failedTsDownloads.sum()
    }

    fun getReadingTsDownloads(): Set<TsDownload> {
        return Collections.unmodifiableSet(this.readingTsDownloads)
    }

    fun onFinishTsDownload(tsDownload: TsDownload, failed: Boolean) {
        readingTsDownloads.remove(tsDownload)
        if (failed) {
            failedTsDownloads.increment()
        } else {
            finishedTsDownloads.increment()
        }
    }

    val instantReadingAndRemainedCount: Long
        get() = tsDownloadsCount - getFinishedTsDownloads() - getFailedTsDownloads()

    companion object {

        val log: Logger = LoggerFactory.getLogger(M3u8Download::class.java)

        const val M3U8_STORE_NAME: String = "m3u8Index.xml"
        const val UNF_TS_EXTENSION: String = "progress"

        fun builder(): M3u8DownloadBuilder {
            return M3u8DownloadBuilder()
        }
    }
}
