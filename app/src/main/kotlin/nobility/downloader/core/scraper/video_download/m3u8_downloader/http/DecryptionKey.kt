package nobility.downloader.core.scraper.video_download.m3u8_downloader.http

import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.CipherUtil.getAndInitM3u8AESDecryptCipher
import javax.crypto.Cipher

class DecryptionKey(
    private val key: ByteArray,
    @Suppress("UNUSED")
    val method: String,
    private val initVector: ByteArray
) {
    val andInitCipher: Cipher
        get() = getAndInitM3u8AESDecryptCipher(key, initVector)
}
