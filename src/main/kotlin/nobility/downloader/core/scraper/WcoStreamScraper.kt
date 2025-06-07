package nobility.downloader.core.scraper

import kotlinx.coroutines.*
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.driver.DriverBaseImpl
import nobility.downloader.utils.FrogLog.message
import nobility.downloader.utils.JavascriptHelper
import nobility.downloader.utils.Tools
import nobility.downloader.utils.source
import org.jsoup.Jsoup
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.net.URLEncoder
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@Suppress("UNUSED")
object WcoStreamScraper {

    //#frameNewcizgifilmuploads0
    //cizgifilmlerizle

    suspend fun spawnMovie() {
        val file = File("./exported-movies.txt")
        val meta = file.readLines().map { VideoMeta.fromLine(it) }
        val random = meta.random()
        val impl = DriverBaseImpl(headless = false)
        val driver = impl.driver
        driver.get("https://www.wcostream.tv/${random.slug}")
        delay(3000)
        impl.executeJs(JavascriptHelper.SPAWN_MOVIE)
    }

    suspend fun spawnVideo() {
        val file = File("./exported-movies.txt")
        val meta = file.readLines().map { VideoMeta.fromLine(it) }
        val random = meta.random()
        val impl = DriverBaseImpl(headless = false)
        val driver = impl.driver
        driver.get("https://www.wcostream.tv/${random.slug}")

        delay(3000)

        impl.executeJs(
            """
    (function() {
        const file = "${URLEncoder.encode(random.embedUrl ?: "", "UTF-8")}";
        const h = "${random.h}";
        const t = "${random.t}";
        const pid = "${random.pid}";
        const playerId = "frameNew" + Math.floor(Math.random() * 100000);
        const iframe = document.createElement("iframe");

        iframe.id = playerId;
        iframe.src = "https://embed.watchanimesub.net/inc/embed/video-js.php?" +
            "file=" + file +
            "&h=" + h +
            "&t=" + t +
            "&pid=" + pid +
            "&fullhd=1&embed=neptun";

        iframe.width = "530";
        iframe.height = "440";
        iframe.frameBorder = "0";
        iframe.scrolling = "no";
        iframe.setAttribute("webkitallowfullscreen", "true");
        iframe.setAttribute("mozallowfullscreen", "true");
        iframe.setAttribute("allowfullscreen", "true");
        iframe.setAttribute("rel", "nofollow");
        iframe.setAttribute("data-type", "wco-embed");

        // Insert it into the DOM somewhere visible
        const container = document.body;
        container.innerHTML = ""; // optional: clear current page
        container.appendChild(iframe);
    })();
    """.trimIndent()
        )
    }

    fun spawnVideo2() {
        val file = File("./exported-movies.txt")
        val meta = file.readLines().map { VideoMeta.fromLine(it) }
        val random = meta.random()

        if (!random.isWritable) {
            println("Selected video meta is incomplete: $random")
            return
        }

        val impl = DriverBaseImpl(headless = false)
        val driver = impl.driver

        driver.get("https://www.wcostream.tv/${random.slug}")

        // Manually build the full embed URL with query parameters
        val embedUrl = buildString {
            append("https://embed.watchanimesub.net/inc/embed/video-js.php?")
            append("file="); append(URLEncoder.encode(random.embedUrl, "UTF-8"))
            append("&h="); append(random.h)
            append("&t="); append(random.t)
            append("&pid="); append(random.pid)
            append("&fullhd=1")
            append("&embed=neptun")
        }

        println("Opening: $embedUrl")

        impl.executeJs(
            JavascriptHelper.changeUrlInternally(embedUrl)
        )
    }



    fun cleanDuplicates() {
        val file = File("./exported-movies.txt")
        val meta = file.readLines().map { VideoMeta.fromLine(it) }
        val filtered = meta.distinctBy { it.slug }.sortedBy { it.slug }
        if (meta.size > filtered.size) {
            val cleaned = File("./exported-movies-clean.txt")
            val bw = cleaned.bufferedWriter()
            filtered.forEach { s ->
                if (s.isWritable) {
                    bw.write(s.toLine)
                    bw.newLine()
                }
            }
            bw.flush()
            bw.close()
            message("Successfully cleaned ${meta.size - filtered.size} duplicates.")
        }
    }

    private data class Details(
        val slug: String,
        val title: String
    )

    suspend fun scrapeDetails() = withContext(Dispatchers.IO) {
        val details = mutableListOf<Details>()
        val impl = DriverBaseImpl(headless = false)
        val driver = impl.driver
        driver.get("https://www.wcostream.tv/movie-list")
        delay(10_000)
        val listDoc = Jsoup.parse(driver.source())
        val list = listDoc.getElementsByClass("ddmcc")
        list.forEach { l ->
            val uls = l.select("ul")
            for (ul in uls) {
                val lis = ul.select("li")
                for (li in lis) {
                    val link = li.select("a").attr("href")
                    val title = li.text()
                    val slug = link.substringAfterLast("/")
                    if (!slug.startsWith("movie-list")) {
                        if (!details.any { it.slug == slug }) {
                            details.add(
                                Details(
                                    slug,
                                    title
                                )
                            )
                        }
                    }
                }
            }
        }
        impl.killDriver()
        if (details.isEmpty()) {
            message(
                "Failed to find any details."
            )
            return@withContext
        }
        message(
            "Successfully found ${details.size} details."
        )
        //details.sortBy { it.title }
        val export = File("./exported-movies.txt")
        val writer = ThreadSafeFileWriter(export.absolutePath)
        val metas = Collections.synchronizedList(
            mutableListOf<VideoMeta>()
        )
        metas.addAll(
            export.readLines().map {
                VideoMeta.fromLine(it)
            }
        )
        val metaSlugs = metas.map { it.slug }
        details.removeAll { metaSlugs.contains(it.slug) }
        val threads = 3
        val lists = details.chunked(details.size / threads)
        val jobs = mutableListOf<Job>()
        message(
            "Spawning $threads tasks to scrape the VideoMeta."
        )
        lists.forEach { l ->
            jobs.add(
                launch {
                    val taskDriver = DriverBaseImpl()
                    l.forEach { detail ->
                        val slug = detail.slug
                        if (!metas.any { it.slug == slug }) {
                            taskDriver.driver.get("https://www.wcostream.tv/$slug")
                            val meta = parseVideoMeta(
                                slug,
                                taskDriver.driver.source()
                            )
                            metas.add(meta)
                            writer.writeLine(meta.toLine)
                        }
                    }
                    message(
                        "Scraped ${l.size} VideoMeta."
                    )
                    taskDriver.killDriver()
                }
            )
        }
        jobs.joinAll()
        writer.close()
        message(
            "Successfully exported all details to: ${export.absolutePath}"
        )
        Tools.openFile(export.absolutePath, true)
    }

    @Suppress("ConstPropertyName")
    private const val del = "<SPL>"

    data class VideoMeta(
        val slug: String,
        val embedUrl: String?,
        val pid: String?,
        val h: String?,
        val t: String?,
        val title: String?,
        val uploadMillis: Long?,
        val frameId: String?
    ) {

        val currentT = (System.currentTimeMillis() / 1000).toString()

        val isWritable: Boolean = embedUrl != null
                && pid != null && h != null
                && t != null && frameId != null

        val toLine
            get() = slug + del + embedUrl + del +
                    pid + del + h + del + t + del + title + del +
                    uploadMillis + del + frameId

        companion object {
            fun fromLine(line: String): VideoMeta {
                val split = line.split(del)
                return VideoMeta(
                    split[0],
                    split[1],
                    split[2],
                    split[3],
                    split[4],
                    split[5],
                    split[6].toLongOrNull(),
                    split[7]
                )
            }
        }

    }

    fun parseTagContent(html: String, key: String): String? {
        val regex = Regex("""<meta\s+itemprop=['"]$key['"]\s+content=['"](.*?)['"]""", RegexOption.IGNORE_CASE)
        return regex.find(html)?.groupValues?.get(1)
    }

    fun extractGroup(pattern: String, input: String): String? {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        return regex.find(input)?.groupValues?.get(1)
    }

    fun parseIsoDateToMillis(isoString: String): Long? {
        return try {
            ZonedDateTime.parse(isoString, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }

    fun parseVideoMeta(
        slug: String,
        html: String
    ): VideoMeta {

        val frameId = extractGroup(
            """\$\s*\(\s*"#([^"]+)"\s*\)\s*\.attr\s*\(\s*"src"""",
            html
        )
        val embedUrl = parseTagContent(html, "embedURL")?.replace("&amp;", "&")
        val title = parseTagContent(html, "name")

        val uploadDate = parseTagContent(html, "uploadDate")
        val uploadMillis = uploadDate?.let { parseIsoDateToMillis(it) }

        val pid = extractGroup("""[?&]pid=(\d+)""", html)
        val h = extractGroup("""[?&]h=([a-f0-9]+)""", html)
        val t = extractGroup("""[?&]t=(\d+)""", html)

        return VideoMeta(
            slug = slug,
            embedUrl = embedUrl,
            pid = pid,
            h = h,
            t = t,
            title = title,
            uploadMillis = uploadMillis,
            frameId
        )
    }


    suspend fun run() {
        delay(500)
        val impl = DriverBaseImpl(headless = false)
        val driver = impl.driver
        val random = BoxHelper.shared.moviesSeriesBox.all.random()
        val link = "https://www.wcostream.tv/${random.slug}"
        driver.get(link)
        delay(10000)
        impl.killDriver()
    }

    class ThreadSafeFileWriter(filePath: String) {
        private val file = File(filePath)
        private val writer: BufferedWriter = BufferedWriter(FileWriter(file, true))
        private val lock = Any()

        fun writeLine(line: String) {
            synchronized(lock) {
                writer.write(line)
                writer.newLine()
                writer.flush()
            }
        }

        fun close() {
            synchronized(lock) {
                writer.close()
            }
        }
    }


}