package nobility.downloader.core.scraper.video_download

enum class ErrorCode(val code: Int) {
    NO_FRAME(0),
    IFRAME_FORBIDDEN(1),
    FAILED_EXTRACT_RES(2),
    NO_JS(3),
    CLOUDFLARE_FUCK(4),
    SIMPLE_MODE_FAILED(5),
    M3U8_LINK_FAILED(6);

    companion object {
        fun errorCodeForCode(code: Int?): ErrorCode? {
            if (code == null) {
                return null
            }
            entries.forEach {
                if (code == it.code) {
                    return it
                }
            }
            return null
        }
    }
}