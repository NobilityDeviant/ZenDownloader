package nobility.downloader.core.scraper.data

import nobility.downloader.core.entities.Episode

data class DownloadQueue(
    val episode: Episode,
    val m3U8Data: M3U8Data? = null
)