package nobility.downloader.core.scraper

import Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nobility.downloader.Page
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.BoxMaker
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Episode
import nobility.downloader.core.entities.Series
import nobility.downloader.core.scraper.data.ToDownload
import nobility.downloader.core.settings.Defaults
import nobility.downloader.utils.FrogLog
import nobility.downloader.utils.Tools
import java.io.File

object DownloadHandler {

    suspend fun run(
        url: String
    ): Resource<Boolean> = withContext(Dispatchers.IO) {

        var toDownload: ToDownload? = null

        val slug = Tools.extractSlugFromLink(url)
        if (slug == "anime/movies") {
            return@withContext Resource.Error(
                "You can't scrape the movies series. Please use the database window instead."
            )
        }

        var cachedSeries: Series? = null
        var cachedEpisode: Episode? = null

        if (url.contains("/anime/")) {
            cachedSeries = BoxHelper.seriesForSlug(slug)
        } else {
            val pair = BoxHelper.seriesForEpisodeSlug(slug)
            if (pair != null) {
                cachedSeries = pair.first
                cachedEpisode = pair.second
            }
        }
        if (cachedSeries != null && cachedSeries.episodes.isNotEmpty()) {
            toDownload = ToDownload(
                series = cachedSeries,
                episode = cachedEpisode
            )
            BoxMaker.makeHistory(
                cachedSeries.slug
            )
        }

        val scraper = SlugHandler()
        val result = scraper.handleSlug(slug)
        val resultData = result.data
        if (resultData != null) {
            if (resultData.series != null) {
                toDownload = resultData
            } else if (resultData.episode != null) {
                toDownload = resultData
                FrogLog.message("Successfully scraped episode from: $url")
            } else {
                return@withContext Resource.Error("Failed to find data for download.")
            }
        } else if (!result.message.isNullOrEmpty()) {
            return@withContext Resource.Error(result.message)
        }

        if (toDownload == null) {
            return@withContext Resource.Error(
                "Failed to find a series or episode for this link."
            )
        }
        val saveDir = File(Defaults.SAVE_FOLDER.string())
        if (!saveDir.exists() && !saveDir.mkdirs()) {
            Core.changePage(Page.SETTINGS)
            return@withContext Resource.Error(
                """
                    Your download folder doesn't exist and wasn't able to be created.
                    Be sure to set it inside the settings before downloading videos.
                """.trimIndent()
            )
        }
        if (toDownload.series != null) {
            Core.openDownloadConfirm(
                toDownload
            )
            return@withContext Resource.Success(true)
        } else {
            if (toDownload.episode != null) {
                Core.child.downloadThread.addToQueue(toDownload.episode)
                return@withContext Resource.Success(true)
            } else {
                return@withContext Resource.Error(
                    "An episode wasn't found or provided."
                )
            }
        }
    }
}