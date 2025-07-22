package nobility.downloader.core.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import nobility.downloader.core.entities.data.Website
import nobility.downloader.utils.capitalizeFirst

@Entity
data class Genre(
    var name: String = "",
    var slug: String = "",
    var website: String = Website.WCOFUN.name,
    @Id var id: Long = 0
) {
    val capitalName: String get() = name.capitalizeFirst()
}
