package nobility.downloader.core.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nobility.downloader.core.driver.DriverBase
import nobility.downloader.core.entities.Episode
import nobility.downloader.core.entities.Series
import nobility.downloader.core.scraper.data.NewEpisodes
import nobility.downloader.utils.FrogLog
import nobility.downloader.utils.Resource
import nobility.downloader.utils.Tools
import nobility.downloader.utils.slugToLink
import org.jsoup.Jsoup

object SeriesUpdater {

    suspend fun checkForNewEpisodes(
        series: Series
    ): Resource<NewEpisodes> = withContext(Dispatchers.IO) {
        FrogLog.writeMessage("Looking for new episodes for: ${series.name}")
        val scraper = EpisodeSlugHelper()
        val result = scraper.getSeriesEpisodesWithSlug(series.slug)
        scraper.killDriver()
        if (result.data != null) {
            if (result.data.size > series.episodesSize) {
                return@withContext Resource.Success(
                    NewEpisodes(compareForNewEpisodes(series, result.data), result.data)
                )
            }
        }
        return@withContext Resource.Error("No new episode have been found for ${series.name}.")
    }

    private class EpisodeSlugHelper : DriverBase() {
        suspend fun getSeriesEpisodesWithSlug(
            seriesSlug: String
        ): Resource<List<Episode>> = withContext(Dispatchers.IO) {
            val seriesLink = seriesSlug.slugToLink()
            try {
                driver.get(seriesLink)
                val doc = Jsoup.parse(driver.pageSource)
                val existsCheck = doc.getElementsByClass("recent-release")
                if (existsCheck.text().lowercase().contains("page not found")) {
                    return@withContext Resource.Error("Page not found.")
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
                            seriesSlug
                        )
                        episodes.add(episode)
                    }
                    return@withContext Resource.Success(episodes)
                }
            } catch (e: Exception) {
                return@withContext Resource.Error("Failed to load $seriesLink Error: ${e.localizedMessage}")
            }
            return@withContext Resource.Error("Failed to find any episodes for $seriesLink")
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