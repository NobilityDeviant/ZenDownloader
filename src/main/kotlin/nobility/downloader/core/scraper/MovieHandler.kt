package nobility.downloader.core.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nobility.downloader.core.BoxHelper
import nobility.downloader.utils.AppInfo
import nobility.downloader.utils.FrogLog
import nobility.downloader.utils.Tools
import java.io.File

class MovieHandler {

    data class Movie(
        val name: String,
        val slug: String,
        val tag: Int
    )

    private val movies = mutableListOf<Movie>()

    suspend fun loadMovies() {
        val moviesFile = File(BoxHelper.databasePath + "movies.txt")
        if (!moviesFile.exists()) {
            FrogLog.writeMessage(
                "Movie slugs list not found. Downloading movie list..."
            )
            downloadMovieList()
            return
        }
        moviesFile.readLines().forEach {
            if (it.isNotEmpty()) {
                val split = it.split(DELIMITER)
                if (split.size == 3) {
                    movies.add(Movie(split[0], split[1], split[2].toInt()))
                }
            }
        }
        if (movies.isNotEmpty()) {
            FrogLog.writeMessage(
                "Successfully loaded ${movies.size} movie slugs."
            )
        }
    }

    private suspend fun downloadMovieList() = withContext(Dispatchers.IO) {
        movies.clear()
        try {
            val moviesFile = File(BoxHelper.databasePath + "movies.txt")
            Tools.downloadFile(
                AppInfo.WCO_MOVIE_LIST,
                moviesFile
            )
            moviesFile.readLines().forEach {
                if (it.isNotEmpty()) {
                    val split = it.split(DELIMITER)
                    if (split.size == 3) {
                        movies.add(Movie(split[0], split[1], split[2].toInt()))
                    }
                }
            }
            if (movies.isNotEmpty()) {
                FrogLog.writeMessage(
                    "Successfully loaded ${movies.size} movie slugs."
                )
            }
        } catch (e: Exception) {
            FrogLog.logError(
                "Failed to download movie list. Please restart the app before downloading movies.",
                e,
                true
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
        const val wcoMoviePlaylistLink = "https://www.wcostream.tv/playlist-cat/"
        const val DELIMITER = "<SPL>"
    }

}