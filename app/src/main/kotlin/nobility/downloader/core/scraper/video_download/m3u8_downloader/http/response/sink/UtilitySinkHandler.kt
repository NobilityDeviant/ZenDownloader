package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.response.sink

import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Utils.EMPTY_BIN
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Utils.mapToNullable
import org.apache.commons.lang3.ObjectUtils
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.CompletableFuture
import javax.crypto.Cipher

class UtilitySinkHandler(
    private val filePath: Path,
    private val bufferProvider: BufferProvider,
    private val asyncSink: AsyncSink?,
    private val decipherable: Decipherable?
) : SinkHandler {

    private val sinkLifeCycles: List<SinkLifeCycle> = listOfNotNull(
        asyncSink,
        decipherable,
        bufferProvider
    )


    // non-final
    private var fileChannel: FileChannel? = null

    private var bufferWrapper: BufferWrapper? = null

    private lateinit var sinkFutures: MutableList<CompletableFuture<Void?>>

    @Throws(IOException::class)
    override fun init(
        sinkFutures: MutableList<CompletableFuture<Void?>>,
        reInit: Boolean
    ) {

        for (sinkLifeCycle in this.sinkLifeCycles) {
            sinkLifeCycle.init(reInit)
        }
        if (reInit) {
            if (null != this.fileChannel) {
                try {
                    fileChannel?.close()
                } catch (_: Exception) {}
            }
            Files.deleteIfExists(this.filePath)
            if (null != bufferWrapper) {
                bufferWrapper?.unWrap()?.clear()
            }
        } else {
            decipherable?.presetOutputBuffer(bufferProvider.newBuffer())
        }
        this.sinkFutures = sinkFutures
        this.fileChannel = FileChannel.open(
            this.filePath,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE
        )
    }

    @Throws(IOException::class)
    override fun doSink(data: ByteBuffer, endData: Boolean) {
        var mData = data
        mData = ObjectUtils.defaultIfNull(mData, EMPTY_BIN)
        val size = mData.remaining()

        if (size > 0) {
            var bufferWrapper = curBuffer
            var byteBuffer = bufferWrapper.unWrap()
            var remainingSize = size
            var remainingCapacity = byteBuffer.remaining()
            while (remainingSize >= remainingCapacity) {
                if (mData.hasArray()) {
                    byteBuffer.put(
                        mData.array(),
                        mData.arrayOffset() + mData.position(),
                        remainingCapacity
                    )
                    mData.position(mData.position() + remainingCapacity)
                } else {
                    var s = 0
                    while (s++ < remainingCapacity) {
                        byteBuffer.put(mData.get())
                    }
                }

                byteBuffer.flip()
                val fileChannel = fileChannel
                if (fileChannel != null) {
                    write(
                        fileChannel,
                        false,
                        bufferWrapper
                    )
                }

                bufferWrapper = nxtBuffer()
                byteBuffer = bufferWrapper.unWrap()
                remainingSize -= remainingCapacity
                remainingCapacity = byteBuffer.remaining()
            }
            byteBuffer.put(mData)
        }
        if (endData) {
            val bufferWrapper = curBuffer
            val byteBuffer = bufferWrapper.unWrap()
            byteBuffer.flip()
            val fileChannel = fileChannel
            if (fileChannel != null) {
                write(fileChannel, true, bufferWrapper)
            } else {
                //log
            }
        }
    }

    @Throws(IOException::class)
    private fun write(
        channel: FileChannel,
        endData: Boolean,
        bufferWrapper: BufferWrapper
    ) {
        if (null != asyncSink) {
            val future = CompletableFuture<Void?>()
            sinkFutures.add(future)
            asyncSink.submitAsyncSinkTask(
                AsyncSinkTask(
                    channel,
                    bufferWrapper,
                    future,
                mapToNullable(decipherable) { obj: Decipherable -> obj.cipher },
                    endData,
                    decipherable
            )
            )
            return
        }

        try {
            doWrite(
                channel,
                bufferWrapper.unWrap(),
                mapToNullable(decipherable) { obj: Decipherable -> obj.cipher },
                endData,
                decipherable
            )
        } finally {
            bufferWrapper.unWrap().clear()
        }
    }

    private val curBuffer: BufferWrapper
        get() {
            if (null == bufferWrapper) {
                bufferWrapper = bufferProvider.newBuffer()
            }
            return bufferWrapper!!
        }

    private fun nxtBuffer(): BufferWrapper {
        if (null != asyncSink) {
            bufferWrapper = bufferProvider.newBuffer()
        }
        return bufferWrapper!!
    }

    @Throws(IOException::class)
    override fun dispose() {
        try {
            if (null != fileChannel) {
                fileChannel!!.close()
                fileChannel = null
            }
            if (null != bufferWrapper) {
                bufferWrapper!!.release()
                bufferWrapper = null
            }
        } catch (_: IOException) {
            // only print
            //log.error("dispose exception: " + filePath.fileName, ex)
        }
        for (sinkLifeCycle in this.sinkLifeCycles) {
            sinkLifeCycle.dispose()
        }
    }

    private class AsyncSinkTask(
        val channel: FileChannel,
        val bufferWrapper: BufferWrapper,
        val future: CompletableFuture<Void?>,
        private val cipher: Cipher?,
        private val endData: Boolean,
        val decipherable: Decipherable?
    ) : AsyncSink.SinkTask {

        override fun endData(): Boolean {
            return this.endData
        }

        @Throws(IOException::class)
        override fun doSink() {
            try {
                doWrite(
                    channel,
                    bufferWrapper.unWrap(),
                    cipher,
                    endData,
                    decipherable
                )
            } finally {
                bufferWrapper.release()
            }
        }

        override fun completableFuture(): CompletableFuture<Void?> {
            return this.future
        }
    }

    companion object {
        @Throws(IOException::class)
        private fun doWrite(
            channel: FileChannel,
            byteBuffer: ByteBuffer,
            cipher: Cipher?,
            endData: Boolean,
            decipherable: Decipherable?
        ) {

            var buffer = byteBuffer
            if (null != decipherable && null != cipher) {
                buffer = decipherable.decrypt(cipher, endData, byteBuffer)
            }
            if (!buffer.hasRemaining()) {
                return
            }
            var spin = 1
            val maxSpin = 20
            while (true) {
                if (!channel.isOpen) {
                    break
                }
                channel.write(buffer)

                // all bytes must be written
                if (!buffer.hasRemaining()) {
                    break
                }
                if (++spin > maxSpin) {
                    throw IOException(String.format("write incomplete, spin=%d", maxSpin))
                }

                buffer.compact()
            }
        }
    }
}
