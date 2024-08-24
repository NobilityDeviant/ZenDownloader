package nobility.downloader.core.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.BoxHelper.Companion.long
import nobility.downloader.core.BoxHelper.Companion.update
import nobility.downloader.core.BoxMaker
import nobility.downloader.core.Core
import nobility.downloader.core.driver.DriverBase
import nobility.downloader.core.settings.Defaults
import nobility.downloader.utils.Constants
import nobility.downloader.utils.FrogLog
import nobility.downloader.utils.linkToSlug
import org.jsoup.Jsoup
import java.util.*

//out of use for now since I added it to the asset updater.
//might still use as a backup
object GenresScraper {

    private class Scraper: DriverBase()

    suspend fun scrape() = withContext(Dispatchers.Default) {
        val lastUpdated = Defaults.WCO_GENRES_LAST_UPDATED.long()
        if (!BoxHelper.shared.wcoGenreBox.isEmpty) {
            if (lastUpdated > 0) {
                val lastUpdatedCal = Calendar.getInstance()
                lastUpdatedCal.time = Date(lastUpdated)
                val currentCal = Calendar.getInstance()
                val lastUpdatedDay = lastUpdatedCal.get(Calendar.DAY_OF_YEAR)
                val currentDay = currentCal.get(Calendar.DAY_OF_YEAR)
                val difference = currentDay - lastUpdatedDay
                if (difference > Constants.daysToUpdateGenres) {
                    FrogLog.logInfo(
                        "Genres need to be updated. Last Checked Day: $lastUpdatedDay Current Day: $currentDay Difference: $difference (Max Days: ${Constants.daysToUpdateMovies})"
                    )
                } else {
                    FrogLog.logInfo(
                        "Skipping genres update. They have been updated recently. Last Checked Day: $lastUpdatedDay Current Day: $currentDay Difference: $difference (Max Days: ${Constants.daysToUpdateMovies})"
                    )
                    return@withContext
                }
            }
        }
        val scraper = Scraper()
        scraper.driver.get(Core.wcoUrl)
        val doc = Jsoup.parse(scraper.driver.pageSource)
        scraper.killDriver()
        val genresHolder = doc.getElementById("sidebar_right3")
        if (genresHolder != null) {
            val genres = genresHolder.getElementsByClass("cerceve")
            genres.forEach { genre ->
                val name = genre.text()
                val slug = genre.select("a").attr("href").linkToSlug()
                BoxMaker.makeGenre(name, slug)
            }
            Defaults.WCO_GENRES_LAST_UPDATED.update(Date().time)
        }
    }
}