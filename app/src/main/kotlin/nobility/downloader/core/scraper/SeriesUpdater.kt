package nobility.downloader.core.scraper

import Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nobility.downloader.core.driver.DriverBase
import nobility.downloader.core.entities.Episode
import nobility.downloader.core.entities.Series
import nobility.downloader.core.entities.data.SeriesIdentity
import nobility.downloader.core.scraper.data.NewEpisodes
import nobility.downloader.core.scraper.video_download.Functions
import nobility.downloader.utils.Tools
import nobility.downloader.utils.slugToLink
import org.jsoup.Jsoup

object SeriesUpdater {

    suspend fun getNewEpisodes(
        series: Series,
        driverBase: DriverBase? = null
    ): Resource<NewEpisodes> = withContext(Dispatchers.IO) {
        if (series.seriesIdentity == SeriesIdentity.MOVIE) {
            return@withContext Resource.Error(
                "Failed to find new episodes for ${series.name}",
                "Movies don't have episodes."
            )
        }
        val result = gatherSeriesEpisodes(series, driverBase)
        val data = result.data
        if (data != null) {
            if (data.size > series.episodesSize) {
                return@withContext Resource.Success(
                    NewEpisodes(
                        compareForNewEpisodes(
                            series,
                            data
                        ),
                        data
                    )
                )
            }
        } else {
            return@withContext Resource.Error(
                "Failed to find new episodes for ${series.name}",
                result.message
            )
        }
        return@withContext Resource.Error()
    }

    suspend fun getNewEpisodesAlwaysSuccess(
        series: Series,
        driverBase: DriverBase? = null
    ): Resource<NewEpisodes> = withContext(Dispatchers.IO) {
        if (series.seriesIdentity == SeriesIdentity.MOVIE) {
            return@withContext Resource.Error(
                "Failed to find new episodes for ${series.name}",
                "Movies don't have episodes."
            )
        }
        val result = gatherSeriesEpisodes(series, driverBase)
        val data = result.data
        if (data != null) {
            if (data.size > series.episodesSize) {
                return@withContext Resource.Success(
                    NewEpisodes(
                        compareForNewEpisodes(
                            series,
                            data
                        ),
                        data
                    )
                )
            } else {
                return@withContext Resource.Success(
                    NewEpisodes()
                )
            }
        } else {
            return@withContext Resource.Error(result.message)
        }
    }

    private suspend fun gatherSeriesEpisodes(
        series: Series,
        driverBase: DriverBase? = null
    ): Resource<List<Episode>> = withContext(Dispatchers.IO) {
        val seriesLink = series.slug.slugToLink()
        val result = Functions.readUrlLines(
            seriesLink,
            "New Episodes: ${series.slug}",
            driverBase = driverBase
        )
        val source = result.data
        if (!source.isNullOrEmpty()) {
            val doc = Jsoup.parse(source.toString())
            val existsCheck = doc.getElementsByClass("recent-release")
            if (existsCheck.text().lowercase().contains("page not found")) {
                return@withContext Resource.Error("Series not found.")
            }

            val episodes = mutableListOf<Episode>()

            if (doc.getElementById("episodeList") != null) {
                val episodesList = doc.select("#episodeList a.dark-episode-item")
                if (episodesList.isNotEmpty()) {
                    for (a in episodesList) {
                        val link = a.attr("href")
                        val title = a.selectFirst("span")?.text() ?: "No Title"

                        val episode = Episode(
                            title,
                            Tools.extractSlugFromLink(link)
                                .replaceFirst("/", ""),
                            series.slug
                        )
                        episodes.add(episode)
                    }
                    return@withContext Resource.Success(episodes)
                } else {
                    return@withContext Resource.Error(
                        "Failed to find episode list in webpage. (episodeList)"
                    )
                }
            } else {
                val categoryEpisodes = doc.getElementsByClass("cat-eps")
                if (categoryEpisodes.isNotEmpty()) {
                    categoryEpisodes.reverse()
                    for (element in categoryEpisodes) {
                        val episodeTitle = element.select("a").text()
                        val episodeLink = element.select("a").attr("href")
                        val episode = Episode(
                            episodeTitle,
                            Tools.extractSlugFromLink(episodeLink),
                            series.slug
                        )
                        episodes.add(episode)
                    }
                    return@withContext Resource.Success(episodes)
                } else {
                    return@withContext Resource.Error(
                        "Failed to find episode list in webpage. (cat-eps)"
                    )
                }
            }
        } else {
            return@withContext Resource.Error(
                "Failed to read webpage.",
                result.message
            )
        }
    }

    private fun compareForNewEpisodes(
        series: Series,
        latestEpisodes: List<Episode>
    ): List<Episode> {
        val episodes = mutableListOf<Episode>()
        for (e in latestEpisodes) {
            if (!series.hasEpisode(e)) {
                episodes.add(e)
            }
        }
        return episodes
    }
}