package nobility.downloader.core.scraper.data

data class RecentResult(val data: List<Data>) {
    data class Data(
        val imagePath: String,
        val imageLink: String,
        val name: String,
        val link: String,
        val isSeries: Boolean
    )
}