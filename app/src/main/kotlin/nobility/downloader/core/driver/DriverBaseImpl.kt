package nobility.downloader.core.driver

class DriverBaseImpl(
    userAgent: String = "",
    headless: Boolean? = null,
    manualSetup: Boolean = false
): DriverBase(userAgent, headless, manualSetup)