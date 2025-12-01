package nobility.downloader.utils

import androidx.compose.material3.MaterialTheme
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
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Download
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import java.io.File
import kotlin.math.roundToInt

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
            if (Core.darkMode.value) 40.0 else 70.0
        ).toColor().copy(alpha = 0.9f)
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
    val mLink = replace("www.", "")
    val key1 = "https://"
    return mLink.substring(
        mLink.indexOf(key1) + key1.length,
        mLink.indexOf(".")
    )
}

fun String.fixForFiles(): String {
    return Tools.fixTitle(this)
}

fun String.fixedSlug(): String {
    var s = this
    if (s.startsWith("/")) {
        s = s.substring(1)
    }
    if (s.endsWith("/") || s.contains("season=")) {
        s = s.substringBeforeLast("/")
    }
    return s
}

fun String.slugToLink(): String {
    return Core.wcoUrl + this.fixedSlug()
}

fun String.linkToSlug(): String {
    if (isNullOrEmpty()) {
        return ""
    }
    if (!Tools.isUrl(this)) {
        return this.fixedSlug()
    }
    return try {
        var s = substring(
            ordinalIndexOf("/", 3) + 1
        ).substringBeforeLast("/")
        if (s.startsWith("/")) {
            s = s.substring(1)
        }
        return s
    } catch (_: Exception) {
        ""
    }
}

fun String.fixedAnimeSlug(): String {
    return this.replace("anime/", "")
        .fixedSlug()
}

fun String.fileExists(): Boolean {
    return if (isNotEmpty())
        File(this).exists()
    else false
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

fun Color.colorName(): String {

    val hct = toHct()
    val tone = hct.tone
    val chroma = hct.chroma
    val hue = hct.hue.roundToInt()

    if (chroma < 8) {
        return when {
            tone < 12 -> "Black"
            tone > 93 -> "White"
            tone < 25 -> "Dark Gray"
            tone < 50 -> "Medium Gray"
            tone < 75 -> "Light Gray"
            else -> "Soft Gray"
        }
    }

    val base = when (hue) {
        in 0..5 -> "Crimson Red"
        in 6..10 -> "Cherry Red"
        in 11..15 -> "Apple Red"
        in 16..20 -> "Strawberry Red"
        in 21..25 -> "Coral Orange"
        in 26..30 -> "Tangerine Orange"
        in 31..35 -> "Pumpkin Orange"
        in 36..40 -> "Sunset Orange"
        in 41..45 -> "Amber Orange"
        in 46..50 -> "Golden Yellow"
        in 51..55 -> "Honey Yellow"
        in 56..60 -> "Canary Yellow"
        in 61..65 -> "Lemon Yellow"
        in 66..70 -> "Chartreuse Green"
        in 71..75 -> "Lime Green"
        in 76..80 -> "Spring Green"
        in 81..85 -> "Mint Green"
        in 86..90 -> "Seafoam Green"
        in 91..100 -> "Jade Green"
        in 101..110 -> "Emerald Green"
        in 111..120 -> "Forest Green"
        in 121..130 -> "Moss Green"
        in 131..140 -> "Olive Green"
        in 141..150 -> "Teal Green"
        in 151..160 -> "Cyan Green"
        in 161..170 -> "Turquoise Cyan"
        in 171..180 -> "Aquamarine Cyan"
        in 181..190 -> "Ocean Blue"
        in 191..200 -> "Sky Cyan"
        in 201..210 -> "Arctic Blue"
        in 211..220 -> "Azure Blue"
        in 221..230 -> "Sapphire Blue"
        in 231..240 -> "Royal Blue"
        in 241..250 -> "Indigo Blue"
        in 251..260 -> "Periwinkle Blue"
        in 261..270 -> "Lavender Purple"
        in 271..280 -> "Violet Purple"
        in 281..290 -> "Orchid Purple"
        in 291..300 -> "Amethyst Purple"
        in 301..310 -> "Mulberry Purple"
        in 311..320 -> "Magenta Pink"
        in 321..330 -> "Fuchsia Pink"
        in 331..340 -> "Rose Pink"
        in 341..350 -> "Carnation Pink"
        in 351..355 -> "Watermelon Red"
        in 356..360 -> "Ruby Red"
        else -> "Mystic Hue"
    }

    val toneModifier = when {
        tone <= 20 -> "Dark"
        tone <= 40 -> "Dim"
        tone <= 60 -> ""
        tone <= 80 -> "Light"
        else -> "Pale"
    }

    val chromaModifier = when {
        chroma < 20 -> "Greyish"
        chroma > 80 -> "Vibrant"
        else -> ""
    }

    val modifiers = listOf(toneModifier, chromaModifier)
        .filter { it.isNotEmpty() }
        .joinToString(" ")

    return if (modifiers.isNotEmpty()) "$modifiers $base" else base
}

typealias Theme = MaterialTheme

@Suppress("UNUSED")
fun WebDriverManager.clearDriverCacheClean(): WebDriverManager {
    val cacheFolder: File = this.config().cacheFolder
    FileUtils.cleanDirectory(cacheFolder)
    return this
}