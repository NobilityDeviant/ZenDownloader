package nobility.downloader.core.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
open class Episode() {

    @Id var id: Long = 0
    var name: String = ""
    var slug: String = ""
    var seriesSlug: String = ""
    var lastUpdated: Long = 0
    var isMovie: Boolean = false

    constructor(name: String, slug: String, seriesSlug: String) : this() {
        this.name = name
        this.slug = slug
        this.seriesSlug = seriesSlug
    }

    fun matches(episode: Episode): Boolean {
        return equals(episode)
    }

    override fun equals(other: Any?): Boolean {
        if (other is Episode) {
            return other.name == name
                    && other.slug == slug
                    && other.seriesSlug == seriesSlug
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + slug.hashCode()
        result = 31 * result + seriesSlug.hashCode()
        return result
    }

}
