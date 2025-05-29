package nobility.downloader.core

import io.objectbox.query.QueryBuilder
import nobility.downloader.core.entities.*
import nobility.downloader.core.entities.data.SeriesIdentity
import nobility.downloader.core.entities.data.Website
import nobility.downloader.utils.findUniqueOrNull
import java.util.*

object BoxMaker {

    /**
     * Creates and returns a series.
     * This is needed so we can control the series creation
     * to ensure everything is properly added.
     * Scattering series creation only complicates everything.
     * @return The created series.
     */
    fun makeSeries(
        slug: String,
        name: String = "",
        dateAdded: String = "",
        imageLink: String = "",
        description: String = "",
        lastUpdated: Long = 0,
        identity: Int = 0,
        episodes: List<Episode>? = null,
        genres: List<Genre>? = null,
        addToHistory: Boolean = true
    ): Series {
        var series = BoxHelper.seriesForSlug(
            slug
        )
        val newSeries = Series(
            slug,
            name,
            dateAdded,
            imageLink,
            description,
            lastUpdated,
            identity
        )
        if (series == null) {
            series = newSeries
            BoxHelper.addSeries(
                series,
                SeriesIdentity.idForType(identity)
            )
        } else {
            if (!series.matches(newSeries)) {
                series.update(newSeries)
                BoxHelper.addSeries(
                    series,
                    SeriesIdentity.idForType(identity)
                )
            }
        }
        if (episodes != null) {
            series.updateEpisodes(episodes)
        }
        if (genres != null) {
            series.updateGenres(genres)
        }
        if (addToHistory) {
            makeHistory(
                slug
            )
        }
        return series
    }

    fun makeHistory(
        seriesSlug: String,
        dateAdded: Long = Date().time,
        website: String = Website.WCOFUN.name
    ) {
        BoxHelper.shared.historyBox.query()
            .equal(
                SeriesHistory_.seriesSlug,
                seriesSlug,
                QueryBuilder.StringOrder.CASE_SENSITIVE
            ).build().use {
                var history = it.findUniqueOrNull()
                if (history != null) {
                    history.dateAdded = dateAdded
                    history.website = website
                } else {
                    history = SeriesHistory(
                        website,
                        seriesSlug,
                        dateAdded
                    )
                }
                BoxHelper.shared.historyBox.put(history)
            }
    }

    fun makeFavorite(
        seriesSlug: String,
        episodeSlugs: List<String> = mutableListOf(),
        website: String = Website.WCOFUN.name
    ) {
        BoxHelper.shared.favoriteBox.query()
            .equal(Favorite_.seriesSlug, seriesSlug, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use {
                var favorite = it.findUniqueOrNull()
                if (favorite != null) {
                    favorite.lastUpdated = System.currentTimeMillis()
                    favorite.website = website
                    favorite.updateEpisodes(episodeSlugs)
                } else {
                    favorite = Favorite(
                        seriesSlug = seriesSlug,
                        website = website,
                        lastUpdated = System.currentTimeMillis()
                    )
                    favorite.updateEpisodes(episodeSlugs)
                }
                BoxHelper.shared.favoriteBox.put(favorite)
            }
    }

    fun makeGenre(
        name: String = "",
        slug: String = "",
        website: String = Website.WCOFUN.name
    ) {
        BoxHelper.shared.wcoGenreBox.query()
            .equal(Genre_.name, name, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .equal(Genre_.slug, slug, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use {
                if (it.findUniqueOrNull() == null) {
                    BoxHelper.shared.wcoGenreBox.put(
                        Genre(
                            name = name,
                            slug = slug,
                            website = website
                        )
                    )
                }
            }
    }

    fun makeRecent(
        imagePath: String,
        imageLink: String,
        name: String,
        link: String,
        isSeries: Boolean,
    ) {
        BoxHelper.shared.wcoRecentBox.query()
            .equal(RecentData_.name, name, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .equal(RecentData_.link, link, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use {
                if (it.findUniqueOrNull() == null) {
                    BoxHelper.shared.wcoRecentBox.put(
                        RecentData(
                            imagePath,
                            imageLink,
                            name,
                            link,
                            isSeries
                        )
                    )
                }
            }
    }
}