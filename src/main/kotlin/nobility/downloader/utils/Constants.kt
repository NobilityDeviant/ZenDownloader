package nobility.downloader.utils

import androidx.compose.ui.unit.dp

@Suppress("warnings")
object Constants {
    val topbarHeight = 45.dp
    val mediumIconSize = 30.dp
    val largeIconSize = 45.dp
    const val minTimeout = 5
    const val maxTimeout = 240
    const val minThreads = 1
    const val maxThreads = 10
    const val maxRetries = 10
    const val maxResRetries = 3
    const val minSpaceNeeded = 150 //in megabytes
    const val averageVideoSize = 100 //in megabytes
    const val daysToUpdateWcoUrl = 7
    const val daysToUpdateMovies = 14
    const val daysToUpdateGenres = 30
}