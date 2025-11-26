package nobility.downloader.core.scraper.data

import nobility.downloader.core.entities.Episode
import nobility.downloader.core.settings.Quality

data class DownloadQueue(
    val episode: Episode,
    val m3U8Data: M3U8Data? = null,
    val quality: Quality? = null
)