package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.component

import java.io.IOException

open class ExplicitlyTerminateIOException : IOException {
    constructor()

    constructor(message: String?) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause)

    constructor(cause: Throwable?) : super(cause)
}
