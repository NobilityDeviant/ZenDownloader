package nobility.downloader.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toLowerCase
import com.materialkolor.ktx.toColor
import com.materialkolor.ktx.toHct
import io.objectbox.exception.NonUniqueResultException
import io.objectbox.query.Query
import nobility.downloader.core.Core
import org.apache.commons.lang3.StringUtils
import java.io.File

fun Color.tone(tone: Double): Color {
    return toHct()
        .withTone(tone)
        .toColor()
}

fun Color.light(): Color {
    return toHct()
        .withTone(70.0)
        .toColor()
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
        first().uppercaseChar() + substring(1).toLowerCase(Locale.current)
    } else this
}

fun String.ordinalIndexOf(searchString: String, ordinal: Int): Int {
    return StringUtils.ordinalIndexOf(this, searchString, ordinal)
}

fun String.fixForFiles(replaceDot: Boolean = true): String {
    return Tools.fixTitle(this, replaceDot)
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
    } catch (e: NonUniqueResultException) {
        findFirst()
    }
}

fun <T> Query<T>.findUniqueOrNull(): T? {
    return try {
        findUnique()
    } catch (e: NonUniqueResultException) {
        null
    }
}