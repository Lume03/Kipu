package com.kipu.app.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

internal data class FilterOption<T>(
    val value: T,
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val testTag: String = "",
)

@Composable
internal fun OptionFilterButton(
    label: String,
    active: Boolean,
    description: String,
    icon: ImageVector = Icons.Default.FilterList,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberCalmEmeraldColors()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (active) colors.primaryDeep else colors.surfaceCard,
        contentColor = if (active) colors.onPrimaryDeep else colors.primaryText,
        border = if (active) null else BorderStroke(1.dp, colors.pillTrack),
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = description },
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> OptionFilterSheet(
    title: String,
    options: List<FilterOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = rememberCalmEmeraldColors()
    KipuBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceCard,
        header = {
            Text(
                title,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.primaryText,
            )
        },
    ) {
        Column(Modifier.selectableGroup().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            options.forEach { option ->
                val isSelected = option.value == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(option.value) }
                        .testTag(option.testTag)
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(option.icon, contentDescription = null, tint = option.tint, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(
                        option.label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) colors.primaryDeep else colors.primaryText,
                    )
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = "Seleccionado", tint = colors.primaryDeep)
                    }
                }
            }
        }
    }
}

