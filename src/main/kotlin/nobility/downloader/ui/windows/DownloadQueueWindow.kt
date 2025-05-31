package nobility.downloader.ui.windows

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import nobility.downloader.core.Core
import nobility.downloader.ui.components.HeaderItem
import nobility.downloader.ui.components.HeaderSort
import nobility.downloader.ui.components.SortedLazyColumn
import nobility.downloader.ui.windows.utils.ApplicationState

class DownloadQueueWindow {

    private var currentSort = mutableStateOf<HeaderSort?>(null)

    fun open() {
        ApplicationState.newWindow(
            "Download Queue",
            maximized = true
        ) {
            val scrollState = rememberLazyListState()
            SortedLazyColumn(
                listOf(
                    HeaderItem(
                        "Name"
                    ) { it.name },
                    HeaderItem(
                        "Status"
                    ) { it.fileSize },
                    HeaderItem(
                        "Test",
                        0.5f
                    )
                ),
                currentSort,
                Core.child.downloadThread.downloadQueue,
                key = { it.name + it.id },
                lazyListState = scrollState
            ) {

            }
            LaunchedEffect(Core.child.downloadList.size) {
                scrollState.animateScrollToItem(0)
            }
        }
    }
}