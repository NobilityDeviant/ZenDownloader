package nobility.downloader.core.scraper.video_download.m3u8_downloader.core

import nobility.downloader.core.scraper.video_download.m3u8_downloader.core.M3u8Download.Companion.M3U8_STORE_NAME
import nobility.downloader.core.scraper.video_download.m3u8_downloader.core.M3u8Download.Companion.UNF_TS_EXTENSION
import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.config.HttpRequestConfig
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.CollUtil
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Preconditions
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Preconditions.m3u8Check
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Preconditions.m3u8Exception
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Utils
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.function.Try
import org.apache.commons.lang3.ObjectUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.util.function.BiPredicate
import java.util.stream.Collectors

class TsDownloadPlanner(
    private val m3u8Download: M3u8Download,
    private val bytesResponseGetter: (URI, HttpRequestConfig?) -> ByteBuffer
) {
    fun plan(): List<TsDownload> {
        val m3u8Download = this.m3u8Download
        val bytesResponseGetter = this.bytesResponseGetter

        val m3u8Uri = m3u8Download.uri
        val tsDir = m3u8Download.tsDir
        val identity = m3u8Download.identity
        val m3u8DownloadOptions = m3u8Download.getM3u8DownloadOptions()

        val m3u8StorePath = tsDir.resolve(M3U8_STORE_NAME)
        val optionsForApplyTsCache = m3u8DownloadOptions.optionsForApplyTsCache
        val requestConfigStrategy = m3u8DownloadOptions.m3u8HttpRequestConfigStrategy

        // resolve
        val m3u8Resolver = M3u8Resolver(
            m3u8Uri,
            requestConfigStrategy,
            bytesResponseGetter
        )
        m3u8Resolver.resolve()

        val mediaSegments = m3u8Resolver.mediaSegments
        m3u8Check(
            mediaSegments.isNotEmpty(),
            "empty mediaSegments: %s",
            identity
        )

        checkTsCache(
            identity,
            tsDir,
            m3u8StorePath,
            optionsForApplyTsCache,
            m3u8Resolver
        )

        // fetchSecretKey
        val secretKeyMap = m3u8Resolver.fetchSecretKey(mediaSegments)

        // convert
        val tsDownloads = convertToTsDownloads(
            M3u8DownloadOptions.OptionsForApplyTsCache.START_OVER == optionsForApplyTsCache,
            tsDir,
            mediaSegments,
            secretKeyMap
        )

        // m3u8Store
        genM3u8Store(m3u8Resolver, m3u8StorePath)

        return tsDownloads
    }

    private fun checkTsCache(
        identity: String,
        tsDir: Path,
        m3u8StorePath: Path,
        optionsForApplyTsCache: M3u8DownloadOptions.OptionsForApplyTsCache,
        m3u8Resolver: M3u8Resolver
    ) {
        if (M3u8DownloadOptions.OptionsForApplyTsCache.START_OVER == optionsForApplyTsCache) {
            log.info("start download all over: {}", identity)
            return
        }
        if (M3u8DownloadOptions.OptionsForApplyTsCache.FORCE_APPLY_CACHE_BASED_ON_FILENAME == optionsForApplyTsCache) {
            log.info("start download force apply cache based on filename: {}", identity)
            return
        }
        if (M3u8DownloadOptions.OptionsForApplyTsCache.SANITY_CHECK != optionsForApplyTsCache) {
            return
        }
        var possibleCompletedFiles: Collection<Path?>
        // except dir, m3u8StoreFile, unFinishedTs and hidden files
        val ignoredPaths = listOf(tsDir, m3u8StorePath)
        val matcher: BiPredicate<Path, BasicFileAttributes> =
            BiPredicate<Path, BasicFileAttributes> { p: Path, _: BasicFileAttributes? ->
                Try.of {
                    (!Files.isHidden(p)
                            && !ignoredPaths.contains(p)
                            && !p.fileName.toString().endsWith(".$UNF_TS_EXTENSION"))
                }.get()
            }

        Try.of { Files.find(tsDir, 2, matcher) }
            .get().use { pathStream ->
                possibleCompletedFiles = pathStream
                    .limit(10)
                    .collect(Collectors.toList())
            }
        if (possibleCompletedFiles.isEmpty()) {
            return
        }

        var suggestion = "You can adopt the following suggestion:\n" +
                "\t1. use startOver, it would be ignore and delete possible completed files;\n" +
                "\t2. modify fileName, it would be keep away from previously downloaded m3u8.\n"
        if (Files.notExists(m3u8StorePath)) {
            log.error(
                """
                    found possible completed files, but m3u8StoreFile({}) is not exists, it cannot be confirmed whether it belongs to the current m3u8: {}
                    {}
                    """.trimIndent(), m3u8StorePath, identity, suggestion
            )
            m3u8Exception("m3u8StoreFile not exists")
        }

        if (!Files.isRegularFile(m3u8StorePath)) {
            log.error(
                """
                    found possible completed files, but m3u8StoreFile({}) is not a regular file, it cannot be confirmed whether it belongs to the current m3u8: {}
                    {}
                """.trimIndent(), m3u8StorePath, identity, suggestion
            )
            m3u8Exception("m3u8StoreFile is not a regular File")
        }

        val m3u8Store: M3u8Store
        try {
            m3u8Store = M3u8Store.load(m3u8StorePath)
        } catch (ex: Exception) {
            log.error(
                """
                    found possible completed files, but load m3u8Store({}) error, it cannot be confirmed whether it belongs to the current m3u8: {}
                    {}
                """.trimIndent(), m3u8StorePath, identity, suggestion
            )
            m3u8Exception("load m3u8Store error", ex)
            return
        }

        if (m3u8Store.m3u8Uri == null || m3u8Store.finalM3u8Uri == null) {
            log.error(
                """
                    found possible completed files, but m3u8Store({}) is invalid, it cannot be confirmed whether it belongs to the current m3u8: {}
                    {}
                    """.trimIndent(), m3u8StorePath, identity, suggestion
            )
            m3u8Exception("m3u8Store is invalid")
        }

        if (m3u8Resolver.m3u8Uri == m3u8Store.m3u8Uri
            && m3u8Resolver.finalM3u8Uri == m3u8Store.finalM3u8Uri
            && m3u8Resolver.masterM3u8Uri == m3u8Store.masterM3u8Uri) {
            log.info("found possible completed files, found m3u8Store: {}\n{}", identity, m3u8Store.toPlainString())
        } else {
            suggestion = "You can adopt the following suggestion:\n" +
                    "\t1. use startOver, it would be ignore and delete possible completed files;\n" +
                    "\t2. modify fileName, it would be keep away from previously downloaded m3u8;\n" +
                    "\t3. consider use forceCacheAssignmentBasedOnFileName, you can read the details of m3u8Store({}) " + "to determine whether to use the cache of {}"
            log.error(
                "found possible completed files, but it maybe belongs to another m3u8: {}\n$suggestion",
                identity, m3u8StorePath, tsDir
            )
            m3u8Exception("possible completed files belongs to another m3u8")
        }
    }

    private fun convertToTsDownloads(
        ignoreCache: Boolean, tsDir: Path,
        mediaSegments: List<M3u8Resolver.MediaSegment>,
        secretKeyMap: Map<M3u8Resolver.MediaSegment, M3u8SecretKey>
    ): List<TsDownload> {
        Preconditions.checkArgument(ObjectUtils.allNotNull(tsDir, mediaSegments, secretKeyMap))

        val tsDownloads: MutableList<TsDownload> = CollUtil.newArrayListWithCapacity(mediaSegments.size)
        for (mediaSegment in mediaSegments) {
            val tsUri = mediaSegment.uri
            var tsFileName = Paths.get(tsUri.path).fileName.toString()
            var tsFile = tsDir.resolve("$tsFileName.$UNF_TS_EXTENSION")

            if (Utils.isFileNameTooLong(tsFile.toString())) {
                val md5 = Utils.md5(tsUri.toString())
                val rtsFile = tsDir.resolve("$md5.$UNF_TS_EXTENSION")
                log.info("fileName too long, use {}: {}", rtsFile, tsFile)
                tsFile = rtsFile
                tsFileName = "$md5.ts"
            }

            if (Files.exists(tsFile)) {
                val aFinalTsFile = tsFile
                Preconditions.checkArgument(Try.run { Files.delete(aFinalTsFile) }
                    .isSuccess, "delete file error: %s", tsFile)
                if (log.isDebugEnabled) {
                    log.debug("delete exists file: {}", tsFile)
                }
            }

            var completed = false
            val finalTsFile = tsDir.resolve(tsFileName)
            if (Files.exists(finalTsFile)) {
                if (ignoreCache || Try.of { Files.size(finalTsFile) }.get() <= 0) {
                    Preconditions.checkArgument(Try.run { Files.delete(finalTsFile) }
                        .isSuccess, "delete file error: %s", finalTsFile)
                } else {
                    completed = true
                    if (log.isDebugEnabled) {
                        log.debug("uri={} complete, use cache: {}", tsUri, finalTsFile)
                    }
                }
            }

            val m3u8SecretKey = secretKeyMap[mediaSegment]
            val tsDownload: TsDownload = TsDownload.getInstance(
                tsUri,
                tsFile,
                mediaSegment.sequence,
                finalTsFile,
                mediaSegment.durationInSeconds,
                m3u8Download,
                m3u8SecretKey
            )

            if (completed) {
                tsDownload.completeInCache()
            }
            tsDownloads.add(tsDownload)
        }

        return tsDownloads
    }

    private fun genM3u8Store(
        m3u8Resolver: M3u8Resolver,
        m3u8StorePath: Path
    ): M3u8Store {

        val m3u8Store = M3u8Store(
            m3u8Resolver.m3u8Uri,
            m3u8Resolver.finalM3u8Uri,
            m3u8Resolver.masterM3u8Uri,
            m3u8Resolver.finalM3u8Content,
            m3u8Resolver.masterM3u8Content
        )
        try {
            Files.deleteIfExists(m3u8StorePath)
            m3u8Store.store(m3u8StorePath)
        } catch (ex: Exception) {
            log.error("save m3u8Store({}) error: {}", m3u8StorePath, ex.message)
            throw RuntimeException(ex)
        }
        return m3u8Store
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(TsDownloadPlanner::class.java)
    }
}
