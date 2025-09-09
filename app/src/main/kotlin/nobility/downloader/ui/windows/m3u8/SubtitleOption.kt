package nobility.downloader.ui.windows.m3u8

data class SubtitleOption(
    val name: String,
    val language: String,
    val uri: String,
    val default: Boolean
) {

    val title get() = name + if (language.isNotEmpty()) " | $language" else ""

    companion object {
        val none: SubtitleOption get() = SubtitleOption(
            "None",
            "",
            "",
            false
        )
    }
}