package nobility.downloader.ui.components.toast

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

object ToastTheme {
    val ActionFocusLabelTextColor: Color @Composable get() = MaterialTheme.colorScheme.inversePrimary
    val ActionHoverLabelTextColor: Color @Composable get() = MaterialTheme.colorScheme.inversePrimary
    val ActionLabelTextColor: Color @Composable get() = MaterialTheme.colorScheme.inversePrimary
    val ActionLabelTextFont: TextStyle @Composable get() = MaterialTheme.typography.labelLarge
    val ActionPressedLabelTextColor: Color @Composable get() = MaterialTheme.colorScheme.inversePrimary
    val ContainerColor: Color @Composable get() = Color.Black
    val ContainerElevation = 0.0.dp
    val ContainerShape: Shape @Composable get() = ShapeDefaults.Medium
    val IconColor: Color @Composable get() = MaterialTheme.colorScheme.inverseOnSurface
    val FocusIconColor: Color @Composable get() = MaterialTheme.colorScheme.inverseOnSurface
    val HoverIconColor: Color @Composable get() = MaterialTheme.colorScheme.inverseOnSurface
    val PressedIconColor: Color @Composable get() = MaterialTheme.colorScheme.inverseOnSurface
    val IconSize = 24.0.dp
    val SupportingTextColor: Color @Composable get() = Color.White
    val SupportingTextFont: TextStyle @Composable get() = MaterialTheme.typography.bodyMedium
    val SingleLineContainerHeight = 200.0.dp
    val TwoLinesContainerHeight = 240.0.dp
}