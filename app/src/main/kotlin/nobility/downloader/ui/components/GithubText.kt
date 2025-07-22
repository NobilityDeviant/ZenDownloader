package nobility.downloader.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import nobility.downloader.ui.components.dialog.DialogHelper
import java.util.regex.Pattern

//thanks robot
@Composable
fun GithubText(
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

    val annotatedString = buildAnnotatedString {
        val matcher = markdownPattern.matcher(text)
        var lastIndex = 0

        while (matcher.find()) {
            append(text.substring(lastIndex, matcher.start()))

            when {
                matcher.group(1) != null -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(matcher.group(1)!!)
                    pop()
                }
                matcher.group(2) != null -> {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(matcher.group(2)!!)
                    pop()
                }
                matcher.group(3) != null -> {
                    pushStyle(SpanStyle(
                        background = Color.LightGray,
                        fontFamily = FontFamily.Monospace
                    ))
                    append(matcher.group(3)!!)
                    pop()
                }
                matcher.group(4) != null -> {
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    append(matcher.group(4)!!)
                    pop()
                }
                matcher.group(5) != null && matcher.group(6) != null -> {
                    val start = length
                    append(matcher.group(5)!!)
                    val end = length
                    addStyle(
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline
                        ),
                        start = start,
                        end = end
                    )
                    addStringAnnotation(
                        tag = URL_TAG,
                        annotation = matcher.group(6)!!,
                        start = start,
                        end = end
                    )
                }
            }


            lastIndex = matcher.end()
        }

        val remaining = text.substring(lastIndex)
        val urlMatcher = urlPattern.matcher(remaining)
        var remIndex = 0

        while (urlMatcher.find()) {
            append(remaining.substring(remIndex, urlMatcher.start()))
            val matchStart = length
            var url = remaining.substring(urlMatcher.start(), urlMatcher.end()).trim()
            if (!url.startsWith("http")) {
                url = "https://$url"
            }
            append(url)
            val matchEnd = length
            addStyle(
                style = SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline
                ),
                start = matchStart,
                end = matchEnd
            )
            addStringAnnotation(
                tag = URL_TAG,
                annotation = url,
                start = matchStart,
                end = matchEnd
            )
            remIndex = urlMatcher.end()
        }

        if (remIndex < remaining.length) {
            append(remaining.substring(remIndex))
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
                    annotatedString.getStringAnnotations(position, position).firstOrNull()?.let { result ->
                        if (result.tag == URL_TAG) {
                            DialogHelper.showLinkPrompt(result.item)
                        }
                    }
                }
            }
        }
    )
}

private val markdownPattern = Pattern.compile(
    "\\*\\*(.+?)\\*\\*" +          //bold
            "|\\*(.+?)\\*" +               //italic
            "|`(.+?)`" +                   //inline code
            "|~~(.+?)~~" +                 //strikethrough
            "|\\[(.+?)]\\((.+?)\\)",     //[text](url)
    Pattern.DOTALL
)

private const val URL_TAG = "URL"

private val urlPattern
    get() = Pattern.compile(
        "(?:^|\\W)((ht|f)tp(s?)://|www\\.)"
                + "(([\\w\\-]+\\.)+([\\w\\-.~]+/?)*"
                + "[\\p{Alnum}.,%_=?&#\\-+()\\[\\]*$~@!:/{};']*)",
        Pattern.CASE_INSENSITIVE or Pattern.MULTILINE or Pattern.DOTALL
    )