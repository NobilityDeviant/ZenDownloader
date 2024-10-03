package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.component

class UnexpectedHttpStatusException(message: String?) : ExplicitlyTerminateIOException(message) {
    companion object {
        @Throws(ExplicitlyTerminateIOException::class)
        fun throwException(message: String?) {
            throw UnexpectedHttpStatusException(message)
        }
    }
}
