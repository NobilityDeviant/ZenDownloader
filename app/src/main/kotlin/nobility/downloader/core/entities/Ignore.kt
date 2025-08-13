package nobility.downloader.core.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import nobility.downloader.core.entities.data.Website

@Entity
data class Ignore(
    @Id
    var id: Long = 0,
    var seriesSlug: String = "",
    var website: String = Website.WCOFUN.name
)