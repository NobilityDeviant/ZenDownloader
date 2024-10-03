package nobility.downloader.core.scraper.video_download.m3u8_downloader.support.log

import org.slf4j.Marker
import org.slf4j.MarkerFactory

object WhiteboardMarkers {
    val whiteboardMarkerStr: List<String>
        get() = mutableListOf("WHITEBOARD", "STD", "PROCESS_STD")

    val whiteboardMarker: Marker
        get() = MarkerFactory.getMarker("WHITEBOARD")
}
