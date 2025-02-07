@file:Suppress("ConstPropertyName")

package nobility.downloader.utils

import androidx.compose.ui.unit.dp
import java.io.File

object Constants {
    val databasePath = "${System.getProperty("user.home")}${File.separator}.zen_database${File.separator}"
    val topBarHeight = 50.dp
    val bottomBarHeight = 80.dp
    val mediumIconSize = 30.dp
    val largeIconSize = 45.dp
    val randomSeriesRowHeight = 110.dp
    const val minTimeout = 5
    const val maxTimeout = 240
    const val minThreads = 1
    const val maxThreads = 10
    const val minFileSize = 3000 //in bytes
    const val daysToUpdateWcoUrl = 7
    const val daysToUpdateMovies = 14
    const val daysToUpdateGenres = 30
}