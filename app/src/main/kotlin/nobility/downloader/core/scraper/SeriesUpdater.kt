package nobility.downloader.core.scraper

import Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nobility.downloader.core.entities.Episode
import nobility.downloader.core.entities.Series
import nobility.downloader.core.scraper.data.NewEpisodes
import nobility.downloader.core.scraper.video_download.Functions
import nobility.downloader.utils.FrogLog
import nobility.downloader.utils.Tools
import nobility.downloader.utils.user_agents.UserAgents
import nobility.downloader.utils.slugToLink
import org.jsoup.Jsoup

object SeriesUpdater {

    suspend fun getNewEpisodes(
        series: Series
    ): Resource<NewEpisodes> = withContext(Dispatchers.IO) {
        val result = gatherSeriesEpisodes(series)
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
        series: Series
    ): Resource<NewEpisodes> = withContext(Dispatchers.IO) {
        val result = gatherSeriesEpisodes(series)
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
        series: Series
    ): Resource<List<Episode>> = withContext(Dispatchers.IO) {
        val seriesLink = series.slug.slugToLink()
        val userAgent = UserAgents.random
        val result = Functions.readUrlLines(
            seriesLink,
            "New Episodes: ${series.slug}",
            userAgent = userAgent
        )
        val source = result.data
        if (!source.isNullOrEmpty()) {
            val doc = Jsoup.parse(source.toString())
            val existsCheck = doc.getElementsByClass("recent-release")
            if (existsCheck.text().lowercase().contains("page not found")) {
                return@withContext Resource.Error("Series not found.")
            }
            val seriesEpisodes = doc.getElementsByClass("cat-eps")
            if (seriesEpisodes.isNotEmpty()) {
                val episodes = mutableListOf<Episode>()
                seriesEpisodes.reverse()
                for (element in seriesEpisodes) {
                    val episodeTitle = element.select("a").text()
                    val episodeSlug = Tools.extractSlugFromLink(
                        element.select("a").attr("href")
                    )
                    val episode = Episode(
                        episodeTitle,
                        episodeSlug,
                        series.slug
                    )
                    episodes.add(episode)
                }
                return@withContext Resource.Success(episodes)
            } else {
                FrogLog.writeErrorToTxt(
                    "Source For ${series.slug}",
                    source.toString(),
                    "UserAgent: $userAgent"
                )
                return@withContext Resource.Error(
                    "Failed to find episode list in webpage. (cat-eps)"
                )
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