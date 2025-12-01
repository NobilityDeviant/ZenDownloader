package nobility.downloader.ui.views

import androidx.compose.runtime.Composable
import nobility.downloader.Page
import nobility.downloader.ui.components.OverflowOption
import nobility.downloader.ui.windows.utils.AppWindowScope

interface ViewPage {
    val page: Page
    @Composable
    fun Ui(windowScope: AppWindowScope)
    fun onClose() {}
    val menuOptions: List<OverflowOption>
        get() = listOf()
}