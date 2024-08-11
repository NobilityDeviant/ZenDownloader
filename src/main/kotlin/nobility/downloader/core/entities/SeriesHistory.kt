package nobility.downloader.core.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import nobility.downloader.core.entities.data.Website

@Entity
data class SeriesHistory(
    var website: Int = Website.WCOFUN.id,
    var seriesSlug: String = "",
    var dateAdded: Long = 0,
    @Id var id: Long = 0
)