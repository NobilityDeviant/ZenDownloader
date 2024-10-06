package nobility.downloader.core.scraper.video_download.m3u8_downloader.core

import nobility.downloader.core.scraper.video_download.m3u8_downloader.http.config.HttpRequestConfig
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Preconditions
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Preconditions.m3u8Check
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Preconditions.m3u8Exception
import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Utils
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class M3u8Resolver(
    val m3u8Uri: URI,
    private val requestConfigStrategy: M3u8HttpRequestConfigStrategy,
    val bytesResponseGetter: (URI, HttpRequestConfig?) -> ByteBuffer
) {
    // ----------- result ------------ //
    var finalM3u8Uri: URI? = null
    var masterM3u8Uri: URI? = null
    var finalM3u8Content: String? = null
    var masterM3u8Content: String? = null
    var mediaSegments: List<MediaSegment> = listOf()

    fun fetchSecretKey(segments: List<MediaSegment>): Map<MediaSegment, M3u8SecretKey> {

        if (segments.isEmpty()) {
            return emptyMap()
        }
        val cache: MutableMap<MediaSegmentKey, M3u8SecretKey> = mutableMapOf()
        val result: MutableMap<MediaSegment, M3u8SecretKey> = linkedMapOf()
        for (segment in segments) {
            val key = segment.key
            if (cache.containsKey(key)) {
                val cached = cache[key]
                if (cached != null) {
                    result[segment] = cached.copy()
                }
                continue
            }
            if (key?.method == null || key.method == "NONE") {
                result[segment] = M3u8SecretKey.NONE
                continue
            }
            if (key.method == "AES-128") {
                val iv = key.iv
                val keyUri = key.uri
                val keyFormat = key.keyFormat
                val keyMethod = "AES-128"
                val keyBytes = ByteArray(16)
                val requestConfig = getConfig(M3u8HttpRequestType.REQ_FOR_KEY, keyUri)

                val byteBuffer = bytesResponseGetter(keyUri, requestConfig)
                if (byteBuffer.remaining() >= 16) {
                    byteBuffer[keyBytes]
                } else {
                    m3u8Exception("keyBytes len < 16: %s", key)
                }
                var initVector = ByteArray(16)
                if (keyFormat.isNullOrBlank() || "identity" == keyFormat) {
                    if (iv.isNullOrBlank()) {
                        val sequence = segment.sequence
                        if (sequence != null) {
                            initVector = sequenceToBytes(sequence)
                        }
                    } else if (iv.startsWith("0x") || iv.startsWith("0X")) {
                        initVector = Utils.parseHexadecimal(iv)
                    }
                    val m3u8SecretKey = M3u8SecretKey(
                        keyBytes,
                        initVector,
                        keyMethod
                    )
                    cache[key] = m3u8SecretKey
                    result[segment] = m3u8SecretKey
                    continue
                } else {
                    m3u8Exception("unSupported keyFormat: %s", key)
                }
            }
            m3u8Exception("unSupported key method: %s", key)
        }
        return result
    }

    private fun sequenceToBytes(sequence: Int): ByteArray {
        Preconditions.checkArgument(sequence >= 0)
        return ByteArray(16)
    }

    fun resolve() {
        doResolve(this.m3u8Uri, null, false)
    }

    private fun doResolve(
        m3u8Uri: URI,
        segmentKey: MediaSegmentKey?,
        secondaryStream: Boolean
    ) {
        var mSegmentKey = segmentKey
        val m3u8ContentSupplier = {
            val requestConfig: HttpRequestConfig = getConfig(
                if (secondaryStream)
                    M3u8HttpRequestType.REQ_FOR_VARIANT_PLAYLIST
                else
                    M3u8HttpRequestType.REQ_FOR_M3U8_CONTENT,
                m3u8Uri
            )
            val buffer = bytesResponseGetter(m3u8Uri, requestConfig)
            String(
                buffer.array(),
                buffer.arrayOffset() + buffer.position(),
                buffer.remaining(),
                StandardCharsets.UTF_8
            )
        }





        /*val m3u8ContentSupplier = Supplier {
            val requestConfig: HttpRequestConfig = getConfig(
                if (secondaryStream)
                    M3u8HttpRequestType.REQ_FOR_VARIANT_PLAYLIST
                else
                    M3u8HttpRequestType.REQ_FOR_M3U8_CONTENT,
                m3u8Uri
            )
            val buffer = bytesResponseGetter(m3u8Uri, requestConfig)
            return@Supplier String(
                buffer.array(),
                buffer.arrayOffset() + buffer.position(),
                buffer.remaining(),
                StandardCharsets.UTF_8
            )
            bytesResponseGetter.andThen { buffer: ByteBuffer ->
                String(
                    buffer.array(),
                    buffer.arrayOffset() + buffer.position(),
                    buffer.remaining(),
                    StandardCharsets.UTF_8
                )
            }.apply(m3u8Uri, requestConfig)
        }*/

        val url = m3u8Uri.toString()
        val m3u8Content = m3u8ContentSupplier()

        //log.info("{} get content: \n{}", url, m3u8Content)

        val lineAry = m3u8Content.split("\\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        m3u8Check(lineAry[0].startsWith("#EXTM3U"), "not m3u8: %s", url)

        var sequenceNumber = 0
        var variantStreamInf: String? = null
        var extInf: String? = null
        val mediaSegments: MutableList<MediaSegment> = mutableListOf()
        val variantStreamUriAttrMap: MutableMap<URI?, Map<String, String?>?> = linkedMapOf()
        for (line in lineAry) {

            val mLine = line.trim { it <= ' ' }
            // ignore blank and comments
            if (mLine.isBlank()
                || (mLine.startsWith("#")
                        && !mLine.startsWith("#EXT"))) {
                continue
            }
            // version
            if (mLine.startsWith("#EXT-X-VERSION")) {
                //val strAry = mLine.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                //if (strAry.size == 2 && strAry[1].length > 0) {
                    //val version = strAry[1].trim { it <= ' ' }.toInt()
                    //if (version > 3) {
                        //log.warn(
                          //  "compatible version is HLS 3, the current HLS version is {}, some functions are not supported",
                            //version
                        //)
                    //}
                //}
                continue
            }
            // variant stream
            // check secondaryStream prevent circle error
            if (!secondaryStream && mLine.startsWith("#EXT-X-STREAM-INF")) {
                variantStreamInf = mLine
                continue
            }
            // sequence
            if (mLine.startsWith("#EXT-X-MEDIA-SEQUENCE")) {
                val strAry = mLine.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (strAry.size == 2 && strAry[1].isNotEmpty()) {
                    sequenceNumber = strAry[1].trim { it <= ' ' }.toInt()
                }
                continue
            }
            // key
            if (mLine.startsWith("#EXT-X-KEY") || mLine.startsWith("#EXT-X-SESSION-KEY")) {
                mSegmentKey = resolveKey(mLine)
                continue
            }
            // media segment
            if (mLine.startsWith("#EXTINF")) {
                extInf = mLine
                continue
            }
            // uri
            if (!mLine.startsWith("#")) {
                // variant stream uri
                if (null != variantStreamInf) {
                    var variantStream = URI.create(mLine)
                    val attrMap = linkedMapOf<String, String?>()
                    if (!variantStream.isAbsolute) {
                        variantStream = m3u8Uri.resolve(variantStream)
                    }
                    var attrString = ""
                    val index = variantStreamInf.indexOf(':')
                    if ((index > 0 && index < variantStreamInf.length - 1)
                        && variantStreamInf.substring(index + 1).also { attrString = it }.isNotEmpty()
                    ) {
                        val attrAry = attrString.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                        if (attrAry.isNotEmpty()) {
                            for (i in attrAry.indices) {
                                attrAry[i] = attrAry[i].trim { it <= ' ' }
                                val attr = attrAry[i].split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                                if (attr.size == 2 && attr[1].isNotEmpty()) {
                                    attrMap[attr[0].trim { it <= ' ' }] = removeQuotes(attr[1])
                                }
                            }
                        }
                    }
                    variantStreamUriAttrMap[variantStream] = attrMap
                    variantStreamInf = null
                    continue
                }
                // ts
                if (null != extInf) {
                    var mediaUri = URI.create(mLine)
                    if (!mediaUri.isAbsolute) {
                        mediaUri = m3u8Uri.resolve(mediaUri)
                    }
                    var attrString = ""
                    var durationInSeconds: Double? = null
                    val index = extInf.indexOf(':')
                    if (index > 0 && index < extInf.length - 1
                        && extInf.substring(index + 1).also { attrString = it }.isNotEmpty()
                    ) {
                        val attrAry = attrString.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                        if (attrAry.isNotEmpty()) {
                            durationInSeconds = attrAry[0].trim { it <= ' ' }.toDouble()
                        }
                    }
                    extInf = null

                    val mediaSegment = MediaSegment(
                        uri = mediaUri,
                        sequence = sequenceNumber++,
                        key = mSegmentKey,
                        durationInSeconds = durationInSeconds
                    )
                    mediaSegments.add(mediaSegment)
                    continue
                }
                //log.debug("ignore uri={}", mLine)
            }
        }

        if (variantStreamUriAttrMap.isNotEmpty()) {
            //log.info("variant playlist: \n{}", variantStreamUriAttrMap)
            val matchedUri = selectVariantStreamUri(variantStreamUriAttrMap) ?: return
            //m3u8Check(Objects.nonNull(matchedUri), "select null variant stream uri")
            this.masterM3u8Uri = m3u8Uri
            this.masterM3u8Content = m3u8Content
            //val matchedUrl = matchedUri.toString()
            //log.info("variant playlist match {}", matchedUrl)

            doResolve(matchedUri, mSegmentKey, true)
            return
        }

        if (mediaSegments.isNotEmpty()) {
            //if (log.isDebugEnabled()) {
              //  log.debug("media segments: \n{}", mediaSegments)
            //}
            this.finalM3u8Uri = m3u8Uri
            this.finalM3u8Content = m3u8Content

            this.mediaSegments = mediaSegments
            return
        }

        //log.warn("resolve empty mediaSegments")
    }

    private fun selectVariantStreamUri(
        variantStreamUriAttrMap: Map<URI?, Map<String, String?>?>
    ): URI? {
        // default is the first one, support selector ?
        return variantStreamUriAttrMap.entries.iterator().next().key
    }

    private fun getConfig(
        requestType: M3u8HttpRequestType,
        uri: URI
    ): HttpRequestConfig {
        return requestConfigStrategy.getConfig(requestType, uri)
    }

    private fun removeQuotes(str: String): String {
        var mStr = str
        if (mStr.trim { it <= ' ' }.also { mStr = it }.isEmpty()) {
            return mStr
        }
        var sub = false
        val len = mStr.length
        var beginIndex = 0
        var endIndex = len
        if (mStr.endsWith("\"")) {
            sub = true
            endIndex = len - 1
        }
        if (mStr.startsWith("\"")) {
            sub = true
            beginIndex = 1
        }
        if (sub) {
            mStr = mStr.substring(beginIndex, endIndex)
        }
        return mStr
    }

    private fun resolveKey(extXKey: String): MediaSegmentKey? {
        var attrString = ""
        val index = extXKey.indexOf(':')
        if ((index > 0 && index < extXKey.length - 1) && extXKey.substring(index + 1)
                .also { attrString = it }.isNotEmpty()
        ) {
            val attrAry = attrString.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (attrAry.isNotEmpty()) {
                val key = MediaSegmentKey()
                for (i in attrAry.indices) {
                    attrAry[i] = attrAry[i].trim { it <= ' ' }
                    val attr = attrAry[i].split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    if (attr.size == 2 && attr[1].isNotEmpty()) {
                        val attrName = attr[0].trim { it <= ' ' }
                        val remove = removeQuotes(attr[1])
                        if (attrName.startsWith("METHOD")) {
                            key.method = remove
                            continue
                        }
                        if (attrName.startsWith("URI")) {
                            key.uri = URI.create(remove)
                            continue
                        }
                        if (attrName.startsWith("IV")) {
                            key.iv = remove
                            continue
                        }
                        if (attrName.startsWith("KEYFORMAT")) {
                            key.keyFormat = remove
                            continue
                        }
                        if (attrName.startsWith("KEYFORMATVERSIONS")) {
                            key.keyFormatVersions = remove
                            continue
                        }
                    }
                }
                return key
            }
        }
        return null
    }


    data class MediaSegment(
        val uri: URI = URI(""),
        val sequence: Int? = null,
        val key: MediaSegmentKey? = null,
        val durationInSeconds: Double? = null
    )

    data class MediaSegmentKey(
        var uri: URI = URI(""),
        var iv: String? = null,
        var method: String? = null,
        var keyFormat: String? = null,
        var keyFormatVersions: String? = null
    )
}
