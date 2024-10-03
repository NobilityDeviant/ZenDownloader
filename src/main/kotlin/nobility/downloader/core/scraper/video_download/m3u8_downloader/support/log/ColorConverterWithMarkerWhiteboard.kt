package nobility.downloader.core.scraper.video_download.m3u8_downloader.support.log

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.PatternLayout
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.pattern.color.ANSIConstants
import ch.qos.logback.core.pattern.color.ForegroundCompositeConverterBase
import nobility.downloader.core.scraper.video_download.m3u8_downloader.support.log.WhiteboardMarkers.whiteboardMarkerStr
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.Marker
import org.slf4j.MarkerFactory
import java.util.*

class ColorConverterWithMarkerWhiteboard :
    ForegroundCompositeConverterBase<ILoggingEvent>() {

    private var whiteboardLayout: PatternLayout? = null
    private val whiteboardMarkers = mutableListOf<Marker>()

    override fun start() {
        // spec marker
        CollectionUtils.emptyIfNull(whiteboardMarkerStr).stream()
            .filter { cs: String? -> StringUtils.isNotBlank(cs) }
            .forEach { whiteboardMarkerStr: String -> this.addWhiteboardMarker(whiteboardMarkerStr) }

        val patternLayout = PatternLayout()
        patternLayout.context = context
        patternLayout.pattern = "%m%n"
        patternLayout.start()
        this.whiteboardLayout = patternLayout
        addInfo("ColorConverterWithMarkerWhiteboard start whiteboardLayout")
        super.start()
    }

    override fun convert(event: ILoggingEvent): String {
        val marker = event.marker
        val whiteboardLayout = this.whiteboardLayout
        if (Objects.nonNull(marker) && Objects.nonNull(whiteboardLayout)) {
            for (whiteboardMarker in this.whiteboardMarkers) {
                if (marker.contains(whiteboardMarker)) {
                    val intermediary = whiteboardLayout!!.doLayout(event)
                    return transform(event, intermediary)
                }
            }
        }
        return super.convert(event)
    }

    override fun getForegroundColorCode(event: ILoggingEvent): String {
        var element = ELEMENTS[firstOption]
        if (element == null) {
            element = LEVELS[event.level.toInteger()]
            element = if ((element != null)) element else ANSIConstants.DEFAULT_FG
        }
        return element
    }

    fun addWhiteboardMarker(whiteboardMarkerStr: String) {
        if (StringUtils.isNotBlank(whiteboardMarkerStr)) {
            val whiteboardMarker = MarkerFactory.getMarker(whiteboardMarkerStr)
            whiteboardMarkers.add(whiteboardMarker)
            addInfo("ColorConverterWithMarkerWhiteboard add whiteboardMarker: $whiteboardMarkerStr")
        }
    }

    companion object {
        private val LEVELS: Map<Int, String>

        private val ELEMENTS: Map<String, String>

        init {
            val ansiElements: MutableMap<String, String> = HashMap()
            ansiElements["red"] = ANSIConstants.RED_FG
            ansiElements["cyan"] = ANSIConstants.CYAN_FG
            ansiElements["blue"] = ANSIConstants.BLUE_FG
            ansiElements["green"] = ANSIConstants.GREEN_FG
            ansiElements["yellow"] = ANSIConstants.YELLOW_FG
            ansiElements["magenta"] = ANSIConstants.MAGENTA_FG
            ansiElements["boldRed"] = ANSIConstants.BOLD + ANSIConstants.RED_FG
            ansiElements["boldCyan"] = ANSIConstants.BOLD + ANSIConstants.CYAN_FG
            ansiElements["boldBlue"] = ANSIConstants.BOLD + ANSIConstants.BLUE_FG
            ansiElements["boldGreen"] = ANSIConstants.BOLD + ANSIConstants.GREEN_FG
            ansiElements["boldYellow"] = ANSIConstants.BOLD + ANSIConstants.YELLOW_FG
            ansiElements["boldMagenta"] = ANSIConstants.BOLD + ANSIConstants.MAGENTA_FG
            ELEMENTS = Collections.unmodifiableMap(ansiElements)

            val ansiLevels: MutableMap<Int, String> = HashMap()
            ansiLevels[Level.ERROR_INTEGER] = ANSIConstants.RED_FG
            ansiLevels[Level.INFO_INTEGER] = ANSIConstants.CYAN_FG
            ansiLevels[Level.WARN_INTEGER] = ANSIConstants.BOLD + ANSIConstants.YELLOW_FG
            LEVELS = Collections.unmodifiableMap(ansiLevels)
        }
    }
}
