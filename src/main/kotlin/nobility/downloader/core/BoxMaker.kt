package nobility.downloader.core

import io.objectbox.query.QueryBuilder
import nobility.downloader.core.entities.*
import nobility.downloader.core.entities.data.Website
import nobility.downloader.utils.findUniqueOrNull

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
        genres: List<Genre>? = null
    ): Series {
        var series = BoxHelper.wcoSeriesForSlug(slug)
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
            BoxHelper.addSeries(series)
        } else {
            if (!series.matches(newSeries)) {
                series.update(newSeries)
                BoxHelper.addSeries(series)
            }
        }
        if (episodes != null) {
            series.updateEpisodes(episodes)
        }
        if (genres != null) {
            series.updateGenres(genres)
        }
        makeHistory(
            slug,
            System.currentTimeMillis()
        )
        return series
    }

    private fun makeHistory(
        seriesSlug: String = "",
        dateAdded: Long = 0,
        website: Int = Website.WCOFUN.id
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

    private fun makeEpisodes(
        seriesSlug: String,
        episodes: List<Episode>
    ) {
        var cachedEpisodes: MutableList<Episode>
        val finalEpisodes = mutableListOf<Episode>()
        BoxHelper.shared.episodesBox.query()
            .equal(
                Episode_.seriesSlug,
                seriesSlug,
                QueryBuilder.StringOrder.CASE_SENSITIVE
            ).build().use {
                cachedEpisodes = it.find()
            }
        BoxHelper.shared.wcoBoxStore.callInTx {
            episodes.forEach {
                if (!cachedEpisodes.contains(it)) {
                    finalEpisodes.add(it)
                }
            }
        }
        BoxHelper.shared.episodesBox.put(finalEpisodes)
    }

    private fun makeGenre(
        name: String = "",
        slug: String = "",
    ) {
        BoxHelper.shared.genreBox.query()
            .equal(Genre_.name, name, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .equal(Genre_.slug, slug, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use {
                if (it.findUniqueOrNull() == null) {
                    BoxHelper.shared.genreBox.put(
                        Genre(
                            name = name,
                            slug = slug
                        )
                    )
                }
            }
    }

    private fun makeGenres(genres: List<Genre>) {
        BoxHelper.shared.wcoBoxStore.callInTx {
            genres.forEach {
                makeGenre(it.name, it.slug)
            }
        }
    }

}