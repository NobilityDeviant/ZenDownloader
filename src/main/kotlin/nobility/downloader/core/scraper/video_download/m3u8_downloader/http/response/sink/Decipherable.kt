package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.response.sink

import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.DecryptionKey
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.ByteBufferUtil
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Utils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.ByteBuffer
import javax.crypto.Cipher

class Decipherable(
    private val identity: String,
    private val decryptionKey: DecryptionKey
) : SinkLifeCycle {

    var cipher: Cipher? = null
    private var heapBuffer: ByteBuffer? = null
    private var presetBuffer: BufferWrapper? = null

    @Throws(IOException::class)
    override fun init(reInit: Boolean) {
        if (reInit) {
            val cipher = this.cipher
            if (null != cipher) {
                try {
                    cipher.doFinal()
                } catch (_: Exception) {
                }
                this.cipher = null
                if (log.isDebugEnabled) {
                    log.debug("reInit, reset cipher: {}", identity)
                }
            }
            val heapBuffer = this.heapBuffer
            heapBuffer?.clear()
        }
        this.cipher = decryptionKey.andInitCipher
    }

    fun presetOutputBuffer(bufferWrapper: BufferWrapper?) {
        if (null == bufferWrapper || null != this.presetBuffer) {
            return
        }
        val byteBuffer = bufferWrapper.unWrap()
        if (!byteBuffer.hasArray()) {
            return
        }
        this.heapBuffer = byteBuffer
        this.presetBuffer = bufferWrapper
    }

    private fun getOutputBuffer(outputSize: Int): ByteBuffer {
        val heapBuffer = this.heapBuffer
        if (null != heapBuffer) {
            if (heapBuffer.hasArray() && heapBuffer.clear().limit() >= outputSize) {
                return heapBuffer
            }
            if (null != presetBuffer) {
                presetBuffer?.release()
                presetBuffer = null
            }
        }
        return ByteBufferUtil.allocate(outputSize).also { this.heapBuffer = it }
    }

    fun decrypt(
        cipher: Cipher,
        endData: Boolean,
        byteBuffer: ByteBuffer
    ): ByteBuffer {
        if (!byteBuffer.hasRemaining()) {
            return byteBuffer
        }
        var outputBuffer: ByteBuffer? = null
        try {
            val inputSize = byteBuffer.remaining()
            val outputSize = cipher.getOutputSize(inputSize)
            outputBuffer = getOutputBuffer(outputSize)
            if (endData) {
                cipher.doFinal(byteBuffer, outputBuffer)
            } else {
                cipher.update(byteBuffer, outputBuffer)
            }
            outputBuffer.flip()
        } catch (ex: Exception) {
            Utils.sneakyThrow<Throwable, Any>(ex)
        }
        return outputBuffer!!
    }

    @Throws(IOException::class)
    override fun dispose() {
        if (null != this.heapBuffer) {
            heapBuffer?.clear()
            this.heapBuffer = null
        }
        if (null != this.presetBuffer) {
            presetBuffer?.release()
            this.presetBuffer = null
        }
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(Decipherable::class.java)
    }
}
