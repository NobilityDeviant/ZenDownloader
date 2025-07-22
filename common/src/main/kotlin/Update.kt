data class Update(
    val version: String,
    val downloadLink: String,
    val downloadName: String,
    val downloadType: String,
    val updateDescription: String,
    var isLatest: Boolean = false
)
