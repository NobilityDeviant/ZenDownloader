package nobility.downloader.core.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.Core
import nobility.downloader.core.driver.BasicDriverBase
import nobility.downloader.core.scraper.data.RecentResult
import nobility.downloader.core.settings.Defaults
import nobility.downloader.utils.Resource
import nobility.downloader.utils.Tools
import nobility.downloader.utils.Tools.titleForImages
import nobility.downloader.utils.UserAgents
import org.jsoup.Jsoup
import java.io.File

object RecentScraper {

    suspend fun run(): Resource<RecentResult> = withContext(Dispatchers.IO) {
        val data = mutableListOf<RecentResult.Data>()
        val scraper = BasicDriverBase()
        try {
            scraper.driver.get(Core.wcoUrl)
            val doc = Jsoup.parse(scraper.driver.pageSource)
            scraper.killDriver()
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
                            Tools.downloadFile(
                                seriesImageLink,
                                imageFile,
                                Defaults.TIMEOUT.int() * 1000,
                                UserAgents.random
                            )
                        }
                        data.add(RecentResult.Data(
                            imagePath,
                            seriesImageLink,
                            episodeName,
                            episodeLink,
                            false
                        ))
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
                            Tools.downloadFile(
                                seriesImageLink,
                                imageFile,
                                Defaults.TIMEOUT.int() * 1000,
                                UserAgents.random
                            )
                        }
                        data.add(RecentResult.Data(
                            imagePath,
                            seriesImageLink,
                            seriesName,
                            seriesLink,
                            true
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            return@withContext Resource.Error(e)
        }
        return@withContext Resource.Success(RecentResult(data))
    }

}