package nobility.downloader.core.scraper

import kotlinx.coroutines.*
import nobility.downloader.Page
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.BoxMaker
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Episode
import nobility.downloader.core.entities.Series
import nobility.downloader.core.scraper.data.ToDownload
import nobility.downloader.core.scraper.video_download.VideoDownloadHandler
import nobility.downloader.core.settings.Defaults
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.utils.FrogLog
import nobility.downloader.utils.Resource
import nobility.downloader.utils.Tools
import java.io.File

/**
 * Used to handle the start of the download and episode scraping process.
 * @author Nobility
 */
class DownloadHandler {

    private var toDownload: ToDownload? = null
    private val taskScope = CoroutineScope(Dispatchers.Default)

    suspend fun extractDataFromUrl(url: String): Resource<Boolean> {
        val slug = Tools.extractSlugFromLink(url)
        if (slug == "anime/movies") {
            return Resource.Error(
                """
                    You can't scrape the movies series. Please use the database window instead.
                """.trimIndent()
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
            return Resource.Success(true)
        }

        val scraper = SlugHandler()
        val result = scraper.handleSlug(slug)
        val resultData = result.data
        if (resultData != null) {
            if (resultData.series != null) {
                toDownload = resultData
            } else if (resultData.episode != null) {
                toDownload = resultData
                FrogLog.writeMessage("Successfully scraped episode from: $url")
            } else {
                return Resource.Error("Failed to find data for download.")
            }
        } else if (!result.message.isNullOrEmpty()) {
            return Resource.Error(result.message)
        }
        return Resource.Success(true)
    }

    fun launch() {
        val toDownload = toDownload
        if (toDownload == null) {
            FrogLog.writeMessage("Failed to find series or episode for this link.")
            kill()
            return
        }
        taskScope.launch task@{
            val saveDir = File(Defaults.SAVE_FOLDER.string())
            if (!saveDir.exists() && !saveDir.mkdirs()) {
                withContext(Dispatchers.Main) {
                    DialogHelper.showError(
                        "Your download folder doesn't exist and wasn't able to be created.",
                        "Be sure to set it inside the settings before downloading videos."
                    )
                    Core.changePage(Page.SETTINGS)
                }
                kill()
                return@task
            }
            if (toDownload.series != null) {
                withContext(Dispatchers.Main) {
                    Core.openDownloadConfirm(
                        toDownload
                    )
                    kill()
                }
            } else {
                val downloader = VideoDownloadHandler()
                try {
                    if (toDownload.episode != null) {
                        Core.child.addEpisodeToQueue(toDownload.episode)
                    } else {
                        throw Exception("Episode hasn't been provided.")
                    }
                    downloader.run()
                    if (Core.child.downloadsFinishedForSession > 0) {
                        FrogLog.writeMessage(
                            "Gracefully finished downloading ${Core.child.downloadsFinishedForSession} video(s)."
                        )
                    } else {
                        FrogLog.writeMessage("Gracefully finished. No downloads have been made.")
                    }
                    kill()
                } catch (e: Exception) {
                    downloader.killDriver()
                    val error = e.localizedMessage
                    if (!error.isNullOrEmpty()) {
                        if (error.contains("unknown error: cannot find")
                            || error.contains("Unable to find driver executable")
                            || error.contains("unable to find binary")
                        ) {
                            FrogLog.writeMessage(
                                "DownloadHandler failed. You must install Chrome before downloading videos."
                            )
                        } else {
                            FrogLog.logError(
                                "DownloadHandler failed.",
                                e
                            )
                        }
                    } else {
                        FrogLog.writeMessage("DownloadHandler failed without a valid error.")
                    }
                    kill()
                }
            }
        }
    }

    fun kill() {
        Core.child.stop()
        taskScope.cancel()
    }

}