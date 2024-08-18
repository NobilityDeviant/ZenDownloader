package nobility.downloader.ui.windows.database

enum class DatabaseSort(val id: Int) {
    NAME(0),
    NAME_DESC(1),
    EPISODES(2),
    EPISODES_DESC(3);

    companion object {
        fun sortForId(id: Int): DatabaseSort {
            entries.forEach {
                if (id == it.id) {
                    return it
                }
            }
            return NAME
        }
    }
}