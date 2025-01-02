package nobility.downloader.core.driver

class BasicDriverBase(
    userAgent: String = "",
    headless: Boolean? = null
): DriverBase(userAgent, headless)