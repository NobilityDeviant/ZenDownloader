package nobility.downloader.core.scraper.data

import nobility.downloader.core.settings.Quality

data class DownloadData(
    val quality: Quality,
    val downloadLink: String,
    val secondFrame: Boolean = false,
    val separateAudioLink: String = ""
)