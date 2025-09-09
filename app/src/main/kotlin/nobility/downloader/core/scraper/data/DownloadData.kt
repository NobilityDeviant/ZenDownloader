package nobility.downloader.core.scraper.data

import nobility.downloader.core.settings.Quality
import nobility.downloader.ui.windows.m3u8.SubtitleOption

data class DownloadData(
    val quality: Quality,
    val downloadLink: String,
    val secondFrame: Boolean = false,
    val separateAudioLink: String = "",
    val subtitleOption: SubtitleOption? = null
)