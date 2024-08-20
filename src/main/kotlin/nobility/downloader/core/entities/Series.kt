package nobility.downloader.core.entities

import io.objectbox.BoxStore
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Transient
import io.objectbox.relation.ToMany
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.entities.data.SeriesIdentity
import nobility.downloader.core.entities.data.Website
import nobility.downloader.utils.FrogLog
import nobility.downloader.utils.Tools

@Entity
data class Series(
    var slug: String = "",
    var name: String = "",
    var dateAdded: String = "",
    var imageLink: String = "",
    var description: String = "",
    var lastUpdated: Long = 0,
    var identity: Int = 0,
    var website: String = Website.WCOFUN.name,
    @Id var id: Long = 0
) {

    var genreNames = mutableListOf<String>()
    var episodes: ToMany<Episode> = ToMany(this, Series_.episodes)

    val episodesSize: Int get() {
        return try {
            episodes.size
        } catch (e: Exception) {
            0
        }
    }

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
        val seriesIdentity = SeriesIdentity.idForType(identity)
        FrogLog.logDebug(
            "Updating episodes for $name."
        )
        FrogLog.logDebug(
            "Old episodes: ${this.episodes.size} New episodes: ${episodes.size}"
        )
        if (this.episodes.size < episodes.size) {
            when (seriesIdentity) {
                SeriesIdentity.DUBBED ->
                    BoxHelper.shared.dubbedSeriesBox.attach(this)
                SeriesIdentity.SUBBED ->
                    BoxHelper.shared.subbedSeriesBox.attach(this)
                SeriesIdentity.MOVIE ->
                    BoxHelper.shared.moviesSeriesBox.attach(this)
                SeriesIdentity.CARTOON ->
                    BoxHelper.shared.cartoonSeriesBox.attach(this)
                else -> BoxHelper.shared.miscSeriesBox.attach(this)
            }
            this.episodes.clear()
            this.episodes.addAll(episodes)
            if (updateDb) {
                when (seriesIdentity) {
                    SeriesIdentity.DUBBED ->
                        BoxHelper.shared.dubbedEpisodeBox.put(episodes)
                    SeriesIdentity.SUBBED ->
                        BoxHelper.shared.subbedEpisodeBox.put(episodes)
                    SeriesIdentity.MOVIE ->
                        BoxHelper.shared.moviesEpisodeBox.put(episodes)
                    SeriesIdentity.CARTOON ->
                        BoxHelper.shared.cartoonEpisodeBox.put(episodes)
                    else -> BoxHelper.shared.miscEpisodeBox.put(episodes)
                }
                this.episodes.applyChangesToDb()
            }
        }
    }

    fun updateGenres(
        genres: List<Genre>
    ) {
        updateGenresString(genres.map { it.name })
    }

    fun updateGenresString(
        genres: List<String>,
        updateDb: Boolean = true
    ) {
        this.genreNames.clear()
        this.genreNames.addAll(genres)
        if (updateDb) {
            val seriesIdentity = SeriesIdentity.idForType(identity)
            when (seriesIdentity) {
                SeriesIdentity.DUBBED ->
                    BoxHelper.shared.dubbedSeriesBox.put(this)

                SeriesIdentity.SUBBED ->
                    BoxHelper.shared.subbedSeriesBox.put(this)

                SeriesIdentity.MOVIE ->
                    BoxHelper.shared.moviesSeriesBox.put(this)

                SeriesIdentity.CARTOON ->
                    BoxHelper.shared.cartoonSeriesBox.put(this)

                else -> BoxHelper.shared.miscSeriesBox.put(this)
            }
        }
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
                + episodesSize + d
                + imageLink + d
                + description + d
                + genreNames.size + d
                + identity)
    }

    val imagePath get() = BoxHelper.seriesImagesPath + Tools.titleForImages(name)
    val seriesIdentity get() = SeriesIdentity.idForType(identity)
    val genreNamesString: String get() {
        var s = ""
        genreNames.forEachIndexed { index, genre ->
            s += genre + if (index != genreNames.lastIndex) ", " else ""
        }
        return s
    }

    @JvmField
    @Transient
    @Suppress("PropertyName")
    var __boxStore: BoxStore? = null
}
