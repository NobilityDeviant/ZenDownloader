package nobility.downloader.core.entities.data

enum class SeriesIdentity(val slug: String, val type: Int) {
    DUBBED("dubbed-anime-list", 0),
    SUBBED("subbed-anime-list", 1),
    CARTOON("cartoon-list", 2),
    MOVIE("movie-list", 3),
    NEW("404", 4);

    companion object {
        fun filteredValues(): List<SeriesIdentity> {
            return listOf(DUBBED, SUBBED, CARTOON, MOVIE)
        }
        fun idForType(type: Int): SeriesIdentity {
            for (id in entries) {
                if (id.type == type) {
                    return id
                }
            }
            return NEW
        }

        fun isEmpty(identity: SeriesIdentity): Boolean {
            return identity == NEW
        }
    }
}