package nobility.downloader.core.scraper.video_download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nobility.downloader.core.BoxHelper.Companion.downloadForSlugAndQuality
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.BoxMaker
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Download
import nobility.downloader.core.scraper.MovieHandler
import nobility.downloader.core.scraper.data.DownloadData
import nobility.downloader.core.scraper.video_download.Functions.downloadVideo
import nobility.downloader.core.scraper.video_download.Functions.fileSize
import nobility.downloader.core.settings.Defaults
import nobility.downloader.core.settings.Quality
import nobility.downloader.utils.*
import org.openqa.selenium.By
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.File
import java.time.Duration

object MovieDownloader {

    suspend fun handleMovie(
        movie: MovieHandler.Movie,
        data: VideoDownloadData
    ) = withContext(Dispatchers.IO) {
        data.info("Detected movie for ${movie.slug}")
        var qualityOption = data.temporaryQuality ?: Quality.qualityForTag(
            Defaults.QUALITY.string()
        )
        var preferredDownload: DownloadData? = null
        var downloadLink = ""
        val videoLinkError = ""
        val downloadFolderPath = Defaults.SAVE_FOLDER.string()
        var saveFolder = File(
            downloadFolderPath + File.separator + "Movies"
        )
        if (!saveFolder.exists() && !saveFolder.mkdirs()) {
            data.message(
                "Failed to create movies save folder: ${saveFolder.absolutePath} " +
                        "\nDefaulting to $downloadFolderPath"
            )
            saveFolder = File(downloadFolderPath + File.separator)
        }
        if (data.quickCheckVideoExists(movie.slug)) {
            return@withContext
        }
        val wait = WebDriverWait(
            data.driver,
            Duration.ofSeconds(
                Defaults.TIMEOUT.int().toLong()
            )
        )
        val premUser = Defaults.WCO_PREMIUM_USERNAME.string()
        val premPassword = Defaults.WCO_PREMIUM_PASSWORD.string()

        if (premUser.isNotEmpty() && premPassword.isNotEmpty()) {
            if (data.downloadDatas.isEmpty()) {
                try {
                    data.driver.navigate().to(
                        "https://www.wcopremium.tv/wp-login.php"
                    )
                    val usernameField = data.driver.findElement(By.id("user_login"))
                    val passwordField = data.driver.findElement(By.id("user_pass"))
                    usernameField.sendKeys(premUser)
                    passwordField.sendKeys(premPassword)
                    passwordField.submit()

                    //look for an element only found while logged in.
                    wait.pollingEvery(Duration.ofSeconds(1))
                        .until(
                            ExpectedConditions.presenceOfElementLocated(
                                By.className("header-top-right")
                            )
                        )

                    try {
                        //no error. logged in successfully
                        data.driver.navigate().to(
                            "https://www.wcopremium.tv/${movie.slug}"
                        )
                        val src = data.driver.source().lines()
                        val hd1080 = """format: "hd1080", src: """"
                        val hd720 = """format: "hd720", src: """"
                        val sd576 = """format: "sd576", src: """"
                        val endTag = """", type: "video/mp4"},"""
                        src.forEach { line ->
                            if (line.contains(hd1080)) {
                                data.downloadDatas.add(
                                    DownloadData(
                                        Quality.HIGH,
                                        line.substringAfter(hd1080).substringBefore(endTag)
                                    )
                                )
                                data.info("[PREM] Found ${Quality.HIGH} quality.")
                            } else if (line.contains(hd720)) {
                                data.downloadDatas.add(
                                    DownloadData(
                                        Quality.MED,
                                        line.substringAfter(hd720).substringBefore(endTag)
                                    )
                                )
                                data.info("[PREM] Found ${Quality.MED} quality.")
                            } else if (line.contains(sd576)) {
                                data.downloadDatas.add(
                                    DownloadData(
                                        Quality.LOW,
                                        line.substringAfter(sd576).substringBefore(endTag)
                                    )
                                )
                                data.info("[PREM] Found ${Quality.LOW} quality.")
                            }
                        }
                        if (data.downloadDatas.isNotEmpty()) {
                            qualityOption = Quality.bestQuality(
                                qualityOption,
                                data.downloadDatas.map { it.quality }
                            )
                            data.downloadDatas.forEach {
                                if (it.quality == qualityOption) {
                                    preferredDownload = it
                                    downloadLink = it.downloadLink
                                }
                            }
                        } else {
                            data.error("Failed to find wcopremium.tv qualities. Make sure you have a valid subscription.")
                        }
                    } catch (e: Exception) {
                        data.error(
                            "Failed to scrape wcopremium.tv | Retrying...",
                            e
                        )
                        data.premRetries++
                        return@withContext
                    }
                } catch (e: Exception) {
                    data.error(
                        "Failed to log into wcopremium.tv | Retrying...",
                        e,
                        true
                    )
                    data.retries++
                    return@withContext
                }
            }

        } else {
            data.message(
                "You can't download movies without a valid wcopremium.tv subscription. | Skipping..."
            )
            data.finishEpisode()
            return@withContext
        }
        if (videoLinkError.isNotEmpty()) {
            data.error(
                "Failed to find video link for movie. | Retrying...",
                videoLinkError
            )
            data.retries++
            return@withContext
        }
        if (downloadLink.isEmpty()) {
            data.message(
                "Failed to find download link for movie. | Retrying..."
            )
            data.retries++
            return@withContext
        }
        val extraQualityName = if (qualityOption != Quality.LOW)
            " (${qualityOption.tag})" else ""
        val episodeName = movie.name.fixForFiles()
        val saveFile = File(
            saveFolder.absolutePath + File.separator
                    + "$episodeName$extraQualityName.mp4"
        )
        data.mCurrentDownload = downloadForSlugAndQuality(
            movie.slug,
            qualityOption
        )
        if (data.mCurrentDownload != null) {
            if (data.currentDownload.downloadPath.isEmpty()
                || !File(data.currentDownload.downloadPath).exists()
                || data.currentDownload.downloadPath != saveFile.absolutePath
            ) {
                data.currentDownload.downloadPath = saveFile.absolutePath
            }
            Core.child.addDownload(data.currentDownload)
            if (data.currentDownload.isComplete) {
                data.message("[DB] Skipping completed video.")
                BoxMaker.makeDownloadedEpisode(data.currentDownload.slug)
                data.finishEpisode()
                return@withContext
            } else {
                data.currentDownload.queued = true
                Core.child.updateDownloadProgress(data.currentDownload)
            }
        }
        data.info("Successfully found movie video link with ${data.retries} retries.")
        try {
            if (data.mCurrentDownload == null) {
                data.mCurrentDownload = Download()
                data.currentDownload.downloadPath = saveFile.absolutePath
                data.currentDownload.name = data.queue.episode.name
                data.currentDownload.slug = data.queue.episode.slug
                data.currentDownload.seriesSlug = data.queue.episode.seriesSlug
                data.currentDownload.resolution = qualityOption.resolution
                data.currentDownload.dateAdded = System.currentTimeMillis()
                data.currentDownload.fileSize = 0
                data.currentDownload.queued = true
                Core.child.addDownload(data.currentDownload)
                data.info("Created new download for movie.")
            } else {
                data.info("Using existing download for movie.")
            }
            data.driver.navigate().to(downloadLink)
            val fileSizeRetries = Defaults.FILE_SIZE_RETRIES.int()
            var originalFileSize = 0L
            var headMode = true
            data.info("Checking movie file size with $fileSizeRetries retries.")
            for (i in 0..fileSizeRetries) {
                originalFileSize = fileSize(
                    downloadLink,
                    data.userAgent,
                    headMode
                )
                if (originalFileSize <= Constants.minFileSize) {
                    headMode = headMode.not()
                    if (i == fileSizeRetries / 2) {
                        data.message(
                            "Failed to find movie file size. Current retries: $i"
                        )
                    }
                    continue
                } else {
                    break
                }
            }
            if (originalFileSize <= Constants.minFileSize) {
                if (data.downloadDatas.isNotEmpty()) {
                    data.error(
                        "Failed to find movie file size after $fileSizeRetries retries. | Using another quality."
                    )
                    if (preferredDownload != null) {
                        data.downloadDatas.remove(
                            preferredDownload
                        )
                    }
                    data.retries++
                } else {
                    data.error(
                        "Failed to find movie file size after $fileSizeRetries retries. | Skipping movie..."
                    )
                    data.finishEpisode()
                }
                return@withContext
            }
            data.debug("Successfully found movie file size of: ${Tools.bytesToString(originalFileSize)}")
            if (saveFile.exists()) {
                if (saveFile.length() >= originalFileSize) {
                    data.message("[IO] Skipping completed movie.")
                    data.currentDownload.downloadPath = saveFile.absolutePath
                    data.currentDownload.fileSize = originalFileSize
                    data.currentDownload.update()
                    BoxMaker.makeDownloadedEpisode(data.currentDownload.slug)
                    data.finishEpisode()
                    return@withContext
                }
            } else {
                var fileRetries = 0
                var fileError: Exception? = null
                for (i in 0..3) {
                    try {
                        val created = saveFile.createNewFile()
                        if (!created) {
                            throw Exception("No error thrown. $i")
                        }
                    } catch (e: Exception) {
                        fileError = e
                        fileRetries++
                        continue
                    }
                }
                if (!saveFile.exists()) {
                    data.error(
                        "Failed to create movie video file after 3 retries. | Skipping movie...",
                        fileError,
                        true
                    )
                    data.finishEpisode()
                    return@withContext
                }
            }
            data.message("Starting movie download with ${qualityOption.tag} quality.")
            data.currentDownload.queued = false
            data.currentDownload.downloading = true
            data.currentDownload.fileSize = originalFileSize
            Core.child.addDownload(data.currentDownload)
            data.currentDownload.update()
            //data.driver.navigate().back()
            data.undriver.blank()
            downloadVideo(downloadLink, saveFile, data)
            data.currentDownload.downloading = false
            //second time to ensure ui update
            data.currentDownload.update()
            if (saveFile.exists() && saveFile.length() >= originalFileSize) {
                Core.child.downloadThread.incrementDownloadsFinished()
                data.message("Successfully downloaded movie.")
                BoxMaker.makeDownloadedEpisode(
                    data.currentDownload.slug
                )
                data.finishEpisode()
            }
        } catch (e: Exception) {
            data.currentDownload.queued = true
            data.currentDownload.downloading = false
            data.currentDownload.update()
            data.message(
                "Failed to download movie. | Retrying..."
            )
            data.error(
                "Failed to download movie.",
                e
            )
        }
    }
}