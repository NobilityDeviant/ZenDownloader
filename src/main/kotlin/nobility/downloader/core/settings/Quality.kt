package nobility.downloader.core.settings

enum class Quality(
    val resolution: Int,
    val htmlText: String,
    val tag: String
) {
    LOW(576, "vsd", "576p"),
    MED(720, "vhd", "720p"),
    HIGH(1080, "vfhd", "1080p");

    companion object {

        fun bestQuality(
            chosenQuality: Quality,
            qualities: List<Quality>
        ): Quality {
            if (chosenQuality == HIGH) {
                return if (qualities.contains(HIGH)) {
                    HIGH
                } else if (qualities.contains(MED)) {
                    MED
                } else {
                    LOW
                }
            } else if (chosenQuality == MED) {
                return if (qualities.contains(MED)) {
                    MED
                } else {
                    LOW
                }
            } else if (chosenQuality == LOW) {
                if (!qualities.contains(LOW)) {
                    return MED
                }
            }
            return LOW
        }

        fun qualityList(
            include720: Boolean = true,
            include1080: Boolean = true
        ): List<Quality> {
            val qualities = mutableListOf(LOW)
            if (include720) {
                qualities.add(MED)
            }
            if (include1080) {
                qualities.add(HIGH)
            }
            return qualities
        }
        fun qualityForTag(tag: String): Quality {
            entries.forEach {
                if (it.tag == tag) {
                    return it
                }
            }
            return LOW
        }
        fun qualityForResolution(resolution: Int): Quality {
            entries.forEach {
                if (it.resolution == resolution) {
                    return it
                }
            }
            return LOW
        }
    }
}