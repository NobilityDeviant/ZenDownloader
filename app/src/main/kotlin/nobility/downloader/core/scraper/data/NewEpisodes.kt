package nobility.downloader.core.scraper.data

import nobility.downloader.core.entities.Episode

data class NewEpisodes(
    val newEpisodes: List<Episode>,
    val updatedEpisodes: List<Episode>
)