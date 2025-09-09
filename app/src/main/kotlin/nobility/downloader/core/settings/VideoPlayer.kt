package nobility.downloader.core.settings

import nobility.downloader.core.BoxHelper.Companion.string

enum class VideoPlayer {
    FFPLAY, MPV;

    companion object {

        fun playerForSetting(): VideoPlayer {
            entries.forEach {
                if (it.name.equals(Defaults.VIDEO_PLAYER.string(), true)) {
                    return it
                }
            }
            return FFPLAY
        }
    }
}