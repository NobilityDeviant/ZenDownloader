package nobility.downloader.core.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import nobility.downloader.core.entities.data.Website

@Entity
data class DownloadedEpisode(
    @Id
    var id: Long = 0,
    var episodeSlug: String = "",
    var downloadedDate: Long = 0,
    var downloadedSecond: Boolean = false,
    var website: String = Website.WCOFUN.name
)