package nobility.downloader.core.scraper.video_download.m3u8_downloader.core

class M3u8SecretKey(
    val key: ByteArray?,
    val initVector: ByteArray?,
    val method: String
) {

    fun copy(): M3u8SecretKey {
        if (key == null || initVector == null) {
            return NONE
        }
        val newKey = ByteArray(key.size)
        val newInitVector = ByteArray(initVector.size)
        System.arraycopy(
            key,
            0,
            newKey,
            0,
            newKey.size
        )
        System.arraycopy(
            initVector,
            0,
            newInitVector,
            0,
            newInitVector.size
        )
        return M3u8SecretKey(newKey, newInitVector, method)
    }

    companion object {
        val NONE: M3u8SecretKey = M3u8SecretKey(
            null,
            null,
            "NONE"
        )
    }
}
