package nobility.downloader.core.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import nobility.downloader.core.entities.data.Website

@Entity
data class Favorite(
    @Id
    var id: Long = 0,
    var seriesSlug: String = "",
    var episodes: MutableList<String> = mutableListOf(),
    var lastUpdated: Long = 0,
    var website: String = Website.WCOFUN.name
) {

    fun updateEpisodes(
        newEpisodes: List<String>
    ) {
        episodes.clear()
        episodes.addAll(newEpisodes)
    }

}