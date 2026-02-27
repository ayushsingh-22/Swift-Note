package com.amvarpvtltd.swiftNote.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.amvarpvtltd.swiftNote.design.NoteTheme
import com.amvarpvtltd.swiftNote.search.SearchAndSortManager
import com.amvarpvtltd.swiftNote.search.SortOption
import com.amvarpvtltd.swiftNote.utils.Constants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSearchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search notes..."
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val hapticFeedback = LocalHapticFeedback.current

    // Local state for immediate text updates to ensure typing works
    var localSearchQuery by remember { mutableStateOf(searchQuery) }

    // Sync local state with external prop changes
    LaunchedEffect(searchQuery) {
        if (localSearchQuery != searchQuery) {
            localSearchQuery = searchQuery
        }
    }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            focusRequester.requestFocus()
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp) // Increased from 48dp to 50dp
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = NoteTheme.Surface),
        shape = RoundedCornerShape(Constants.CORNER_RADIUS_MEDIUM.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "Search",
                tint = if (isSearchActive || localSearchQuery.isNotEmpty()) NoteTheme.Primary else NoteTheme.OnSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            TextField(
                value = localSearchQuery,
                onValueChange = { newValue ->
                    localSearchQuery = newValue
                    onSearchQueryChange(newValue)
                    // Update search active state immediately
                    if (newValue.isNotEmpty() && !isSearchActive) {
                        onSearchActiveChange(true)
                    } else if (newValue.isEmpty() && isSearchActive) {
                        onSearchActiveChange(false)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = {
                    Text(
                        text = placeholder,
                        color = NoteTheme.OnSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                        if (localSearchQuery.isNotEmpty()) {
                            onSearchActiveChange(true)
                        }
                    }
                ),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    cursorColor = NoteTheme.Primary,
                    focusedTextColor = NoteTheme.OnSurface,
                    unfocusedTextColor = NoteTheme.OnSurface
                ),
                textStyle = MaterialTheme.typography.bodyMedium
            )

            AnimatedVisibility(
                visible = localSearchQuery.isNotEmpty(),
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                IconButton(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        localSearchQuery = ""
                        onClearSearch()
                        onSearchActiveChange(false)
                        keyboardController?.hide()
                    },
                    modifier = Modifier.size(30.dp) // Increased clear button size slightly for 50dp height
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear search",
                        tint = NoteTheme.OnSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortOptionsSheet(
    currentSort: SortOption,
    onSortChange: (SortOption) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NoteTheme.Surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Constants.PADDING_MEDIUM.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sort Notes",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = NoteTheme.OnSurface
                )

                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = NoteTheme.OnSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(Constants.PADDING_MEDIUM.dp))

            // Sort options
            SortOption.values().forEach { option ->
                SortOptionItem(
                    sortOption = option,
                    isSelected = currentSort == option,
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSortChange(option)
                        onDismiss()
                    }
                )
            }

            Spacer(modifier = Modifier.height(Constants.PADDING_LARGE.dp))
        }
    }
}

@Composable
private fun SortOptionItem(
    sortOption: SortOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                NoteTheme.Primary.copy(alpha = 0.1f)
            else
                Color.Transparent
        ),
        shape = RoundedCornerShape(Constants.CORNER_RADIUS_SMALL.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Constants.PADDING_MEDIUM.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = SearchAndSortManager.getSortIcon(sortOption),
                contentDescription = null,
                tint = if (isSelected) NoteTheme.Primary else NoteTheme.OnSurfaceVariant,
                modifier = Modifier.size(Constants.ICON_SIZE_LARGE.dp)
            )

            Spacer(modifier = Modifier.width(Constants.PADDING_MEDIUM.dp))

            Text(
                text = SearchAndSortManager.getSortOptionLabel(sortOption),
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) NoteTheme.Primary else NoteTheme.OnSurface,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = NoteTheme.Primary,
                    modifier = Modifier.size(Constants.ICON_SIZE_MEDIUM.dp)
                )
            }
        }
    }
}

@Composable
fun ThemeToggleButton(
    currentTheme: com.amvarpvtltd.swiftNote.theme.ThemeMode,
    onThemeChange: (com.amvarpvtltd.swiftNote.theme.ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current

    // Use adaptive colors for consistency with other buttons
    val (containerColor, contentColor) = adaptiveIconColors(NoteTheme.Background, NoteTheme.Secondary)

    Card(
        modifier = modifier
            .clickable {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                val nextTheme = when (currentTheme) {
                    com.amvarpvtltd.swiftNote.theme.ThemeMode.LIGHT -> com.amvarpvtltd.swiftNote.theme.ThemeMode.DARK
                    com.amvarpvtltd.swiftNote.theme.ThemeMode.DARK -> com.amvarpvtltd.swiftNote.theme.ThemeMode.SYSTEM
                    com.amvarpvtltd.swiftNote.theme.ThemeMode.SYSTEM -> com.amvarpvtltd.swiftNote.theme.ThemeMode.LIGHT
                }

                // Show toast notification based on the theme being changed to
                val toastMessage = when (nextTheme) {
                    com.amvarpvtltd.swiftNote.theme.ThemeMode.LIGHT -> "🌞 Light mode activated"
                    com.amvarpvtltd.swiftNote.theme.ThemeMode.DARK -> "🌙 Dark mode activated"
                    com.amvarpvtltd.swiftNote.theme.ThemeMode.SYSTEM -> "🔄 System theme activated"
                }

                Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
                onThemeChange(nextTheme)
            },
        shape = CircleShape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier.padding(Constants.PADDING_SMALL.dp),
            contentAlignment = Alignment.Center
        ) {
            val (icon, contentDescription) = when (currentTheme) {
                com.amvarpvtltd.swiftNote.theme.ThemeMode.LIGHT -> Icons.Outlined.LightMode to "Light mode"
                com.amvarpvtltd.swiftNote.theme.ThemeMode.DARK -> Icons.Outlined.DarkMode to "Dark mode"
                com.amvarpvtltd.swiftNote.theme.ThemeMode.SYSTEM -> Icons.Outlined.SettingsBrightness to "System theme"
            }

            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.Black,
                modifier = Modifier.size(Constants.ICON_SIZE_LARGE.dp)
            )
        }
    }
}

@Composable
fun SyncStatusIndicator(
    isOnline: Boolean,
    hasPendingSync: Boolean,
    isSyncing: Boolean = false,
    onSyncClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current

    // Only show the indicator when there's actually syncing happening or pending sync with offline status
    AnimatedVisibility(
        visible = isSyncing || (hasPendingSync && !isOnline),
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
        modifier = modifier
    ) {
        // Determine adaptive colors using background luminance so icon remains visible
        val (containerColor, contentColor) = when {
            isSyncing -> Pair(NoteTheme.Primary.copy(alpha = 0.1f), NoteTheme.Primary)
            !isOnline -> adaptiveIconColors(NoteTheme.Background, NoteTheme.Error)
            else -> adaptiveIconColors(NoteTheme.Background, NoteTheme.Warning)
        }

        Card(
            modifier = Modifier
                .clickable(enabled = !isSyncing && hasPendingSync) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSyncClick()
                },
            colors = CardDefaults.cardColors(
                containerColor = containerColor
            ),
            shape = RoundedCornerShape(Constants.CORNER_RADIUS_SMALL.dp)
        ) {
            Row(
                modifier = Modifier.padding(Constants.PADDING_SMALL.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSyncing) {
                    // Show spinning animation when syncing
                    CircularProgressIndicator(
                        modifier = Modifier.size(Constants.ICON_SIZE_SMALL.dp),
                        strokeWidth = 2.dp,
                        color = contentColor
                    )
                } else {
                    Icon(
                        imageVector = if (isOnline) Icons.Outlined.CloudSync else Icons.Outlined.CloudOff,
                        contentDescription = if (isOnline) "Sync pending" else "Offline",
                        tint = contentColor,
                        modifier = Modifier.size(Constants.ICON_SIZE_SMALL.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = when {
                        isSyncing -> "Syncing..."
                        !isOnline -> "Offline"
                        else -> "Sync"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
