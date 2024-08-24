package nobility.downloader.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.res.useResource
import kotlinx.coroutines.launch
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Series
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.net.URI
import javax.imageio.ImageIO

object ImageUtils {

    fun loadSeriesImageFromFileWithBackup(
        series: Series
    ): ImageBitmap {
        if (series.imagePath.isEmpty()) {
            return useResource(AppInfo.NO_IMAGE_PATH) {
                ImageIO.read(it).toComposeImageBitmap()
            }
        }
        val bufferedImage: BufferedImage
        try {
            bufferedImage = ImageIO.read(File(series.imagePath))
            return bufferedImage.toComposeImageBitmap()
        } catch (e: IOException) {
            return loadSeriesImageFromLink(series)
        }
    }

    private fun loadSeriesImageFromLink(
        series: Series
    ): ImageBitmap {
        if (series.imageLink.isEmpty()) {
            return useResource(AppInfo.NO_IMAGE_PATH) {
                ImageIO.read(it).toComposeImageBitmap()
            }
        }
        Core.child.taskScope.launch {
            downloadSeriesImage(series)
        }
        val bufferedImage: BufferedImage
        try {
            bufferedImage = ImageIO.read(URI(series.imageLink).toURL())
            return bufferedImage.toComposeImageBitmap()
        } catch (e: IOException) {
            return useResource(AppInfo.NO_IMAGE_PATH) {
                ImageIO.read(it).toComposeImageBitmap()
            }
        }
    }

    fun loadImageFromFileWithBackup(filePath: String, urlBackup: String): ImageBitmap {
        if (filePath.isEmpty()) {
            return useResource(AppInfo.NO_IMAGE_PATH) {
                ImageIO.read(it).toComposeImageBitmap()
            }
        }
        val bufferedImage: BufferedImage
        try {
            bufferedImage = ImageIO.read(File(filePath))
            return bufferedImage.toComposeImageBitmap()
        } catch (e: IOException) {
            return loadImageFromLink(urlBackup)
        }
    }


    private fun loadImageFromLink(link: String): ImageBitmap {
        if (link.isEmpty()) {
            return useResource(AppInfo.NO_IMAGE_PATH) {
                ImageIO.read(it).toComposeImageBitmap()
            }
        }
        val bufferedImage: BufferedImage
        try {
            bufferedImage = ImageIO.read(URI(link).toURL())
            return bufferedImage.toComposeImageBitmap()
        } catch (e: IOException) {
            return useResource(AppInfo.NO_IMAGE_PATH) {
                ImageIO.read(it).toComposeImageBitmap()
            }
        }
    }

    @Suppress("UNUSED")
    fun loadImageFromFilePath(filePath: String): ImageBitmap {
        if (filePath.isEmpty()) {
            return useResource(AppInfo.NO_IMAGE_PATH) {
                ImageIO.read(it).toComposeImageBitmap()
            }
        }
        val bufferedImage: BufferedImage
        try {
            bufferedImage = ImageIO.read(File(filePath))
            return bufferedImage.toComposeImageBitmap()
        } catch (e: IOException) {
            return useResource(AppInfo.NO_IMAGE_PATH) {
                ImageIO.read(it).toComposeImageBitmap()
            }
        }
    }

    suspend fun downloadSeriesImage(series: Series) {
        if (series.imageLink.isEmpty() || series.imageLink == "N/A") {
            return
        }
        val saveFolder = File(BoxHelper.seriesImagesPath)
        if (!saveFolder.exists() && !saveFolder.mkdirs()) {
            FrogLog.writeMessage("Unable to download series image: ${series.imageLink}. Save folder was unable to be created.")
            return
        }
        val saveFile = File(
            "${saveFolder.absolutePath}${File.separator}" +
                    Tools.titleForImages(series.name)
        )
        if (!saveFile.exists()) {
            try {
                Tools.downloadFile(
                    series.imageLink,
                    saveFile
                )
            } catch (e: Exception) {
                FrogLog.logError(
                    "Failed to download image for ${series.imageLink}",
                    e,
                    true
                )
            }
        }
    }
}