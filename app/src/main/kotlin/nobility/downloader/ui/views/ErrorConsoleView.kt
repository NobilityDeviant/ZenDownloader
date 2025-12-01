package nobility.downloader.ui.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import nobility.downloader.Page
import nobility.downloader.core.Core
import nobility.downloader.ui.windows.utils.AppWindowScope

class ErrorConsoleView: ViewPage {

    override val page = Page.ERROR_CONSOLE

    @Composable
    override fun Ui(windowScope: AppWindowScope) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Core.errorConsole.ConsoleTextField(
                windowScope,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}