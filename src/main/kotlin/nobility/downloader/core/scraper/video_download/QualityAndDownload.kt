package nobility.downloader.core.scraper.video_download

import nobility.downloader.core.settings.Quality

data class QualityAndDownload(
    val quality: Quality,
    val downloadLink: String
)