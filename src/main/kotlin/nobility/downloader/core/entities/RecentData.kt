package nobility.downloader.core.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class RecentData(
    var imagePath: String = "",
    var imageLink: String = "",
    var name: String = "",
    var link: String = "",
    var isSeries: Boolean = false,
    var dateFound: Long = 0,
    @Id var id: Long = 0
) {

    fun matches(other: RecentData): Boolean {
        return imageLink == other.imageLink
                && name == other.name
                && link == other.link
                && isSeries == other.isSeries
    }
}
