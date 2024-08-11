package nobility.downloader.core.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class Genre(
    var name: String = "",
    var slug: String = "",
    @Id var id: Long = 0
)
