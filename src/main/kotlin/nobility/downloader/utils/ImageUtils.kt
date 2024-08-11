package nobility.downloader.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.res.useResource
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.net.URI
import javax.imageio.ImageIO

object ImageUtils {

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


    fun loadImageFromLink(link: String): ImageBitmap {
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
}