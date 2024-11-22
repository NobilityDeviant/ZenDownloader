package nobility.downloader.core.scraper.data

import nobility.downloader.core.settings.Quality

data class ParsedQuality(
    val quality: Quality,
    val downloadLink: String,
    val separateAudioLink: String
)