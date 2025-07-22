package nobility.downloader.core.scraper.video_download.m3u8_downloader.core

class M3u8Exception : RuntimeException {

    constructor(message: String?) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause)
}
