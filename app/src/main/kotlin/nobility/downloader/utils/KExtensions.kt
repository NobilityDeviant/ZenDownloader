package nobility.downloader.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import com.materialkolor.hct.Hct
import com.materialkolor.ktx.toColor
import com.materialkolor.ktx.toHct
import io.github.bonigarcia.wdm.WebDriverManager
import io.objectbox.exception.NonUniqueResultException
import io.objectbox.query.Query
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Download
import nobility.downloader.core.settings.Defaults
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import java.io.File

val KeyEvent.isUp: Boolean get() = type == KeyEventType.KeyUp

@Suppress("UNUSED")
fun WebDriver.findElementOrNull(
    by: By,
    printError: Boolean = false
): WebElement? {
    return try {
        findElement(by)
    } catch (e: Exception) {
        if (printError) {
            e.printStackTrace()
        }
        null
    }
}

fun WebDriver.source(): String {
    val src = pageSource
    return if (!src.isNullOrEmpty()) {
        src
    } else {
        ""
    }
}

fun WebDriver.url(): String {
    val url = currentUrl
    return if (!url.isNullOrEmpty()) {
        url
    } else {
        ""
    }
}

fun WebDriver.title(): String {
    val title = title
    return if (!title.isNullOrEmpty()) {
        title
    } else {
        ""
    }
}

fun Color.tone(tone: Double): Color {
    return toHct()
        .withTone(tone)
        .toColor()
}

fun Color.hover(): Color {
    return toHct()
        .withTone(
            if (Defaults.DARK_MODE.boolean()) 70.0 else 30.0
        ).toColor()
}

fun Color.toColorOnThis(otherColor: Color = this): Color {
    val hct = toHct()
    val tone = hct.tone
    val otherHct = otherColor.toHct()
    val otherTone = otherHct.tone
    val onTone = if (tone > 60 || otherTone > 60) 10.0 else 90.0
    val foregroundHct = Hct.from(hct.hue, hct.chroma, onTone)
    return foregroundHct.toColor()
}

fun <T> List<T>.containsOne(other: List<T>): Boolean {
    for (e in other) {
        if (contains(e)) {
            return true
        }
    }
    return false
}

fun String.capitalizeFirst(): String {
    return if (length > 1) {
        first().uppercaseChar() + substring(1).lowercase()
    } else this.uppercase()
}

fun String.normalizeEnumName(): String {
    return this.replace("_", " ")
        .split(" ")
        .joinToString(" ") {
            it.capitalizeFirst()
    }
}

fun String.ordinalIndexOf(searchString: String, ordinal: Int): Int {
    return StringUtils.ordinalIndexOf(this, searchString, ordinal)
}

fun String.domainFromLink(): String {
    return Tools.extractDomainFromLink(this)
}

fun String.fixForFiles(): String {
    return Tools.fixTitle(this)
}

fun String.slugToLink(): String {
    return Core.wcoUrl + this
}

fun String.linkToSlug(): String {
    return Tools.extractSlugFromLink(this)
}

fun String.fixedAnimeSlug(): String {
    return this.replace("anime/", "")
        .removeSeasonExtra()
}

fun String.removeSeasonExtra(): String {
    return this.replace(Regex("/season=[^/&]+&lang=[^/&]+"), "")
}

fun String.fileExists(): Boolean {
    return File(this).exists()
}

fun String.fileIsNotZero(): Boolean {
    val file = File(this)
    return file.exists() && file.length() > 50L
}

fun String.folderIsEmpty(): Boolean {
    val file = File(this)
    if (file.exists() && file.isDirectory) {
        val files = file.listFiles()
        return files?.isEmpty() == true
    }
    return true
}

fun String.isDirectory(): Boolean {
    val file = File(this)
    return file.exists() && file.isDirectory
}

fun <T> Query<T>.findUniqueOrFirst(): T? {
    return try {
        findUnique()
    } catch (_: NonUniqueResultException) {
        findFirst()
    }
}

fun <T> Query<T>.findUniqueOrNull(): T? {
    return try {
        findUnique()
    } catch (_: NonUniqueResultException) {
        null
    }
}

fun Download.update(
    updateProperties: Boolean = true
) {
    Core.child.updateDownloadInDatabase(this, updateProperties)
}

@Suppress("UNUSED")
fun WebDriverManager.clearDriverCacheClean(): WebDriverManager {
    val cacheFolder: File = this.config().cacheFolder
    FileUtils.cleanDirectory(cacheFolder)
    return this
}