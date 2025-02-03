package nobility.downloader.core.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class RecentData(
    val imagePath: String,
    val imageLink: String,
    val name: String,
    val link: String,
    val isSeries: Boolean,
    @Id var id: Long = 0
)
