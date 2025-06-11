package nobility.downloader.core.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import nobility.downloader.core.BoxHelper.Companion.addSeries
import nobility.downloader.core.BoxHelper.Companion.seriesForSlugAndIdentity
import nobility.downloader.core.BoxMaker
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Episode
import nobility.downloader.core.entities.Genre
import nobility.downloader.core.entities.Series
import nobility.downloader.core.entities.data.SeriesIdentity
import nobility.downloader.core.scraper.data.ToDownload
import nobility.downloader.core.scraper.video_download.Functions
import nobility.downloader.utils.*
import org.jsoup.Jsoup
import java.util.*

class SlugHandler {

    suspend fun handleSlug(
        slug: String,
        forceSeries: Boolean = false
    ): Resource<ToDownload> {
        val fullLink = slug.slugToLink()
        if (slug == "anime/movies") {
            FrogLog.message(
                """
                    You can't scrape the movies series.
                """.trimIndent()
            )
            return Resource.Error("Failed to read series.")
        }
        FrogLog.message("Scraping data from url: $fullLink")
        if (forceSeries) {
            return handleSeriesSlug(slug)
        }
        val isSeriesResult = isSeriesOrEpisodeWithSlug(slug)
        if (isSeriesResult.isFailed) {
            return Resource.Error(isSeriesResult.message)
        }
        val isSeries = isSeriesResult.data == true
        return if (isSeries) {
            handleSeriesSlug(slug)
        } else {
            handleEpisodeSlug(slug)
        }
    }

    private suspend fun handleSeriesSlug(slug: String): Resource<ToDownload> {
        val result = scrapeSeriesWithSlug(slug)
        return if (result.data != null) {
            Resource.Success(ToDownload(series = result.data))
        } else if (result.message != null) {
            Resource.Error(result.message)
        } else {
            return Resource.Error(
                "Something weird went wrong when handling a series."
            )
        }
    }

    private suspend fun handleEpisodeSlug(
        episodeSlug: String
    ): Resource<ToDownload> {
        val episode = scrapeEpisodeWithSlug(episodeSlug)
        return if (episode.data != null) {
            Resource.Success(episode.data)
        } else if (episode.message != null) {
            Resource.Error(episode.message)
        } else {
            Resource.Error(
                "Something weird went wrong when handling an episode."
            )
        }
    }

    /**
     * Returns Resource.Success(true) for series, Resource.Success(false) for episode
     * or Resource.Error for an error.
     */
    private suspend fun isSeriesOrEpisodeWithSlug(
        slug: String
    ): Resource<Boolean> = withContext(Dispatchers.IO) {
        val link = slug.slugToLink()
        try {
            val result = Functions.readUrlLines(
                link,
                "isSeriesOrEpisodeWithSlug"
            )
            if (result.data == null) {
                return@withContext Resource.Error(
                    "Failed to read source code.",
                    result.message
                )
            }
            val doc = Jsoup.parse(result.data.toString())
            val existsCheck = doc.getElementsByClass("recent-release")
            if (existsCheck.text().lowercase().contains("page not found")) {
                return@withContext Resource.Error("Page not found.")
            }
            val categoryEpisodes = doc.getElementsByClass("cat-eps")
            return@withContext Resource.Success(categoryEpisodes.isNotEmpty())
        } catch (e: Exception) {
            return@withContext Resource.Error(
                "Failed to load $link",
                e
            )
        }
    }

    private suspend fun scrapeSeriesWithSlug(
        seriesSlug: String,
        identityType: Int = -1
    ): Resource<Series> = withContext(Dispatchers.IO) {
        val identity = if (identityType == -1)
            findIdentityForSlug(seriesSlug)?.identity
        else
            SeriesIdentity.idForType(identityType)
        if (identity == null) {
            return@withContext Resource.Error(
                "Failed to find identity for $seriesSlug"
            )
        }
        val fullLink = seriesSlug.slugToLink()
        try {
            val episodes = mutableListOf<Episode>()
            val result = Functions.readUrlLines(
                fullLink,
                "scrapeSeriesWithSlug"
            )
            if (result.data == null) {
                return@withContext Resource.Error(
                    "Failed to read source code.",
                    result.message
                )
            }
            var doc = Jsoup.parse(result.data.toString())
            if (identity == SeriesIdentity.MOVIE) {
                val category = doc.getElementsByClass("header-tag")
                val h2 = category[0].select("h2")
                val categoryLink = h2.select("a").attr("href")
                val categoryName = h2.text()
                if (categoryName.lowercase(Locale.getDefault()) == "movies") {
                    val videoTitle = doc.getElementsByClass("video-title")
                    val series = BoxMaker.makeSeries(
                        slug = seriesSlug,
                        name = videoTitle[0].text(),
                        dateAdded = Tools.dateFormatted,
                        identity = identity.type,
                        episodes = listOf(
                            Episode(
                                videoTitle[0].text(),
                                seriesSlug,
                                ""
                            )
                        )
                    )
                    return@withContext Resource.Success(series)
                } else {
                    val result = Functions.readUrlLines(
                        categoryLink,
                        "scrapeSeriesWithSlug-else-category"
                    )
                    if (result.data == null) {
                        return@withContext Resource.Error(
                            "Failed to read source code.",
                            result.message
                        )
                    }
                    doc = Jsoup.parse(result.data.toString())
                }
            }

            val videoTitle = doc.getElementsByClass("video-title")
            val categoryEpisodes = doc.getElementsByClass("cat-eps")
            if (categoryEpisodes.isNotEmpty()) {
                categoryEpisodes.reverse()
                for (element in categoryEpisodes) {
                    val episodeTitle = element.select("a").text()
                    val episodeLink = element.select("a").attr("href")
                    val episode = Episode(
                        episodeTitle,
                        Tools.extractSlugFromLink(episodeLink),
                        seriesSlug
                    )
                    episodes.add(episode)
                }
                var title = ""
                if (videoTitle.isNotEmpty()) {
                    title = videoTitle[0].text()
                }
                val image = doc.getElementsByClass("img5")
                var imageLink = ""
                if (image.isNotEmpty()) {
                    imageLink = "https:${image.attr("src")}"
                }
                var descriptionText = ""
                val description = doc.getElementsByTag("p")
                if (description.isNotEmpty()) {
                    descriptionText = description[0].text()
                }
                val genres = doc.getElementsByClass("genre-buton")
                val genresList = mutableListOf<Genre>()
                if (genres.isNotEmpty()) {
                    for (genre in genres) {
                        val linkElement = genre.attr("href")
                        if (linkElement.contains("search-by-genre")) {
                            genresList.add(
                                Genre(
                                    name = genre.text(),
                                    slug = Tools.extractSlugFromLink(linkElement)
                                )
                            )
                        }
                    }
                }
                val series = BoxMaker.makeSeries(
                    seriesSlug,
                    title,
                    imageLink = imageLink,
                    description = descriptionText,
                    dateAdded = Tools.dateFormatted,
                    identity = identity.type,
                    episodes = episodes,
                    genres = genresList
                )
                ImageUtils.downloadSeriesImage(series)
                return@withContext Resource.Success(series)
            } else {
                delay(5000)
                throw Exception("No episodes were found.")
            }
        } catch (e: Exception) {
            return@withContext Resource.Error(e)
        }
    }

    private suspend fun scrapeEpisodeWithSlug(
        episodeSlug: String
    ): Resource<ToDownload> = withContext(Dispatchers.IO) {
        val episodeLink = episodeSlug.slugToLink()
        try {
            val result = Functions.readUrlLines(
                episodeLink,
                "scrapeEpisodeWithSlug"
            )
            if (result.data == null) {
                return@withContext Resource.Error(
                    "Failed to read source code.",
                    result.message
                )
            }
            val doc = Jsoup.parse(result.data.toString())
            val episodeTitle = doc.getElementsByClass("video-title")
            val category = doc.getElementsByClass("header-tag") //category is the series
            var seriesSlug = ""
            if (!category.isEmpty()) {
                val h2 = category[0].select("h2")
                val categoryLink = h2.select("a").attr("href")
                val categoryName = h2.text()
                seriesSlug = Tools.extractSlugFromLink(categoryLink)
                if (categoryName.isNotEmpty() && seriesSlug.isNotEmpty()) {
                    /**
                     * If the series is "movies" then it's not treated like a series.
                     * The movies category lists all movies and provides no other info so we can skip it.
                     */
                    if (seriesSlug.endsWith("movies")) {
                        if (episodeTitle.isNotEmpty()) {
                            val movieEpisode = Episode(
                                episodeTitle[0].text(),
                                episodeSlug,
                                ""
                            )
                            movieEpisode.isMovie = true
                            return@withContext Resource.Success(
                                ToDownload(
                                    episode = movieEpisode,
                                    isMovie = true,
                                    movieSlug = episodeSlug
                                )
                            )
                        } else {
                            return@withContext Resource.Error("Failed to find the episode title.")
                        }
                    }
                    val identityResult = findIdentityForSlug(seriesSlug)
                    if (identityResult == null) {
                        return@withContext Resource.Error(
                            "Failed to find identity for $episodeSlug"
                        )
                    }
                    val cachedSeries = seriesForSlugAndIdentity(
                        seriesSlug,
                        identityResult.identity
                    )
                    if (cachedSeries != null) {
                        val newEpisodesResult = SeriesUpdater.getNewEpisodes(cachedSeries)
                        if (newEpisodesResult.data != null) {
                            cachedSeries.updateEpisodes(
                                newEpisodesResult.data.updatedEpisodes
                            )
                        }
                        if (identityResult.alreadyExists) {
                            cachedSeries.identity = identityResult.identity.type
                            addSeries(
                                cachedSeries,
                                identityResult.identity
                            )
                        }
                        /**
                         * If the movie does have a regular series then treat it as one,
                         * but we send extra data to use Movie Mode.
                         */
                        if (identityResult.identity == SeriesIdentity.MOVIE || Core.child.movieHandler.movieForSlug(
                                episodeSlug
                            ) != null
                        ) {
                            return@withContext Resource.Success(
                                ToDownload(
                                    series = cachedSeries,
                                    episode = cachedSeries.episodeForSlug(
                                        episodeSlug
                                    ),
                                    isMovie = true,
                                    movieSlug = episodeSlug
                                )
                            )
                        } else {
                            return@withContext Resource.Success(
                                ToDownload(
                                    cachedSeries,
                                    cachedSeries.episodeForSlug(
                                        episodeSlug
                                    )
                                )
                            )
                        }
                    }
                    try {
                        FrogLog.message("Looking for series from episode link ($episodeLink).")
                        val result = handleSlug(seriesSlug, true)
                        val resultData = result.data
                        if (resultData != null) {
                            val series = resultData.series
                            if (series != null) {
                                val added = addSeries(
                                    series,
                                    identityResult.identity
                                )
                                if (added) {
                                    FrogLog.message("Added series found from episode link ($episodeLink) successfully.")
                                }
                                return@withContext Resource.Success(
                                    ToDownload(
                                        series,
                                        series.episodeForSlug(
                                            episodeSlug
                                        )
                                    )
                                )
                            }
                        } else {
                            FrogLog.logInfo(
                                "Failed to find series for $episodeLink. Error: ${result.message}"
                            )
                        }
                    } catch (e: Exception) {
                        FrogLog.logError(
                            "Failed to find series for $episodeLink.",
                            e
                        )
                    }
                }
            }
            FrogLog.message(
                "Failed to find series for episode ($episodeLink)" +
                        " Just downloading episode..."
            )
            if (episodeTitle.isNotEmpty()) {
                val episode = Episode(
                    episodeTitle[0].text(),
                    episodeSlug,
                    seriesSlug
                )
                return@withContext Resource.Success(ToDownload(episode = episode))
            } else {
                return@withContext Resource.Error("Failed to find the episode title.")
            }
        } catch (e: Exception) {
            return@withContext Resource.Error(e)
        }
    }

    private data class IdentityResult(
        val identity: SeriesIdentity,
        val alreadyExists: Boolean = false
    )

    private suspend fun findIdentityForSlug(
        slug: String
    ): IdentityResult? {
        val checkedIdentity = IdentityScraper.findIdentityForSlugLocally(slug)
        return if (SeriesIdentity.isEmpty(checkedIdentity)) {
            val result = IdentityScraper.findIdentityForSlugOnline(slug)
            return if (result.data != null) {
                IdentityResult(result.data)
            } else {
                null
            }
        } else {
            IdentityResult(
                checkedIdentity,
                true
            )
        }
    }
}