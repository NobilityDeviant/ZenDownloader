package nobility.downloader.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.entities.Episode
import nobility.downloader.core.settings.Defaults
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.ui.windows.utils.AppWindowScope
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.*
import java.net.URI
import java.nio.file.Files
import java.text.CharacterIterator
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.text.StringCharacterIterator
import java.util.*
import java.util.regex.Pattern
import javax.net.ssl.HttpsURLConnection


object Tools {

    private const val DATE_FORMAT = "MM/dd/yyyy hh:mm:ssa"

    data class FileMetaData(
        val name: String,
        val season: String,
        val episodeNumber: Int
    )

    //i tried, couldn't figure it out :(
    @Suppress("UNUSED")
    fun addMetaDataToFile(
        file: File,
        metaData: FileMetaData
    ) {
        Files.setAttribute(
            file.toPath(),
            "user:title",
            metaData.name
        )
    }

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
    fun fixTitle(title: String, replaceDotAtStart: Boolean = false): String {
        var mTitle = title
        if (replaceDotAtStart) {
            if (mTitle.startsWith(".")) {
                mTitle = mTitle.substring(1)
            }
        }
        //add a 0 before single numbers for roku media player sorting.
        val digit = mTitle.filter { it.isDigit() }.toIntOrNull()
        if (digit != null) {
            if (digit < 10) {
                mTitle = mTitle.replace("$digit", "0$digit")
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

    fun findSeasonFromEpisode(title: String): String {
        val hasSeason = title.contains("Season")
        if (hasSeason) {
            val match = Regex("Season(?:\\s|\\s?[:/]\\s?)\\d+").find(title)
            if (match != null) {
                val digit = title.filter { it.isDigit() }.toIntOrNull()
                if (digit != null) {
                    if (digit < 10) {
                        //add a 0 before single numbers for roku media player sorting.
                        return title.substring(
                            match.range
                        ).replace("$digit", "0$digit")
                    }
                }
                return title.substring(match.range)
            } else {
                return "Season 01"
            }
        } else {
            return "Season 01"
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
                    true
                )
            )
        ).trim() + ".jpg"
    }

    val percentFormat = DecimalFormat("#.##%")

    fun bytesToMB(bytes: Long): Double {
        val kb = (bytes / 1024L).toInt()
        return (kb / 1024L).toDouble()
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

    fun dateFormatted(time: Long): String {
        val sdf = SimpleDateFormat(DATE_FORMAT)
        return sdf.format(time).replace(" ", "\n")
    }

    val date: String
        get() {
            val c = Calendar.getInstance()
            val day = c[Calendar.DAY_OF_MONTH]
            val year = c[Calendar.YEAR]
            val month = c[Calendar.MONTH] + 1
            return "$month/$day/$year"
        }

    val currentTime: String
        get() {
            val c = Calendar.getInstance()
            val hour = c[Calendar.HOUR]
            val minute = c[Calendar.MINUTE]
            return "$hour:$minute"
        }

    val baseEpisodesComparator = Comparator { e1: Episode, e2: Episode ->
        val seasonKey = "Season"
        val ovaKey = "OVA"
        val first = e1.name
        val second = e2.name
        if (
            !first.contains(seasonKey) && !second.contains(seasonKey)
            && !first.contains(ovaKey) && !second.contains(ovaKey)
        ) {
            return@Comparator episodeCompare(first, second)
        }
        return@Comparator seriesCompare(first, second)
    }

    fun secondsToRemainingTime(totalSeconds: Int): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(
            "%02d:%02d:%02d",
            hours,
            minutes,
            seconds
        ) + " Time Left"
    }

    private fun seriesCompare(
        first: String,
        second: String
    ): Int {
        try {
            val seasonKey = "Season"
            val seasonPattern = Pattern.compile("$seasonKey \\d{0,3}")
            val s1Matcher = seasonPattern.matcher(first)
            val s2Matcher = seasonPattern.matcher(second)
            var s1Number = ""
            var s2Number = ""
            while (s1Matcher.find()) {
                s1Number = s1Matcher.group(0)
            }
            while (s2Matcher.find()) {
                s2Number = s2Matcher.group(0)
            }
            if (s1Number.isNotEmpty() && s2Number.isEmpty()) {
                return 1
            } else if (s2Number.isNotEmpty() && s1Number.isEmpty()) {
                return -1
            }
            if (s1Number.isNotEmpty() && s2Number.isNotEmpty()) {
                return s1Number.filter { it.isDigit() }.toInt()
                    .compareTo(s2Number.filter { it.isDigit() }.toInt())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 0
    }

    private fun episodeCompare(
        first: String,
        second: String
    ): Int {
        try {
            val episodeKey = "Episode"
            val episodePattern = Pattern.compile("$episodeKey \\d{0,3}")
            val e1Matcher = episodePattern.matcher(first)
            val e2Matcher = episodePattern.matcher(second)
            var e1Number = ""
            var e2Number = ""
            while (e1Matcher.find()) {
                e1Number = e1Matcher.group(0)
            }
            while (e2Matcher.find()) {
                e2Number = e2Matcher.group(0)
            }
            if (e1Number.isNotBlank() && e2Number.isBlank()) {
                return 1
            } else if (e2Number.isNotBlank() && e1Number.isBlank()) {
                return -1
            }
            if (e1Number.isNotBlank() && e2Number.isNotBlank()) {
                val e1NumberDigits = e1Number.filter { it.isDigit() }.toIntOrNull()
                val e2NumberDigits = e2Number.filter { it.isDigit() }.toIntOrNull()
                if (e1NumberDigits != null && e2NumberDigits != null) {
                    return e1NumberDigits.compareTo(e2NumberDigits)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 0
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
        con.addRequestProperty("Accept-Encoding", "gzip, deflate, br")
        con.addRequestProperty("Accept-Language", "en-US,en;q=0.9")
        con.addRequestProperty("Connection", "keep-alive")
        con.addRequestProperty("Sec-Fetch-Dest", "document")
        con.addRequestProperty("Sec-Fetch-Mode", "navigate")
        con.addRequestProperty("Sec-Fetch-Site", "cross-site")
        con.addRequestProperty("Sec-Fetch-User", "?1")
        con.addRequestProperty("Upgrade-Insecure-Requests", "1")
        con.connectTimeout = timeout
        con.readTimeout = timeout
        con.setRequestProperty("Range", "bytes=$offset-")
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

    fun openFolder(
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
            DialogHelper.showError("Failed to open folder.", e)
        }
    }
}