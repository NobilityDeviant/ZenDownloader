package nobility.downloader.utils

import nobility.downloader.core.BoxHelper
import nobility.downloader.core.entities.Series
import java.io.File

object ImageUtils {

    suspend fun downloadSeriesImage(series: Series) {
        if (series.imageLink.isEmpty() || series.imageLink == "N/A") {
            return
        }
        val saveFolder = File(BoxHelper.seriesImagesPath)
        if (!saveFolder.exists() && !saveFolder.mkdirs()) {
            FrogLog.writeMessage("Failed to download series image: ${series.imageLink}. Save folder was unable to be created.")
            return
        }
        val saveFile = File(series.imagePath)
        if (!saveFile.exists() || saveFile.length() <= 50L) {
            try {
                //println("Downloading image: ${series.imageLink}")
                Tools.downloadFileWithRetries(
                    series.imageLink,
                    saveFile
                )
            } catch (e: Exception) {
                FrogLog.logError(
                    "Failed to download image for ${series.imageLink}",
                    e
                )
            }
        }
    }

}