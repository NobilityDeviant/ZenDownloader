package nobility.downloader.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.res.useResource
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.entities.Series
import java.io.File
import java.net.URI
import javax.imageio.ImageIO

object ImageUtils {

    private fun bitmapForPath(path: String): ImageBitmap? {
        if (path.isEmpty()) {
            return null
        }
        try {
            val bufferedImage = ImageIO.read(File(path))
            if (bufferedImage != null) {
                return bufferedImage.toComposeImageBitmap()
            }
        } catch (_: Exception) {}
        return null
    }

    private fun bitmapForLink(url: String): ImageBitmap? {
        if (url.isEmpty()) {
            return null
        }
        try {
            val bufferedImage = ImageIO.read(URI(url).toURL())
            if (bufferedImage != null) {
                return bufferedImage.toComposeImageBitmap()
            }
        } catch (_: Exception) {}
        return null
    }

    @Suppress("UNUSED")
    fun seriesImageBitmap(
        series: Series
    ): ImageBitmap {
        val fileBitmap = bitmapForPath(series.imagePath)
        return if (fileBitmap != null) {
            return fileBitmap
        } else {
            val linkBitmap = bitmapForLink(series.imageLink)
            return linkBitmap ?: noImage
        }
    }

    @Suppress("UNUSED")
    fun fileImageBitmap(
        filePath: String,
        urlBackup: String? = null
    ): ImageBitmap {
        val fileBitmap = bitmapForPath(filePath)
        return if (fileBitmap != null) {
            return fileBitmap
        } else {
            if (urlBackup != null) {
                val linkBitmap = bitmapForLink(urlBackup)
                return linkBitmap ?: noImage
            } else {
                return noImage
            }
        }
    }

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
                println("Downloading image: ${series.imageLink}")
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

    val noImage get() = useResource(AppInfo.NO_IMAGE_PATH) {
        ImageIO.read(it).toComposeImageBitmap()
    }
}