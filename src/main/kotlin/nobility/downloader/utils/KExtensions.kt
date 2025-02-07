package nobility.downloader.utils

import androidx.compose.ui.graphics.Color
import com.materialkolor.ktx.toColor
import com.materialkolor.ktx.toHct
import io.github.bonigarcia.wdm.WebDriverManager
import io.objectbox.exception.NonUniqueResultException
import io.objectbox.query.Query
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.Core
import nobility.downloader.core.settings.Defaults
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.openqa.selenium.WebDriver
import java.io.File

//fix the new selenium pageSource being nullable.
//why?
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
    return this.replace("_", " ").split(" ").joinToString(" ") {
        it.capitalizeFirst()
    }
}

fun String.ordinalIndexOf(searchString: String, ordinal: Int): Int {
    return StringUtils.ordinalIndexOf(this, searchString, ordinal)
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

fun String.fixedSlug(): String {
    return this.replace("anime/", "")
}

fun String.fileExists(): Boolean {
    return File(this).exists()
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

@Suppress("UNUSED")
fun WebDriverManager.clearDriverCacheClean(): WebDriverManager {
    val cacheFolder: File = this.config().cacheFolder
    FileUtils.cleanDirectory(cacheFolder)
    return this
}