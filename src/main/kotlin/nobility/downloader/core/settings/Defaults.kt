package nobility.downloader.core.settings

import nobility.downloader.ui.windows.database.DatabaseSort
import nobility.downloader.ui.windows.database.DatabaseType
import nobility.downloader.utils.Constants

/**
 * An enum used for easy settings management.
 * The type must be specified when calling the value.
 * Settings only support numbers, strings and booleans.
 * Custom saves must be implemented inside SettingsMeta
 * @see nobility.downloader.core.entities.settings.SettingsMeta
 */
enum class Defaults(
    val key: String,
    val value: Any,
    val description: String = ""
) {
    BYPASS_DISK_SPACE(
        "bypass_check",
        false,
        """
            Bypassing the disk space checks can be needed sometimes.
            Usually everything goes well, but if your disk has certain permission issues or anything else goes wrong,
            the checks can fail even if you have enough space.
            Disks must have at least 150MB to start downloading with this option off.
            Default: Off
        """.trimIndent()
    ),
    PROXY(
        "proxy",
        "",
        """
            The HTTPS IPV4 proxy used for downloads.
            A proxy would be used in case you can't access the website.
            Proxies must be put as the IP:Port format. Username & Password Authentication isn't fully supported in Kotlin.
            Example: 122.188.1.22:6266
            Note: Proxies aren't fully supported. It's recommended to use a VPN instead.
            Default: Empty
        """.trimIndent()
    ),
    TIMEOUT(
        "timeout",
        30,
        """
            The timeout in seconds used to know when a network action takes too long to respond.
            This is used globally for all actions including element detection.
            Lower = Faster Internet | Higher = Slower Internet
            Minimum: ${Constants.minTimeout} | Maximum: ${Constants.maxTimeout}
            Default: 30
        """.trimIndent()
    ),
    SAVE_FOLDER(
        "save_folder",
        System.getProperty("user.home"),
        """
            The folder where all your downloads get saved.
            Default: ${System.getProperty("user.home")}
        """.trimIndent()
    ),
    DOWNLOAD_THREADS(
        "download_threads",
        1,
        """
            The amount of browser instances used to download files at the same time.
            Each thread is considered a download process.
            If you want to download more than 1 file at a time, you can increase this.
            Each instance opens up the browser in the background and it consumes quite a bit of ram.
            Cloudflare can also block your IP if you use too many.
            Lower = Slow PC | Higher = Fast PC
            Minimum: ${Constants.minThreads} | Maximum: ${Constants.maxThreads}
            Default: 1
        """.trimIndent()
    ),
    TOAST_ALPHA(
        "toast_alpha",
        0.6f,
        """
            The transparency for toasts.
            Toasts are just the little messages that show up and go away after some time.
            Default: 0.6f
        """.trimIndent()
    ),
    SHOW_DEBUG_MESSAGES(
        "debug_messages",
        false,
        """
            Shows debug logs in the console.
            Debug logs are useful for reporting issues to Github or just wanting to know what's going on in the background.
            Default: Off
        """.trimIndent()
    ),
    QUALITY(
        "video_quality",
        Quality.LOW.tag,
        """
            The quality videos download in.
            Sometimes a quality option is available for certain videos.
            This option will try to get your selected option, but if it doesn't exist, it will default to the one underneath it.
            Default: 576p
        """.trimIndent()
    ),
    SHOW_TOOLTIPS(
        "show_tooltips",
        true,
        """
            Show these types of boxes on certain components.
            Default: On
        """.trimIndent()
    ),
    CONSOLE_ON_TOP(
        "console_on_top",
        true,
        """
            When popping out the console it will always stay on top of other windows.
            Default: On
        """.trimIndent()
    ),
    HEADLESS_MODE(
        "headless_mode",
        true,
        """
            Headless mode is used to hide the browsers during download or scraping.
            If you turn this off then all browser instances will show.
            The only reason this option is here is for debugging or if you're curious to see the process.
            Note: With this option off, your download threads will be capped at 1 to prevent window spam.
            Default: On
        """.trimIndent()
    ),
    SEPARATE_SEASONS(
        "separate_seasons",
        true,
        """
            Used to give each found season inside a series it's own folder.
            A season folder is created if the episodes name contains "Season *" with * as any number.
            If the season folder is created from the episode name, that episode will go inside.
            Also if the episode doesn't have "Season *" then it defaults to Season 01.
            Default: On
        """.trimIndent()
    ),
    AUTO_SCROLL_CONSOLES(
        "auto_scroll_consoles",
        true,
        """
            Auto scrolls all consoles to the bottom when a new text is added to it.
            Default: On
        """.trimIndent()
    ),
    LAST_DOWNLOAD("last_dl", ""),
    DENIED_UPDATE("denied_update", false),
    UPDATE_VERSION("update_version", "1.0"),
    WCO_DOMAIN("wco_website", "wcofun"), //used to combat the constantly changing domain
    WCO_EXTENSION("wco_ext", "org"), //used to combat the constantly changing domain
    WCO_LAST_UPDATED("wco_last_update", 0L),
    ENABLE_PROXY("enable_proxy", false),
    FIRST_LAUNCH("1st-launch", false),
    DARK_MODE("dark_mode", true),
    WCO_GENRES_LAST_UPDATED("wco_genres_last_updated", 0L),
    DB_LAST_TYPE_USED("db_last_type", DatabaseType.ANIME.id),
    DB_LAST_SORT_USED("db_last_sort", DatabaseSort.NAME.id)
}