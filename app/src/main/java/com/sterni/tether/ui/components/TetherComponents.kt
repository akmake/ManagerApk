@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("UNUSED_PARAMETER")

package com.sterni.tether.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.*
import androidx.compose.material3.Card as MaterialCard
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.OutlinedButton as MaterialOutlinedButton
import androidx.compose.material3.OutlinedTextField as MaterialOutlinedTextField
import androidx.compose.material3.TopAppBar as MaterialTopAppBar
import androidx.compose.material3.CenterAlignedTopAppBar as MaterialCenteredTopAppBar
import androidx.compose.material3.NavigationBar as MaterialNavigationBar
import androidx.compose.material3.NavigationBarItem as MaterialNavigationBarItem
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.sterni.tether.ui.theme.TetherLayout

/** Native Android components with the grouped, neutral surfaces of the selected v0 reference. */
@Composable
fun TetherScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {}, bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {}, floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = MaterialTheme.colorScheme.onBackground,
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit
) {
    Box(Modifier.fillMaxSize().background(containerColor), contentAlignment = Alignment.TopCenter) {
        Scaffold(
            modifier = modifier.fillMaxHeight().widthIn(max = 840.dp).fillMaxWidth(),
            topBar = topBar, bottomBar = bottomBar, snackbarHost = snackbarHost,
            floatingActionButton = floatingActionButton, floatingActionButtonPosition = floatingActionButtonPosition,
            containerColor = containerColor, contentColor = contentColor,
            contentWindowInsets = contentWindowInsets, content = content
        )
    }
}

@Composable
fun TetherTopAppBar(
    title: @Composable () -> Unit, modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null
) = Header(title, modifier, navigationIcon, actions, windowInsets, scrollBehavior, false)

@Composable
fun TetherCenteredTopAppBar(
    title: @Composable () -> Unit, modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.centerAlignedTopAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null
) = Header(title, modifier, navigationIcon, actions, windowInsets, scrollBehavior, true)

@Composable
private fun Header(
    title: @Composable () -> Unit, modifier: Modifier, navigationIcon: (@Composable () -> Unit)?,
    actions: @Composable RowScope.() -> Unit, insets: WindowInsets,
    scrollBehavior: TopAppBarScrollBehavior?, centered: Boolean
) {
    val styledTitle: @Composable () -> Unit = { ProvideTextStyle(MaterialTheme.typography.titleLarge) { title() } }
    val navigation: @Composable () -> Unit = {
        navigationIcon?.let { icon ->
            Box(Modifier.padding(start = 8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer)) {
                icon()
            }
        }
    }
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.background,
        scrolledContainerColor = MaterialTheme.colorScheme.background,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (centered) MaterialCenteredTopAppBar(title = styledTitle, modifier = modifier, navigationIcon = navigation,
        actions = actions, windowInsets = insets, colors = colors, scrollBehavior = scrollBehavior)
    else MaterialTopAppBar(title = styledTitle, modifier = modifier, navigationIcon = navigation,
        actions = actions, windowInsets = insets, colors = colors, scrollBehavior = scrollBehavior)
}

@Composable
fun TetherCard(
    modifier: Modifier = Modifier, shape: Shape = TetherLayout.cardShape,
    colors: CardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    elevation: CardElevation = CardDefaults.cardElevation(), border: BorderStroke? = null,
    onClick: (() -> Unit)? = null, enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val neutral = if (colors.containerColor == Color.White || colors.containerColor == MaterialTheme.colorScheme.surface)
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer) else colors
    if (onClick == null) MaterialCard(modifier, TetherLayout.cardShape, neutral,
        CardDefaults.cardElevation(defaultElevation = 0.dp), null, content)
    else MaterialCard(onClick = onClick, modifier = modifier, enabled = enabled,
        shape = TetherLayout.cardShape, colors = neutral, elevation = CardDefaults.cardElevation(0.dp),
        border = null, interactionSource = interactionSource, content = content)
}

@Composable
fun TetherSwitch(
    checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null, enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(), interactionSource: MutableInteractionSource? = null
) {
    val offset by animateDpAsState(if (checked) 20.dp else 0.dp, label = "tether_switch")
    Box(modifier.minimumInteractiveComponentSize().size(51.dp, 31.dp)
        .toggleable(checked, enabled = enabled && onCheckedChange != null, role = Role.Switch,
            onValueChange = { onCheckedChange?.invoke(it) }).alpha(if (enabled) 1f else 0.45f),
        contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(Modifier.size(51.dp, 31.dp).clip(CircleShape)
                .background(if (checked) MaterialTheme.colorScheme.primary else Color(0xFFBFBFBF)).padding(3.dp)) {
                Box(Modifier.offset(x = offset).size(25.dp).clip(CircleShape).background(Color.White),
                    contentAlignment = Alignment.Center) { thumbContent?.invoke() }
            }
        }
    }
}

@Composable
fun TetherButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = TetherLayout.controlShape, colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(), border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    interactionSource: MutableInteractionSource? = null, content: @Composable RowScope.() -> Unit
) = MaterialButton(onClick, modifier.heightIn(min = 48.dp), enabled, shape, colors,
    ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp), border,
    contentPadding, interactionSource, content)

@Composable
fun TetherOutlinedButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = TetherLayout.controlShape, colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null, border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    interactionSource: MutableInteractionSource? = null, content: @Composable RowScope.() -> Unit
) = MaterialOutlinedButton(onClick, modifier.heightIn(min = 48.dp), enabled, shape, colors,
    null, border, contentPadding, interactionSource, content)

@Composable
fun TetherTextField(
    value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, readOnly: Boolean = false, textStyle: TextStyle = LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null, placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null, trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null, suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null, isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default, keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false, maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE, minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null, shape: Shape = TetherLayout.controlShape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors()
) = MaterialOutlinedTextField(
    value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled,
    readOnly = readOnly, textStyle = textStyle, label = label, placeholder = placeholder,
    leadingIcon = leadingIcon, trailingIcon = trailingIcon, prefix = prefix, suffix = suffix,
    supportingText = supportingText, isError = isError, visualTransformation = visualTransformation,
    keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, singleLine = singleLine,
    maxLines = maxLines, minLines = minLines, interactionSource = interactionSource,
    shape = TetherLayout.controlShape,
    colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent, errorContainerColor = MaterialTheme.colorScheme.errorContainer
    )
)

@Composable
fun TetherNavigationBar(
    modifier: Modifier = Modifier, containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface, tonalElevation: Dp = 0.dp,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets, content: @Composable RowScope.() -> Unit
) = MaterialNavigationBar(modifier, MaterialTheme.colorScheme.surface, contentColor, 0.dp, windowInsets, content)

@Composable
fun RowScope.TetherNavigationBarItem(
    selected: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, label: (@Composable () -> Unit)? = null,
    alwaysShowLabel: Boolean = true, colors: NavigationBarItemColors = NavigationBarItemDefaults.colors(),
    interactionSource: MutableInteractionSource? = null
) = MaterialNavigationBarItem(
    selected, onClick, icon, modifier, enabled, label, alwaysShowLabel,
    NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary, indicatorColor = Color.Transparent,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant), interactionSource
)

@Composable
fun TetherSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier.padding(start = 16.dp, top = 24.dp, bottom = 10.dp),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun TetherSettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = TetherLayout.cardShape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 16.dp), content = content)
    }
}

@Composable
fun TetherPreferenceRow(
    title: String, icon: ImageVector, modifier: Modifier = Modifier, subtitle: String? = null,
    onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null,
    destructive: Boolean = false
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val clickable = if (onClick != null) modifier.clickable(role = Role.Button, onClick = onClick) else modifier
    Row(clickable.fillMaxWidth().heightIn(min = if (subtitle == null) 58.dp else 76.dp).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (destructive) color else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = color)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp)) }
        }
        Spacer(Modifier.width(12.dp))
        if (trailing != null) trailing()
        else if (onClick != null) Icon(Icons.Default.ChevronLeft, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun TetherGroupDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
