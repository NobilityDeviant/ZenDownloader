package nobility.downloader.core.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.BoxHelper.Companion.update
import nobility.downloader.core.BoxMaker
import nobility.downloader.core.Core
import nobility.downloader.core.scraper.video_download.Functions
import nobility.downloader.core.settings.Defaults
import nobility.downloader.utils.FrogLog
import nobility.downloader.utils.Resource
import nobility.downloader.utils.Tools
import nobility.downloader.utils.Tools.titleForImages
import nobility.downloader.utils.UserAgents
import org.jsoup.Jsoup
import java.io.File

object RecentScraper {

    suspend fun run(): Resource<Boolean> = withContext(Dispatchers.IO) {
        try {
            val result = Functions.readUrlLines(
                Core.wcoUrl,
                "RecentScraper"
            )
            val data = result.data
            if (data == null) {
                return@withContext Resource.Error(
                    "Failed to read the websites source code."
                )
            }
            val doc = Jsoup.parse(data.toString())
            val recentEpisodeHolder = doc.getElementById("sidebar_right")
            if (recentEpisodeHolder != null) {
                val ul = recentEpisodeHolder.select("ul")
                for (uls in ul) {
                    val lis = uls.select("li")
                    for (li in lis) {
                        val img = li.select("div.img")
                        var seriesImageLink = img.select("a").select("img").attr("src")
                        if (!seriesImageLink.startsWith("https:")) {
                            seriesImageLink = "https:$seriesImageLink"
                        }
                        val episode = li.select("div.recent-release-episodes").select("a")
                        val episodeName = episode.text()
                        val episodeLink = episode.attr("href")
                        val seriesImagesPath = BoxHelper.seriesImagesPath
                        val seriesImagesFolder = File(seriesImagesPath)
                        if (!seriesImagesFolder.exists()) {
                            seriesImagesFolder.mkdirs()
                        }
                        val imagePath = seriesImagesPath + titleForImages(episodeName)
                        val imageFile = File(imagePath)
                        if (!imageFile.exists()) {
                            try {
                                Tools.downloadFile(
                                    seriesImageLink,
                                    imageFile,
                                    Defaults.TIMEOUT.int() * 1000,
                                    UserAgents.random
                                )
                            } catch (_: Exception) {
                                FrogLog.logError(
                                    "Failed to download image: $seriesImageLink"
                                )
                            }
                        }
                        BoxMaker.makeRecent(
                            imagePath,
                            seriesImageLink,
                            episodeName,
                            episodeLink,
                            false
                        )
                    }
                }
            }
            val recentSeriesHolder = doc.getElementById("sidebar_right2")
            if (recentSeriesHolder != null) {
                val ul = recentSeriesHolder.select("ul")
                for (uls in ul) {
                    val lis = uls.select("li")
                    for (li in lis) {
                        val img = li.select("div.img")
                        var seriesImageLink = img.select("a").select("img").attr("src")
                        if (!seriesImageLink.startsWith("https:")) {
                            seriesImageLink = "https:$seriesImageLink"
                        }
                        val series = li.select("div.recent-release-episodes").select("a")
                        val seriesName = series.text()
                        val seriesLink = series.attr("href")
                        val seriesImagesPath = BoxHelper.seriesImagesPath
                        val seriesImagesFolder = File(seriesImagesPath)
                        if (!seriesImagesFolder.exists()) {
                            seriesImagesFolder.mkdirs()
                        }
                        val imagePath = seriesImagesPath + titleForImages(seriesName)
                        val imageFile = File(imagePath)
                        if (!imageFile.exists()) {
                            try {
                                Tools.downloadFile(
                                    seriesImageLink,
                                    imageFile,
                                    Defaults.TIMEOUT.int() * 1000,
                                    UserAgents.random
                                )
                            } catch (_: Exception) {
                                FrogLog.logError(
                                    "Failed to download image: $seriesImageLink"
                                )
                            }
                        }
                        BoxMaker.makeRecent(
                            imagePath,
                            seriesImageLink,
                            seriesName,
                            seriesLink,
                            true
                        )
                    }
                }
            }
            Defaults.WCO_RECENT_LAST_UPDATED.update(Tools.currentTime)
        } catch (e: Exception) {
            return@withContext Resource.Error(e)
        }
        return@withContext Resource.Success(true)
    }

}