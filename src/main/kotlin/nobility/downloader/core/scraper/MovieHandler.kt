package nobility.downloader.core.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.BoxHelper.Companion.long
import nobility.downloader.core.BoxHelper.Companion.update
import nobility.downloader.core.driver.DriverBase
import nobility.downloader.core.settings.Defaults
import nobility.downloader.utils.Constants
import nobility.downloader.utils.FrogLog
import nobility.downloader.utils.Tools
import org.jsoup.Jsoup
import java.io.File
import java.util.*

class MovieHandler {

    data class Movie(
        val name: String,
        val slug: String
    )

    private val movies = mutableListOf<Movie>()

    suspend fun loadMovies() {
        val moviesFile = File(BoxHelper.databasePath + "movies.txt")
        if (!moviesFile.exists()) {
            FrogLog.writeMessage(
                "Movie slugs list not found. Launching movie scraper in the background."
            )
            scrapeMovies()
            return
        }
        val lastUpdated = Defaults.WCO_MOVIES_LAST_UPDATED.long()
        if (lastUpdated > 0) {
            val lastUpdatedCal = Calendar.getInstance()
            lastUpdatedCal.time = Date(lastUpdated)
            val currentCal = Calendar.getInstance()
            val lastUpdatedDay = lastUpdatedCal.get(Calendar.DAY_OF_YEAR)
            val currentDay = currentCal.get(Calendar.DAY_OF_YEAR)
            val difference = currentDay - lastUpdatedDay
            if (difference > Constants.daysToUpdateMovies) {
                FrogLog.logInfo(
                    "Movies need to be updated. Last Checked Day: $lastUpdatedDay Current Day: $currentDay Difference: $difference (Max Days: ${Constants.daysToUpdateMovies})"
                )
                scrapeMovies()
                return
            } else {
                FrogLog.logInfo("Skipping movies update. They have been updated recently. Last Checked Day: $lastUpdatedDay Current Day: $currentDay Difference: $difference (Max Days: ${Constants.daysToUpdateMovies})")
            }
        }
        moviesFile.readLines().forEach {
            if (it.isNotEmpty()) {
                val split = it.split(DELIMITER)
                if (split.size == 2) {
                    movies.add(Movie(split[0], split[1]))
                }
            }
        }
        if (movies.isNotEmpty()) {
            FrogLog.writeMessage(
                "Successfully loaded ${movies.size} movie slugs."
            )
        } else {
            FrogLog.writeMessage(
                "Movie slugs list is empty. Launching movie scraper in the background."
            )
            scrapeMovies()
        }
    }

    private class MovieDriver: DriverBase()

    private suspend fun scrapeMovies() = withContext(Dispatchers.IO) {
        try {
            val movieDriver = MovieDriver()
            movies.clear()
            movieDriver.driver.get("https://www.wcostream.tv/movie-list")
            delay(3000)
            val doc = Jsoup.parse(movieDriver.driver.pageSource)
            val list = doc.getElementsByClass("ddmcc")
            val uls = list.select("ul")
            for (ul in uls) {
                val lis = ul.select("li")
                for (li in lis) {
                    var s = li.select("a").attr("href")
                    if (s.contains("//")) {
                        s = Tools.extractSlugFromLink(s)
                    }
                    if (s.startsWith("/")) {
                        s = s.replaceFirst("/", "")
                    }
                    if (!s.contains("movie-list")) {
                        movies.add(Movie(li.text(), s))
                    }
                }
            }
            val export = File(BoxHelper.databasePath + "movies.txt")
            val bw = export.bufferedWriter()
            movies.forEach {
                bw.write("${it.name}$DELIMITER${it.slug}")
                bw.newLine()
            }
            bw.flush()
            bw.close()
            movieDriver.killDriver()
            Defaults.WCO_MOVIES_LAST_UPDATED.update(Date().time)
            FrogLog.writeMessage(
                "Successfully downloaded ${movies.size} movie slugs."
            )
        } catch (e: Exception) {
            FrogLog.logError(
                "Failed to download movie slugs.", e
            )
        }
    }

    fun movieForSlug(slug: String): Movie? {
        if (movies.isEmpty()) {
            FrogLog.writeMessage(
                """
                    Failed to check if slug is a movie.
                    This means the movies list is empty.
                    Please reload the program before downloading movies.
                """.trimIndent()
            )
            return null
        }
        movies.forEach {
            if (it.slug == slug) {
                return it
            }
        }
        return null
    }

    companion object {
        @Suppress("warnings")
        const val wcoMoviePlaylistLink = "https://www.wcostream.tv/playlist-cat/46540/"
        private const val DELIMITER = "<SPL>"
    }

}