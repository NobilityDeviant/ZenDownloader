package nobility.downloader.core.scraper.video_download.m3u8_downloader.core

class M3u8DownloadOptions(
    val deleteTsOnComplete: Boolean,
    val mergeWithoutConvertToMp4: Boolean,
    val optionsForApplyTsCache: OptionsForApplyTsCache,
    val m3u8HttpRequestConfigStrategy: M3u8HttpRequestConfigStrategy
) {
    enum class OptionsForApplyTsCache {
        START_OVER,
        SANITY_CHECK,
        FORCE_APPLY_CACHE_BASED_ON_FILENAME,
    }
}
