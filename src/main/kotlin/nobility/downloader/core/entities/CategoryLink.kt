package nobility.downloader.core.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Transient
import nobility.downloader.core.entities.data.SeriesIdentity

@Entity
data class CategoryLink(
    var slug: String = "",
    var type: Int = 0,
    @Id var id: Long = 0
) {

    @Transient
    val identity = SeriesIdentity.idForType(type)

}
