package nobility.downloader.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.entities.Series
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

object ImageUtils {

    //it's way faster to preload it and re-use it everywhere.
    val noImage = loadImageBitmap(AppInfo.NO_IMAGE_PATH)

    suspend fun downloadSeriesImage(series: Series) {
        if (series.imageLink.isEmpty() || series.imageLink == "N/A") {
            return
        }
        val saveFolder = File(BoxHelper.seriesImagesPath)
        if (!saveFolder.exists() && !saveFolder.mkdirs()) {
            FrogLog.message("Failed to download series image: ${series.imageLink}. Save folder was unable to be created.")
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

    fun loadImageBitmap(resourcePath: String): ImageBitmap {
        val inputStream = checkNotNull(ClassLoader.getSystemResourceAsStream(resourcePath)) {
            "Resource not found: $resourcePath"
        }
        val bufferedImage: BufferedImage = ImageIO.read(inputStream)
        return bufferedImage.toComposeImageBitmap()
    }

    fun loadPainterFromResource(resourcePath: String): Painter {
        val inputStream = checkNotNull(ClassLoader.getSystemResourceAsStream(resourcePath)) {
            "Resource not found: $resourcePath"
        }
        val image: BufferedImage = ImageIO.read(inputStream)
        return BitmapPainter(image.toComposeImageBitmap())
    }



}