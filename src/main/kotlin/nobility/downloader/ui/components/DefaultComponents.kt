package nobility.downloader.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.fill.ArrowDown
import nobility.downloader.utils.Constants

data class DropdownOption(
    val title: String,
    val modifier: Modifier = Modifier,
    val onClick: () -> Unit
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun defaultDropdown(
    label: String,
    expanded: Boolean,
    dropdownOptions: List<DropdownOption>,
    dropdownModifier: Modifier = Modifier,
    boxWidth: Dp = 120.dp,
    boxHeight: Dp = Dp.Unspecified,
    boxPadding: Dp = 0.dp,
    boxColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    boxTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    centerBoxText: Boolean = false,
    onTextClick: () -> Unit = {},
    onDismissRequest: () -> Unit = {}
) {
    Box {
        Row(
            modifier = Modifier
                .width(boxWidth)
                .height(boxHeight)
                .padding(boxPadding)
                .background(
                    boxColor,
                    RoundedCornerShape(4.dp)
                ).onClick { onTextClick() },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp, start = 5.dp)
                    .weight(1f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = if (centerBoxText) TextAlign.Center else TextAlign.Start,
                color = boxTextColor
            )
            Icon(
                EvaIcons.Fill.ArrowDown,
                "",
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp, end = 1.dp)
                    .size(18.dp),
                tint = boxTextColor
            )
        }
        DropdownMenu(
            expanded,
            onDismissRequest,
            modifier = dropdownModifier
        ) {
            dropdownOptions.forEach {
                defaultDropdownItem(
                    it.title
                ) {
                    it.onClick()
                }
            }
        }
    }
}

@Composable
fun defaultDropdownItem(
    text: String,
    startIcon: ImageVector? = null,
    endIcon: ImageVector? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (startIcon != null) {
                Icon(
                    startIcon,
                    "",
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text,
                fontSize = 12.sp,
                modifier = Modifier.offset(y = (-3).dp)
            )
            if (endIcon != null) {
                Icon(
                    endIcon,
                    "",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun defaultTextField(
    value: String,
    onValueChanged: (String) -> Unit,
    hint: String = "",
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions(),
    modifier: Modifier = Modifier,
    contextMenuItems: () -> List<ContextMenuItem> = { listOf() }
) {
    val contextMenuRepresentation = if (isSystemInDarkTheme()) {
        DarkDefaultContextMenuRepresentation
    } else {
        LightDefaultContextMenuRepresentation
    }
    CompositionLocalProvider(LocalContextMenuRepresentation provides contextMenuRepresentation) {
        ContextMenuDataProvider(
            items = contextMenuItems
        ) {
            TextField(
                value,
                onValueChange = onValueChanged,
                singleLine = singleLine,
                readOnly = readOnly,
                enabled = enabled,
                placeholder = {
                    if (hint.isNotEmpty()) {
                        Text(
                            hint,
                            modifier = Modifier.alpha(0.3f)
                        )
                    }
                },
                modifier = modifier,
                colors = TextFieldDefaults.colors(),
                keyboardOptions = keyboardOptions
            )
        }
    }
}

@Composable
fun defaultSettingsTextField(
    value: String,
    onValueChanged: (String) -> Unit,
    hint: String = "",
    singleLine: Boolean = true,
    enabled: MutableState<Boolean> = mutableStateOf(true),
    numbersOnly: Boolean = false,
    modifier: Modifier = Modifier.height(30.dp),
    settingsDescription: String = "",
    contextMenuItems: () -> List<ContextMenuItem> = { listOf() }
) {
    val stateEnabled by remember { enabled }
    val contextMenuRepresentation = if (isSystemInDarkTheme()) {
        DarkDefaultContextMenuRepresentation
    } else {
        LightDefaultContextMenuRepresentation
    }
    tooltip(
        tooltipText = settingsDescription
    ) {
        CompositionLocalProvider(LocalContextMenuRepresentation provides contextMenuRepresentation) {
            ContextMenuDataProvider(
                items = contextMenuItems
            ) {
                FixedTextField(
                    value,
                    enabled = stateEnabled,
                    onValueChange = onValueChanged,
                    singleLine = singleLine,
                    modifier = modifier,
                    textStyle = MaterialTheme.typography.labelSmall,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (numbersOnly)
                            KeyboardType.Number
                        else
                            KeyboardType.Text
                    ),
                    placeholder = {
                        if (hint.isNotEmpty()) {
                            Text(
                                hint,
                                modifier = Modifier.alpha(0.3f)
                            )
                        }
                    },
                    colors = TextFieldDefaults.colors(),
                    padding = PaddingValues(start = 4.dp, end = 4.dp)
                )
            }
        }
    }
}

@Composable
fun defaultButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: MutableState<Boolean> = mutableStateOf(true),
    height: Dp = Dp.Unspecified,
    width: Dp = Dp.Unspecified,
    padding: Dp = 5.dp,
    onClick: () -> Unit
) {
    val stateEnabled by remember { enabled }
    Button(
        onClick = onClick,
        modifier = Modifier.padding(padding)
            .height(height)
            .width(width)
            .then(modifier),
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(),
        enabled = stateEnabled,
        contentPadding = PaddingValues(5.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.CenterVertically)
        ) {
            Text(
                text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun defaultCheckbox(
    checked: MutableState<Boolean>,
    modifier: Modifier = Modifier,
    onCheckedChanged: ((Boolean) -> Unit)
) {
    val stateChecked by remember { checked }
    CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
        Checkbox(
            stateChecked,
            onCheckedChanged,
            colors = CheckboxDefaults.colors(),
            modifier = modifier
        )
    }
}

@Composable
fun defaultIcon(
    icon: ImageVector,
    iconSize: Dp = Constants.mediumIconSize,
    iconColor: Color = MaterialTheme.colorScheme.onPrimary,
    iconModifier: Modifier = Modifier,
    contentDescription: String = "",
) {
    Icon(
        icon,
        contentDescription,
        tint = iconColor,
        modifier = iconModifier.size(iconSize)
    )
}

@Composable
fun defaultFilledIconButton(
    icon: ImageVector,
    iconSize: Dp = Constants.mediumIconSize,
    iconColor: Color = MaterialTheme.colorScheme.onPrimary,
    iconButtonColors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
    buttonModifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    contentDescription: String = "",
    onClick: () -> Unit
) {
    FilledIconButton(
        onClick = onClick,
        modifier = buttonModifier,
        colors = iconButtonColors
    ) {
        Icon(
            icon,
            contentDescription,
            tint = iconColor,
            modifier = iconModifier.size(iconSize)
        )
    }
}

@Composable
fun tooltipIconButton(
    tooltipText: String,
    icon: ImageVector,
    iconSize: Dp = Constants.mediumIconSize,
    iconColor: Color = MaterialTheme.colorScheme.onPrimary,
    iconModifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
    tooltipModifier: Modifier = Modifier,
    contentDescription: String = "",
    onClick: () -> Unit
) {
    tooltip(
        tooltipText,
        tooltipModifier
    ) {
        IconButton(
            onClick = onClick,
            modifier = buttonModifier
        ) {
            Icon(
                icon,
                contentDescription,
                tint = iconColor,
                modifier = iconModifier.size(iconSize)
            )
        }
    }
}

@Composable
fun tooltipIconButton(
    tooltipText: String,
    icon: ImageVector,
    iconSize: Dp = Constants.mediumIconSize,
    iconColor: Color = MaterialTheme.colorScheme.onPrimary,
    contentDescription: String = "",
    spacePosition: SpacePosition? = null,
    space: Dp = 0.dp,
    onClick: () -> Unit
) {
    val modifier = Modifier
    if (spacePosition != null) {
        if (spacePosition == SpacePosition.START) {
            modifier.apply {
                padding(start = space)
            }
        } else if (spacePosition == SpacePosition.END) {
            modifier.apply {
                padding(end = space)
            }
        }
    }
    tooltip(tooltipText) {
        IconButton(
            onClick = onClick
        ) {
            Icon(
                icon,
                contentDescription,
                tint = iconColor,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

enum class SpacePosition {
    START, END
}