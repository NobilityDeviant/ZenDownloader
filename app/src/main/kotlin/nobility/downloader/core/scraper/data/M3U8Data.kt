package nobility.downloader.core.scraper.data

import nobility.downloader.ui.windows.m3u8.AudioOption
import nobility.downloader.ui.windows.m3u8.SubtitleOption
import nobility.downloader.ui.windows.m3u8.VideoOption

data class M3U8Data(
    val masterLink: String,
    val videoOption: VideoOption,
    val audioOption: AudioOption?,
    val subtitleOption: SubtitleOption?,
    val userAgent: String
)