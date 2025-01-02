package nobility.downloader.ui.views

import androidx.compose.runtime.Composable
import nobility.downloader.Page
import nobility.downloader.ui.windows.utils.AppWindowScope

interface ViewPage {
    val page: Page
    @Composable
    fun ui(windowScope: AppWindowScope)
    fun onClose()
}