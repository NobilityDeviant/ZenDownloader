package nobility.downloader.core.scraper.video_download.m3u8_downloader.core

import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Preconditions
import org.apache.commons.lang3.StringUtils
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

class M3u8Store(
    val m3u8Uri: URI?,
    val finalM3u8Uri: URI?,
    val masterM3u8Uri: URI?,
    private val finalM3u8Content: String?,
    private val masterM3u8Content: String?
) {
    @Throws(IOException::class)
    fun store(m3u8StorePath: Path) {
        Preconditions.checkArgument(Files.notExists(m3u8StorePath))
        val properties = Properties()
        run {
            setUri(properties, "m3u8Uri", this.m3u8Uri)
            setUri(properties, "masterM3u8Uri", this.masterM3u8Uri)
            setStr(properties, "masterM3u8Content", this.masterM3u8Content)

            setUri(properties, "finalM3u8Uri", this.finalM3u8Uri)
            setStr(properties, "finalM3u8Content", this.finalM3u8Content)
        }

        Files.newOutputStream(m3u8StorePath).use { outputStream ->
            properties.storeToXML(outputStream, "m3u8Store")
        }
    }

    private fun setUri(properties: Properties, name: String, uri: URI?) {
        Optional.ofNullable(uri).ifPresent { u: URI -> properties.setProperty(name, u.toString()) }
    }

    private fun setStr(
        properties: Properties,
        name: String,
        prop: String?
    ) {
        Optional.ofNullable(prop).ifPresent { p: String? -> properties.setProperty(name, p) }
    }

    fun toPlainString(): String {
        return "M3u8Store{" +
                "m3u8Uri=" + m3u8Uri +
                ", finalM3u8Uri=" + finalM3u8Uri +
                ", masterM3u8Uri=" + masterM3u8Uri +
                '}'
    }

    companion object {
        @Throws(IOException::class)
        fun load(m3u8StorePath: Path): M3u8Store {
            Preconditions.checkArgument(Files.isRegularFile(m3u8StorePath))
            val properties = Properties()
            Files.newInputStream(m3u8StorePath).use { inputStream ->
                properties.loadFromXML(inputStream)
            }
            val m3u8Uri = loadUri(properties, "m3u8Uri")
            val finalM3u8Uri = loadUri(properties, "finalM3u8Uri")
            val masterM3u8Uri = loadUri(properties, "masterM3u8Uri")

            val finalM3u8Content = properties.getProperty("finalM3u8Content")
            val masterM3u8Content = properties.getProperty("masterM3u8Content")

            return M3u8Store(m3u8Uri, finalM3u8Uri, masterM3u8Uri, finalM3u8Content, masterM3u8Content)
        }

        private fun loadUri(properties: Properties, name: String): URI? {
            val property = properties.getProperty(name)
            if (StringUtils.isBlank(property)) {
                return null
            }
            return URI.create(property)
        }
    }
}
