package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ChipColors
import androidx.compose.material3.ChipElevation
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SelectableChipElevation
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/*
 * The chips of the whole app (AP-70 b). Material 3 lifts a chip by 1 dp while the mouse is over it. Its container is
 * transparent (an unselected FilterChip, AssistChip, SuggestionChip), so the shadow under it shows through as a grey
 * box with a dark edge across the label – the „seltsamer Schatten“ on *Klinge (B)*. These chips keep everything of
 * Material 3 but stay flat on hover: hovering only tints them faintly (the state layer), the label stays readable, and
 * the clear keyboard focus of AP-30 (ui/Theme.kt) is unchanged.
 *
 * Same names and parameters as in androidx.compose.material3: screens in this package get them without an import
 * (UiChecksTest keeps the material ones from coming back).
 */

/** Elevation of a flat chip: none, also not on hover (only a drag lifts it, as in Material 3). */
@Composable
private fun flatSelectable(): SelectableChipElevation =
    FilterChipDefaults.filterChipElevation(elevation = 0.dp, pressedElevation = 0.dp, focusedElevation = 0.dp, hoveredElevation = 0.dp, disabledElevation = 0.dp)

@Composable
private fun flatAssist(): ChipElevation =
    AssistChipDefaults.assistChipElevation(elevation = 0.dp, pressedElevation = 0.dp, focusedElevation = 0.dp, hoveredElevation = 0.dp, disabledElevation = 0.dp)

/** Material 3 FilterChip without the hover shadow. */
@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = FilterChipDefaults.shape,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(),
    elevation: SelectableChipElevation? = flatSelectable(),
    border: BorderStroke? = FilterChipDefaults.filterChipBorder(enabled, selected),
    interactionSource: MutableInteractionSource? = null,
) = androidx.compose.material3.FilterChip(
    selected, onClick, label, modifier, enabled, leadingIcon, trailingIcon, shape, colors, elevation, border,
    interactionSource ?: remember { MutableInteractionSource() },
)

/** Material 3 AssistChip without the hover shadow. */
@Composable
fun AssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = AssistChipDefaults.shape,
    colors: ChipColors = AssistChipDefaults.assistChipColors(),
    elevation: ChipElevation? = flatAssist(),
    border: BorderStroke? = AssistChipDefaults.assistChipBorder(enabled),
    interactionSource: MutableInteractionSource? = null,
) = androidx.compose.material3.AssistChip(
    onClick, label, modifier, enabled, leadingIcon, trailingIcon, shape, colors, elevation, border,
    interactionSource ?: remember { MutableInteractionSource() },
)

/** Material 3 SuggestionChip without the hover shadow. */
@Composable
fun SuggestionChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    shape: Shape = SuggestionChipDefaults.shape,
    colors: ChipColors = SuggestionChipDefaults.suggestionChipColors(),
    elevation: ChipElevation? = SuggestionChipDefaults.suggestionChipElevation(elevation = 0.dp, pressedElevation = 0.dp, focusedElevation = 0.dp, hoveredElevation = 0.dp, disabledElevation = 0.dp),
    border: BorderStroke? = SuggestionChipDefaults.suggestionChipBorder(enabled),
    interactionSource: MutableInteractionSource? = null,
) = androidx.compose.material3.SuggestionChip(
    onClick, label, modifier, enabled, icon, shape, colors, elevation, border,
    interactionSource ?: remember { MutableInteractionSource() },
)

/** Material 3 InputChip, flat on hover like the other chips. */
@Composable
fun InputChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    avatar: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = InputChipDefaults.shape,
    colors: SelectableChipColors = InputChipDefaults.inputChipColors(),
    elevation: SelectableChipElevation? = InputChipDefaults.inputChipElevation(elevation = 0.dp, pressedElevation = 0.dp, focusedElevation = 0.dp, hoveredElevation = 0.dp, disabledElevation = 0.dp),
    border: BorderStroke? = InputChipDefaults.inputChipBorder(enabled, selected),
    interactionSource: MutableInteractionSource? = null,
) = androidx.compose.material3.InputChip(
    selected, onClick, label, modifier, enabled, leadingIcon, avatar, trailingIcon, shape, colors, elevation, border,
    interactionSource ?: remember { MutableInteractionSource() },
)
