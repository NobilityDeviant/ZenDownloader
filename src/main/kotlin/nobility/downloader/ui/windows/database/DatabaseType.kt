package nobility.downloader.ui.windows.database

enum class DatabaseType(val title: String, val id: Int) {
    ANIME("Anime", 0),
    MOVIE("Movie", 1),
    CARTOON("Cartoon", 2),
    MISC("Misc.", 3);

    companion object {
        fun typeForId(id: Int): DatabaseType {
            entries.forEach {
                if (id == it.id) {
                    return it
                }
            }
            return ANIME
        }
    }
}