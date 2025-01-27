package nobility.downloader.ui.windows.database

import nobility.downloader.ui.windows.database.DatabaseType.entries


enum class DatabaseType(val title: String, val id: Int) {
    ALL("All", 0),
    DUBBED("Dubbed Anime", 1),
    SUBBED("Subbed Anime", 2),
    CARTOON("Cartoons", 3),
    MOVIE("Movies", 4),
    MISC("Unsorted", 5);

    companion object {
        fun typeForId(id: Int): DatabaseType {
            entries.forEach {
                if (id == it.id) {
                    return it
                }
            }
            return ALL
        }
    }
}