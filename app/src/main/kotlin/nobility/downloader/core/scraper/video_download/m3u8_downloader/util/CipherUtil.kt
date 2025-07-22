package nobility.downloader.core.scraper.video_download.m3u8_downloader.util

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.security.Security
import java.security.spec.AlgorithmParameterSpec
import javax.crypto.Cipher
import javax.crypto.NoSuchPaddingException
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CipherUtil {
    init {
        Security.addProvider(BouncyCastleProvider())
    }

    fun getAndInitM3u8AESDecryptCipher(
        key: ByteArray,
        initVector: ByteArray
    ): Cipher {
        val cipher: Cipher
        try {
            val secretKeySpec = SecretKeySpec(key, "AES")
            val paramSpec: AlgorithmParameterSpec = IvParameterSpec(initVector)

            cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, paramSpec)
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException(e)
        } catch (e: NoSuchPaddingException) {
            throw RuntimeException(e)
        } catch (e: InvalidKeyException) {
            throw RuntimeException(e)
        } catch (e: InvalidAlgorithmParameterException) {
            throw RuntimeException(e)
        }
        return cipher
    }
}
