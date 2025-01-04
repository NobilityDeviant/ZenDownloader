package nobility.downloader

import nobility.downloader.Page.entries


enum class Page(val title: String) {
    DOWNLOADER("Downloader"),
    DOWNLOADS("Downloads"),
    HISTORY("History"),
    RECENT("Recent Series"),
    SETTINGS("Settings"),
    ERROR_CONSOLE("Error Console");

    companion object {

        fun nextPage(page: Page): Page? {
            val index = indexForPage(page)
            if (index != -1) {
                return entries.getOrNull(index + 1)
            }
            return null
        }

        fun beforePage(page: Page): Page? {
            val index = indexForPage(page)
            if (index != -1) {
                return entries.getOrNull(index - 1)
            }
            return null
        }

        private fun indexForPage(page: Page): Int {
            entries.forEachIndexed { i, p ->
                if (page == p) {
                    return i
                }
            }
            return -1
        }
    }
}