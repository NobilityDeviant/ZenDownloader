package nobility.downloader.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.Text
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import nobility.downloader.ui.components.dialog.DialogHelper
import nobility.downloader.utils.Option
import java.util.regex.Pattern

private const val URL_TAG = "URL"

@Composable
fun LinkifyText(
    text: String,
    textColor: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    style: TextStyle = LocalTextStyle.current,
    linkColor: Color = Color.Red,
    modifier: Modifier = Modifier
) {
    val layoutResult = remember {
        mutableStateOf<TextLayoutResult?>(null)
    }
    val linksList = extractUrls(text)
    val annotatedString = buildAnnotatedString {
        append(text)
        linksList.forEach {
            addStyle(
                style = SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline
                ),
                start = it.start,
                end = it.end
            )
            addStringAnnotation(
                tag = URL_TAG,
                annotation = it.url,
                start = it.start,
                end = it.end
            )
        }
    }
    Text(
        text = annotatedString,
        color = textColor,
        style = style,
        textAlign = textAlign,
        onTextLayout = { layoutResult.value = it },
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { offsetPosition ->
                layoutResult.value?.let {
                    val position = it.getOffsetForPosition(offsetPosition)
                    annotatedString.getStringAnnotations(position, position).firstOrNull()
                        ?.let { result ->
                            if (result.tag == URL_TAG) {
                                DialogHelper.showOptions(
                                    "Link Manager",
                                    "Choose what would you like to do with the link: \n${result.item}",
                                    false,
                                    Option("Copy") {
                                        DialogHelper.showCopyPrompt(
                                            result.item,
                                            prompt = false
                                        )
                                    },
                                    Option("Open Link") {
                                        DialogHelper.showLinkPrompt(
                                            result.item
                                        )
                                    },
                                    Option("Cancel")
                                )
                            }
                        }
                }
            }
        }
    )
}

private val urlPattern: Pattern = Pattern.compile(
    "(?:^|\\W)((ht|f)tp(s?)://|www\\.)"
            + "(([\\w\\-]+\\.)+([\\w\\-.~]+/?)*"
            + "[\\p{Alnum}.,%_=?&#\\-+()\\[\\]*$~@!:/{};']*)",
    Pattern.CASE_INSENSITIVE or Pattern.MULTILINE or Pattern.DOTALL
)

fun extractUrls(text: String): List<LinkInfo> {
    val matcher = urlPattern.matcher(text)
    var matchStart: Int
    var matchEnd: Int
    val links = arrayListOf<LinkInfo>()

    while (matcher.find()) {
        matchStart = matcher.start(1)
        matchEnd = matcher.end()

        var url = text.substring(matchStart, matchEnd)
        if (!url.startsWith("http://") && !url.startsWith("https://"))
            url = "https://$url"

        links.add(LinkInfo(url, matchStart, matchEnd))
    }
    return links
}

data class LinkInfo(
    val url: String,
    val start: Int,
    val end: Int
)