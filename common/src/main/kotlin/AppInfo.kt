import java.io.File

object AppInfo {

    const val DEBUG_MODE = false
    const val USE_CUSTOM_ERROR_PS = true
    const val UPDATE_ASSETS_ON_LAUNCH = true
    private const val NAME = "ZenDownloader"
    const val VERSION = "1.2.5" //version can only have 3 numbers split by decimals
    const val TITLE = "$NAME v$VERSION"
    const val RELEASES_LINK = "https://github.com/NobilityDeviant/ZenDownloader/releases"
    const val GITHUB_LATEST = "https://api.github.com/repos/NobilityDeviant/ZenDownloader/releases/latest"
    const val GITHUB_URL = "https://github.com/NobilityDeviant/ZenDownloader"
    const val FFMPEG_GUIDE_URL = "https://github.com/NobilityDeviant/ZenDownloader/blob/master/README.md#ffmpeg"
    const val EXAMPLE_SERIES = "anime/negima"
    const val EXAMPLE_EPISODE = "strange-planet-episode-1-the-flying-machine"
    const val WCOFUN_WEBSITE_URLS_LINK = "https://raw.githubusercontent.com/NobilityDeviant/ZenDownloader/master/assets/wco_urls.txt"
    const val WCO_MOVIE_LIST_LINK = "https://raw.githubusercontent.com/NobilityDeviant/ZenDownloader/master/assets/movies.txt"
    const val USER_AGENTS_LINK = "https://raw.githubusercontent.com/NobilityDeviant/ZenDownloader/master/assets/user_agents.txt"
    val databasePath = "${System.getProperty("user.home")}${File.separator}.zen_database${File.separator}"
    //const val NO_IMAGE_DRAWABLE = "/drawable/no_image.png"
    private const val IMAGE_PATH = "images/"
    const val APP_ICON_PATH = IMAGE_PATH + "icon.png"
    const val NO_IMAGE_PATH = IMAGE_PATH + "no_image.png"
    const val UPDATE_CODE = 108
}