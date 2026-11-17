package com.apilens.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apilens.presentation.dashboard.ApiFilter
import com.apilens.presentation.dashboard.ApiSortOrder
import com.apilens.presentation.theme.LocalApiLensColors

@Composable
fun FilterAndSortBar(
    selectedFilter: ApiFilter,
    selectedSort: ApiSortOrder,
    onFilterSelected: (ApiFilter) -> Unit,
    onSortSelected: (ApiSortOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalApiLensColors.current
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ApiFilter.entries.forEach { filter ->
                val selected = filter == selectedFilter
                FilterChip(
                    selected = selected,
                    onClick = { onFilterSelected(filter) },
                    label = {
                        Text(
                            text = filter.label,
                            fontSize = 12.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.accentCyan.copy(alpha = 0.2f),
                        selectedLabelColor = colors.accentCyan,
                        containerColor = colors.surfaceCard,
                        labelColor = colors.textSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = colors.borderSubtle,
                        selectedBorderColor = colors.accentCyan
                    ),
                    modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}")
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box {
            OutlinedButton(
                onClick = { sortMenuExpanded = true },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("sort_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Sort,
                    contentDescription = "Sort requests",
                    modifier = Modifier.size(16.dp),
                    tint = colors.accentCyan
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = selectedSort.label,
                    fontSize = 12.sp,
                    color = colors.textPrimary
                )
            }

            DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = { sortMenuExpanded = false }
            ) {
                ApiSortOrder.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = sort.label,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        },
                        onClick = {
                            sortMenuExpanded = false
                            onSortSelected(sort)
                        },
                        modifier = Modifier.testTag("sort_option_${sort.name.lowercase()}")
                    )
                }
            }
        }
    }
}
