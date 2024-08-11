package nobility.downloader.core.entities

import io.objectbox.BoxStore
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Transient
import io.objectbox.relation.ToMany
import nobility.downloader.core.BoxHelper
import nobility.downloader.utils.FrogLog

@Entity
data class Series(
    var slug: String = "",
    var name: String = "",
    var dateAdded: String = "",
    var imageLink: String = "",
    var description: String = "",
    var lastUpdated: Long = 0,
    var identity: Int = 0,
    @Id var id: Long = 0
) {

    var genres: ToMany<Genre> = ToMany(this, Series_.genres)
    var episodes: ToMany<Episode> = ToMany(this, Series_.episodes)

    fun update(series: Series) {
        slug = series.slug
        name = series.name
        dateAdded = series.dateAdded
        imageLink = series.imageLink
        description = series.description
        identity = series.identity
    }

    fun updateEpisodes(
        episodes: List<Episode>,
        updateDb: Boolean = true
    ) {
        FrogLog.logDebug("Updating episodes for $name.")
        FrogLog.logDebug("Old episodes: ${this.episodes.size} New episodes: ${episodes.size}")
        if (this.episodes.size < episodes.size) {
            BoxHelper.shared.wcoSeriesBox.attach(this)
            this.episodes.clear()
            this.episodes.addAll(episodes)
            if (updateDb) {
                BoxHelper.shared.episodesBox.put(episodes)
                this.episodes.applyChangesToDb()
            }
        }
    }

    fun updateGenres(
        genres: List<Genre>
    ) {
        BoxHelper.shared.wcoSeriesBox.attach(this)
        this.genres.clear()
        this.genres.addAll(genres)
        BoxHelper.shared.genreBox.put(genres)
        this.genres.applyChangesToDb()
    }

    fun matches(series: Series): Boolean {
        return series.toReadable() == toReadable()
    }

    fun episodeForSlug(slug: String): Episode? {
        episodes.forEach {
            if (it.slug == slug) {
                return it
            }
        }
        return null
    }

    fun hasEpisode(episode: Episode?): Boolean {
        if (episode == null) {
            return false
        }
        episodes.forEach {
            if (it.slug == episode.slug) {
                return true
            }
        }
        return false
    }

    private fun toReadable(): String {
        val d = ";"
        return (slug + d + name + d
                + episodes.size + d
                + imageLink + d
                + description + d
                + genres.size + d
                + identity)
    }

    fun hasImageAndDescription(): Boolean {
        return (imageLink.isNotEmpty() && description.isNotEmpty())
    }

    @JvmField
    @Transient
    @Suppress("PropertyName")
    var __boxStore: BoxStore? = null
}
