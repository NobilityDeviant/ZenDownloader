package nobility.downloader.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.entities.Download
import nobility.downloader.core.entities.Episode
import nobility.downloader.core.scraper.video_download.Functions
import nobility.downloader.core.settings.Defaults
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.ui.windows.utils.AppWindowScope
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.*
import java.net.URI
import java.text.CharacterIterator
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.text.StringCharacterIterator
import java.util.*
import java.util.regex.Pattern
import javax.net.ssl.HttpsURLConnection


object Tools {

    private const val DATE_FORMAT = "MM/dd/yyyy hh:mm:ssa"

    val percentFormat: DecimalFormat get() = DecimalFormat("#.##%")

    val currentTime: Long get() = Date().time

    /**
     * Access clipboard outside a composable.
     */
    var clipboardString: String
        get() {
            return try {
                Toolkit.getDefaultToolkit()
                    .systemClipboard
                    .getData(DataFlavor.stringFlavor).toString()
            } catch (e: Exception) {
                FrogLog.logError(
                    "Failed to get clipboard contents.",
                    e
                )
                ""
            }
        }
        set(value) {
            val stringSelection = StringSelection(value)
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(stringSelection, null)
        }

    fun extractSlugFromLink(link: String?): String {
        if (link.isNullOrEmpty()) {
            return ""
        }
        return try {
            link.substring(
                //after the https:// and after the .com/
                link.ordinalIndexOf("/", 3) + 1
            )
        } catch (_: Exception) {
            ""
        }
    }

    fun extractExtensionFromLink(link: String): String {
        return link.substringAfterLast(".").substringBefore("/")
    }

    /**
     * Extracts the domain name without the www. (because it's never needed)
     * and without the extension.
     * Doesn't support subdomains.
     */
    fun extractDomainFromLink(link: String): String {
        val mLink = link.replace("www.", "")
        val key1 = "https://"
        return mLink.substring(
            mLink.indexOf(key1) + key1.length,
            mLink.indexOf(".")
        )
    }

    /**
     * This is used to fix strings in order to be used as files.
     * Files can't contain certain characters.
     */
    fun fixTitle(
        title: String,
        fixNumbers: Boolean = true
    ): String {
        var mTitle = title
        if (mTitle.startsWith(".")) {
            mTitle = mTitle.substring(1)
        }
        if (fixNumbers) {
            val matchSeason = Regex("Season(?:\\s|\\s?[:/]\\s?)\\d+").find(title)
            if (matchSeason != null) {
                val original = matchSeason.value
                val digit = original.filter { it.isDigit() }.toIntOrNull()
                if (digit != null) {
                    if (digit < 10 && !digit.toString().contains("0")) {
                        //add a 0 before single numbers for roku media player sorting.
                        mTitle = mTitle.replace(original, original.replace("$digit", "0$digit"))
                    }
                }
            }
            val matchEpisode = Regex("Episode(?:\\s|\\s?[:/]\\s?)\\d+").find(title)
            if (matchEpisode != null) {
                val original = matchEpisode.value
                val digit = original.filter { it.isDigit() }.toIntOrNull()
                if (digit != null) {
                    if (digit < 10 && !digit.toString().contains("0")) {
                        //add a 0 before single numbers for roku media player sorting.
                        mTitle = mTitle.replace(original, original.replace("$digit", "0$digit"))
                    }
                }
            }
        }
        return mTitle.trim { it <= ' ' }
            .replace("[\\\\/*?\"<>|]".toRegex(), "_")
            .replace(":".toRegex(), ";")
            .replace("ﾒ", "'")
    }

    //used to save/retrieve images for series
    private fun stripExtraFromTitle(title: String): String {
        var mTitle = title
        val subKeyword = "English Subbed"
        val dubKeyword = "English Dubbed"
        if (mTitle.contains(subKeyword)) {
            mTitle = mTitle.substringBefore(subKeyword)
        } else if (mTitle.contains(dubKeyword)) {
            mTitle = mTitle.substringBefore(dubKeyword)
        }
        return mTitle.trim()
    }

    //should only be used after fixing the title for files.
    fun findSeasonFromEpisode(title: String): String {
        val hasSeason = title.contains("Season")
        return if (hasSeason) {
            val match = Regex("Season(?:\\s|\\s?[:/]\\s?)\\d+").find(title)
            if (match != null) {
                title.substring(match.range)
            } else {
                "Season 01"
            }
        } else {
            "Season 01"
        }
    }

    //used to fetch image from files for an episode
    private fun seriesNameFromEpisode(title: String): String {
        var mTitle = title
        val episodeKeyword = "Episode"
        if (mTitle.contains(episodeKeyword)) {
            mTitle = mTitle.substringBefore(episodeKeyword)
        }
        return mTitle
    }

    fun titleForImages(title: String): String {
        return seriesNameFromEpisode(
            stripExtraFromTitle(
                fixTitle(
                    title,
                    false
                )
            )
        ).trim() + ".jpg"
    }

    fun bytesToString(bytes: Long): String {
        var mBytes = bytes
        if (-1000 < mBytes && mBytes < 1000) {
            return "$mBytes B"
        }
        val ci: CharacterIterator = StringCharacterIterator("kMGTPE")
        while (mBytes <= -999950 || mBytes >= 999950) {
            mBytes /= 1000
            ci.next()
        }
        return String.format("%.1f %cB", mBytes / 1000.0, ci.current())
    }

    val dateFormatted: String
        get() {
            val sdf = SimpleDateFormat(DATE_FORMAT)
            return sdf.format(Date())
        }

    fun dateFormatted(
        time: Long,
        newlineForTime: Boolean = true
    ): String {
        val sdf = SimpleDateFormat(DATE_FORMAT)
        return if (newlineForTime)
            sdf.format(time).replace(" ", "\n")
        else
            sdf.format(time)
    }

    val downloadProgressComparator = Comparator<Download> { d1, d2 ->

        if (d1.downloading && !d2.downloading) {
            return@Comparator -1
        }
        if (!d1.downloading && d2.downloading) {
            return@Comparator 1
        }

        val d1InProgress = d1.downloadFile() != null && !d1.isComplete
        val d2InProgress = d2.downloadFile() != null && !d2.isComplete

        if (d1InProgress && !d2InProgress) {
            return@Comparator -1
        }
        if (!d1InProgress && d2InProgress) {
            return@Comparator 1
        }

        return@Comparator 0
    }


    val baseEpisodesComparator = Comparator { e1: Episode, e2: Episode ->
        val seasonKey = "Season"
        val ovaKey = "OVA"
        val movieKey = "Movie"
        val filmKey = "Film"
        val specialKey = "Special"
        val first = e1.name
        val second = e2.name
        if (first.contains(filmKey, true) && !second.contains(filmKey, true)) {
            return@Comparator -1
        }
        if (!first.contains(filmKey, true) && second.contains(filmKey, true)) {
            return@Comparator 1
        }
        if (first.contains(movieKey, true) && !second.contains(movieKey, true)) {
            return@Comparator -1
        }
        if (!first.contains(movieKey, true) && second.contains(movieKey, true)) {
            return@Comparator 1
        }
        if (first.contains(ovaKey, true) && !second.contains(ovaKey, true)) {
            return@Comparator -1
        }
        if (!first.contains(ovaKey, true) && second.contains(ovaKey, true)) {
            return@Comparator 1
        }
        if (first.contains(specialKey, true) && !second.contains(specialKey, true)) {
            return@Comparator -1
        }
        if (!first.contains(specialKey, true) && second.contains(specialKey, true)) {
            return@Comparator 1
        }
        if (
            !first.contains(seasonKey, true)
            && !second.contains(seasonKey, true)
        ) {
            return@Comparator episodeCompare(first, second)
        }
        return@Comparator seriesCompare(first, second)
    }

    fun secondsToRemainingTime(totalSeconds: Int): String {
        if (totalSeconds <= 0) {
            return "0:00"
        }
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        if (hours <= 0 && minutes > 0) {
            return " (${if (minutes < 10) "0" else ""}$minutes:${if (seconds < 10) "0" else ""}$seconds)"
        } else if (minutes <= 0 && hours <= 0) {
            return " (0:${if (seconds < 10) "0" else ""}$seconds)"
        }
        return " (" + String.format(
            "%02d:%02d:%02d",
            hours,
            minutes,
            seconds
        ) + ")"
    }

    @Suppress("KotlinConstantConditions")
    private fun seriesCompare(
        first: String,
        second: String
    ): Int {
        try {
            val seasonKey = "Season"
            val seasonPattern = Pattern.compile("(?i)$seasonKey \\d{0,3}")
            val s1Matcher = seasonPattern.matcher(first)
            val s2Matcher = seasonPattern.matcher(second)
            var s1Number: String? = ""
            var s2Number: String? = ""
            while (s1Matcher.find()) {
                s1Number = s1Matcher.group(0)
            }
            while (s2Matcher.find()) {
                s2Number = s2Matcher.group(0)
            }
            if (!s1Number.isNullOrBlank() && s2Number.isNullOrBlank()) {
                return 1
            } else if (!s2Number.isNullOrBlank() && s1Number.isNullOrBlank()) {
                return -1
            }
            if (!s1Number.isNullOrBlank() && !s2Number.isNullOrBlank()) {
                val s1Filtered = s1Number.filter { it.isDigit() }.toIntOrNull()
                val s2Filtered = s2Number.filter { it.isDigit() }.toIntOrNull()
                if (s1Filtered != null && s2Filtered != null) {
                    return s1Filtered.compareTo(s2Filtered)
                } else if (s1Filtered == null && s2Filtered != null) {
                    return -1
                    //false flag? I don't see anything wrong with this check.
                } else if (s1Filtered != null && s2Filtered == null) {
                    return 1
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 0
    }

    @Suppress("KotlinConstantConditions")
    private fun episodeCompare(
        first: String,
        second: String
    ): Int {
        try {
            val episodeKey = "Episode"
            val episodePattern = Pattern.compile("(?i)$episodeKey \\d{0,4}")
            val e1Matcher = episodePattern.matcher(first)
            val e2Matcher = episodePattern.matcher(second)
            var e1Number: String? = ""
            var e2Number: String? = ""
            while (e1Matcher.find()) {
                e1Number = e1Matcher.group(0)
            }
            while (e2Matcher.find()) {
                e2Number = e2Matcher.group(0)
            }
            if (!e1Number.isNullOrBlank() && e2Number.isNullOrBlank()) {
                return 1
            } else if (!e2Number.isNullOrBlank() && e1Number.isNullOrBlank()) {
                return -1
            }
            if (!e1Number.isNullOrBlank() && !e2Number.isNullOrBlank()) {
                val e1Filtered = e1Number.filter { it.isDigit() }.toIntOrNull()
                val e2Filtered = e2Number.filter { it.isDigit() }.toIntOrNull()
                if (e1Filtered != null && e2Filtered != null) {
                    return e1Filtered.compareTo(e2Filtered)
                } else if (e1Filtered == null && e2Filtered != null) {
                    return -1
                    //false flag? I don't see anything wrong with this check.
                } else if (e1Filtered != null && e2Filtered == null) {
                    return 1
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 0
    }

    suspend fun downloadFileWithRetries(
        link: String,
        output: File,
        timeout: Int = Defaults.TIMEOUT.int() * 1000,
        userAgent: String = UserAgents.random,
        retries: Int = 5
    ) = withContext(Dispatchers.IO) {
        var headMode = true
        for (i in 0..retries) {
            try {
                Functions.fileSize(link, userAgent, headMode)
                downloadFile(
                    link,
                    output,
                    timeout,
                    userAgent
                )
                i
            } catch (_: Exception) {}
            if (output.length() > 50L) {
                break
            } else {
                headMode = headMode.not()
            }
        }
    }

    suspend fun downloadFile(
        link: String,
        output: File,
        timeout: Int = Defaults.TIMEOUT.int() * 1000,
        userAgent: String = UserAgents.random
    ) = withContext(Dispatchers.IO) {
        var offset = 0L
        if (output.exists()) {
            offset = output.length()
        }
        val con = URI(link).toURL().openConnection() as HttpsURLConnection
        con.addRequestProperty(
            "Accept",
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
        )
        if (!output.name.endsWith(".txt")) {
            con.addRequestProperty("Accept-Encoding", "gzip, deflate, br")
        }
        con.addRequestProperty("Accept-Language", "en-US,en;q=0.9")
        //not even sure if these are needed
        con.addRequestProperty("Connection", "keep-alive")
        con.addRequestProperty("Sec-Fetch-Dest", "document")
        con.addRequestProperty("Sec-Fetch-Mode", "navigate")
        con.addRequestProperty("Sec-Fetch-Site", "cross-site")
        con.addRequestProperty("Sec-Fetch-User", "?1")
        con.addRequestProperty("Upgrade-Insecure-Requests", "1")
        con.connectTimeout = timeout
        con.readTimeout = timeout
        con.addRequestProperty("Range", "bytes=$offset-")
        con.addRequestProperty("User-Agent", userAgent)
        val buffer = ByteArray(8192)
        val bis = BufferedInputStream(con.inputStream)
        val fos = FileOutputStream(output, true)
        val bos = BufferedOutputStream(fos, buffer.size)
        var count: Int
        var total = offset
        while (bis.read(buffer, 0, 8192).also { count = it } != -1) {
            total += count.toLong()
            bos.write(buffer, 0, count)
        }
        bos.flush()
        bos.close()
        fos.flush()
        fos.close()
        bis.close()
        con.disconnect()
    }

    fun openFile(
        path: String,
        parent: Boolean = false,
        appWindowScope: AppWindowScope? = null
    ) {
        var file = File(path)
        if (!file.exists()) {
            appWindowScope?.showToast("This file doesn't exist.")
            return
        }
        if (parent) {
            file = if (file.parentFile.exists()) {
                file.parentFile
            } else {
                appWindowScope?.showToast("This file's parent doesn't exist.")
                return
            }
        }
        try {
            Desktop.getDesktop().open(file)
        } catch (e: IOException) {
            DialogHelper.showError("Failed to open file.", e)
        }
    }

    fun removeDuplicateWord(
        input: String,
        targetWord: String
    ): String {
        val regex = "\\b${Regex.escape(targetWord)}\\b".toRegex(RegexOption.IGNORE_CASE)
        var found = false

        val result = regex.replace(input) { matchResult ->
            if (!found) {
                found = true
                matchResult.value
            } else {
                ""
            }
        }
        return result.replace("\\s{2,}".toRegex(), " ").trim()
    }

    enum class ColorStyle {
        ANY,
        PASTEL,
        NEON
    }

    fun randomColor(style: ColorStyle = ColorStyle.ANY): Color {
        val hue = kotlin.random.Random.nextFloat() * 360f
        val (saturation, lightness) = when (style) {
            ColorStyle.PASTEL -> {
                val sat = 0.3f + kotlin.random.Random.nextFloat() * 0.2f
                val light = 0.7f + kotlin.random.Random.nextFloat() * 0.2f
                sat to light
            }
            ColorStyle.NEON -> {
                val sat = 0.9f + kotlin.random.Random.nextFloat() * 0.1f
                val light = 0.5f + kotlin.random.Random.nextFloat() * 0.2f
                sat to light
            }
            ColorStyle.ANY -> {
                val sat = 0.4f + kotlin.random.Random.nextFloat() * 0.6f
                val light = 0.3f + kotlin.random.Random.nextFloat() * 0.6f
                sat to light
            }
        }

        return Color.hsl(
            hue,
            saturation,
            lightness,
            1f,
            ColorSpaces.Srgb
        )
    }

}