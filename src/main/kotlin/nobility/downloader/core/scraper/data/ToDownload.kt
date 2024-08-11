package nobility.downloader.core.scraper.data

import nobility.downloader.core.entities.Episode
import nobility.downloader.core.entities.Series

data class ToDownload(
    val series: Series? = null,
    val episode: Episode? = null,
    val isMovie: Boolean = false,
    val movieSlug: String = ""
)
