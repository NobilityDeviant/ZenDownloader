package nobility.downloader.utils

data class Option(
    val title: String,
    val func: () -> Unit = {}
)