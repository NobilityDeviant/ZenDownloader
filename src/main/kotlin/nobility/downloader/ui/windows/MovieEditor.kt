package nobility.downloader.ui.windows

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.entities.Series
import nobility.downloader.core.entities.data.SeriesIdentity
import nobility.downloader.ui.components.defaultButton
import nobility.downloader.ui.components.defaultTextField
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.ImageUtils

object MovieEditor {
    fun open(movie: Series) {
        ApplicationState.newWindow(
            "Update Movie: ${movie.name}"
        ) {
            var name by remember { mutableStateOf(movie.name) }
            var description by remember { mutableStateOf(movie.description) }
            var imageLink by remember { mutableStateOf(movie.imageLink) }
            var genres by remember { mutableStateOf(movie.genreNamesString) }
            val scope = rememberCoroutineScope()
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            defaultButton("Close") {
                                closeWindow()
                            }
                            defaultButton(
                                "Update"
                            ) {
                                val splitGenres = genres.split(",").map { it.trim() }
                                movie.name = name
                                movie.description = description
                                movie.imageLink = imageLink
                                if (splitGenres.isNotEmpty()) {
                                    movie.updateGenresString(splitGenres, false)
                                }
                                scope.launch {
                                    ImageUtils.downloadSeriesImage(movie)
                                    BoxHelper.addSeries(
                                        movie,
                                        SeriesIdentity.MOVIE
                                    )
                                }
                                showToast("Movie has been updated.")
                            }
                        }
                    }
                }
            ) { padding ->
                val scrollState = rememberScrollState()
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(padding)
                ) {
                    Text(
                        "Name:",
                        fontSize = 16.sp,
                        modifier = Modifier.padding(5.dp)
                    )
                    defaultTextField(
                        name,
                        onValueChanged = { name = it },
                        modifier = Modifier.fillMaxWidth().padding(10.dp)
                    )
                    Text(
                        "Description:",
                        fontSize = 16.sp,
                        modifier = Modifier.padding(5.dp)
                    )
                    defaultTextField(
                        description,
                        onValueChanged = { description = it },
                        modifier = Modifier.fillMaxWidth().height(250.dp).padding(10.dp),
                        singleLine = false
                    )
                    Text(
                        "Genres (Split By Comma):",
                        fontSize = 16.sp,
                        modifier = Modifier.padding(5.dp)
                    )
                    defaultTextField(
                        genres,
                        onValueChanged = { genres = it },
                        modifier = Modifier.fillMaxWidth().padding(10.dp)
                    )
                    Text(
                        "Image Link:",
                        fontSize = 16.sp,
                        modifier = Modifier.padding(5.dp)
                    )
                    defaultTextField(
                        imageLink,
                        onValueChanged = { imageLink = it },
                        modifier = Modifier.fillMaxWidth().padding(10.dp)
                    )
                }
            }
        }
    }
}