package nobility.downloader.core.scraper.video_download.m3u8_downloader.core
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Preconditions.checkNonNegative
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Preconditions.checkNotBlank
import org.apache.commons.collections4.MapUtils
import java.net.URI

@Suppress("UNUSED")
class M3u8DownloadBuilder internal constructor() {

    private lateinit var uri: URI
    private var workHomeDirectory: String? = null
    private lateinit var fileName: String
    private lateinit var targetFileDir: String
    private var retryCount: Int = 10
    private var deleteTsOnComplete = false
    private var mergeWithoutConvertToMp4 = false
    private var specRequestConfigStrategy: M3u8HttpRequestConfigStrategy? = null
    private var optionsForApplyTsCache: M3u8DownloadOptions.OptionsForApplyTsCache =
        M3u8DownloadOptions.OptionsForApplyTsCache.SANITY_CHECK
    private var listener: M3u8DownloadListener? = null
    private val requestTypeHeaderMap = linkedMapOf<M3u8HttpRequestType, Map<String, Any>>()

    fun setUri(uri: URI): M3u8DownloadBuilder {
        this.uri = uri
        return this
    }

    fun setUri(m3u8Url: String): M3u8DownloadBuilder {
        this.uri = URI.create(checkNotBlank(m3u8Url))
        return this
    }

    fun setWorkHome(workHomeDirectory: String): M3u8DownloadBuilder {
        this.workHomeDirectory = workHomeDirectory
        return this
    }

    fun setFileName(fileName: String): M3u8DownloadBuilder {
        this.fileName = checkNotBlank(fileName)
        return this
    }

    fun setTargetFiletDir(targetFileDir: String): M3u8DownloadBuilder {
        this.targetFileDir = targetFileDir
        return this
    }

    fun setRetryCount(retryCount: Int): M3u8DownloadBuilder {
        this.retryCount = checkNonNegative(retryCount, "retryCount")
        return this
    }

    fun deleteTsOnComplete(): M3u8DownloadBuilder {
        this.deleteTsOnComplete = true
        return this
    }

    fun mergeWithoutConvertToMp4(): M3u8DownloadBuilder {
        this.mergeWithoutConvertToMp4 = true
        return this
    }

    fun startOver(): M3u8DownloadBuilder {
        this.optionsForApplyTsCache = M3u8DownloadOptions
            .OptionsForApplyTsCache
            .START_OVER
        return this
    }

    fun forceCacheAssignmentBasedOnFileName(): M3u8DownloadBuilder {
        this.optionsForApplyTsCache = M3u8DownloadOptions
                .OptionsForApplyTsCache
                .FORCE_APPLY_CACHE_BASED_ON_FILENAME
        return this
    }

    fun optionsForApplyTsCache(
        optionsForApplyTsCache: M3u8DownloadOptions.OptionsForApplyTsCache
    ): M3u8DownloadBuilder {
        this.optionsForApplyTsCache = optionsForApplyTsCache
        return this
    }

    fun setRequestConfigStrategy(
        requestConfigStrategy: M3u8HttpRequestConfigStrategy
    ): M3u8DownloadBuilder {
        this.specRequestConfigStrategy = requestConfigStrategy
        return this
    }

    fun addListener(
        m3u8DownloadListener: M3u8DownloadListener
    ): M3u8DownloadBuilder {
        listener = m3u8DownloadListener
        return this
    }

    fun addHeaderForGetM3u8Content(
        headerKey: String,
        headerVal: Any
    ): M3u8DownloadBuilder {
        return addHttpHeader(
            M3u8HttpRequestType.REQ_FOR_M3U8_CONTENT,
            headerKey,
            headerVal
        )
    }

    fun addHeaderForGetM3u8Content(
        requestHeaderMap: Map<String, Any>
    ): M3u8DownloadBuilder {
        return addHttpHeader(
            M3u8HttpRequestType.REQ_FOR_M3U8_CONTENT,
            requestHeaderMap
        )
    }

    fun addHeaderForGetVariantPlaylist(
        headerKey: String,
        headerVal: Any
    ): M3u8DownloadBuilder {
        return addHttpHeader(
            M3u8HttpRequestType.REQ_FOR_VARIANT_PLAYLIST,
            headerKey,
            headerVal
        )
    }

    fun addHeaderForGetVariantPlaylist(
        requestHeaderMap: Map<String, Any>
    ): M3u8DownloadBuilder {
        return addHttpHeader(
            M3u8HttpRequestType.REQ_FOR_VARIANT_PLAYLIST,
            requestHeaderMap
        )
    }

    fun addHeaderForGetKey(
        headerKey: String,
        headerVal: Any
    ): M3u8DownloadBuilder {
        return addHttpHeader(
            M3u8HttpRequestType.REQ_FOR_KEY,
            headerKey,
            headerVal
        )
    }

    fun addHeaderForGeKey(
        requestHeaderMap: Map<String, Any>
    ): M3u8DownloadBuilder {
        return addHttpHeader(
            M3u8HttpRequestType.REQ_FOR_KEY,
            requestHeaderMap
        )
    }

    fun addHeaderForGetTs(
        headerKey: String,
        headerVal: Any
    ): M3u8DownloadBuilder {
        return addHttpHeader(
            M3u8HttpRequestType.REQ_FOR_TS,
            headerKey,
            headerVal
        )
    }

    fun addHeaderForGetTs(
        requestHeaderMap: Map<String, Any>
    ): M3u8DownloadBuilder {
        return addHttpHeader(
            M3u8HttpRequestType.REQ_FOR_TS,
            requestHeaderMap
        )
    }

    private fun addHttpHeader(
        requestType: M3u8HttpRequestType,
        headerKey: String,
        headerVal: Any
    ): M3u8DownloadBuilder {
        (requestTypeHeaderMap.computeIfAbsent(
            requestType
        ) { linkedMapOf() } as LinkedHashMap)[headerKey] =
            headerVal
        return this
    }

    private fun addHttpHeader(
        requestType: M3u8HttpRequestType,
        requestHeaderMap: Map<String, Any>
    ): M3u8DownloadBuilder {
        if (requestHeaderMap.isNotEmpty()) {
            (requestTypeHeaderMap.computeIfAbsent(
                requestType
            ) { linkedMapOf() } as LinkedHashMap).putAll(requestHeaderMap)
        }
        return this
    }

    fun addHttpHeader(
        headerKey: String,
        headerVal: Any
    ): M3u8DownloadBuilder {
        for (requestType in M3u8HttpRequestType.entries) {
            addHttpHeader(requestType, headerKey, headerVal)
        }
        return this
    }

    fun addHttpHeader(
        requestHeaderMap: Map<String, Any>
    ): M3u8DownloadBuilder {
        if (MapUtils.isNotEmpty(requestHeaderMap)) {
            for (requestType in M3u8HttpRequestType.entries) {
                addHttpHeader(requestType, requestHeaderMap)
            }
        }
        return this
    }

    fun build(): M3u8Download {
        var configStrategy: M3u8HttpRequestConfigStrategy? = this.specRequestConfigStrategy
        if (null == configStrategy) {
            configStrategy =
                M3u8HttpRequestConfigStrategy.DefaultM3u8HttpRequestConfigStrategy(
                    this.retryCount,
                    this.requestTypeHeaderMap
                )
        }

        val options = M3u8DownloadOptions(
            this.deleteTsOnComplete,
            this.mergeWithoutConvertToMp4,
            optionsForApplyTsCache,
            configStrategy
        )

        return M3u8Download(
            uri,
            fileName,
            workHomeDirectory,
            targetFileDir,
            listener,
            options
        )
    }
}
