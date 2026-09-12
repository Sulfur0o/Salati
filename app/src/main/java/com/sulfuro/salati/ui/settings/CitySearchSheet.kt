package com.sulfuro.salati.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.sulfuro.salati.R
import com.sulfuro.salati.core.location.CityGeocoder
import com.sulfuro.salati.core.location.CitySearchResult
import com.sulfuro.salati.core.location.CitySuggestion
import com.sulfuro.salati.theme.SalatiShapeTokens
import com.sulfuro.salati.theme.SalatiSpacing
import kotlinx.coroutines.delay

/** Typing pause before a geocode request fires, so each keystroke is not a lookup. */
private const val SEARCH_DEBOUNCE_MILLIS = 350L

/**
 * City-name search that replaces manual latitude/longitude entry. The user never sees a
 * coordinate: picking a suggestion hands the caller a resolved [CitySuggestion] and the
 * settings screen turns that into a time zone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CitySearchSheet(
    onSelect: (CitySuggestion) -> Unit,
    onDismiss: () -> Unit,
    /**
     * Offered above the search field when the caller can detect a position. The settings
     * card used to carry this as a row of its own beside the search row; both were ways of
     * answering the same question, so they belong in the same place.
     */
    onUseCurrentLocation: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]

    var query by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CitySearchResult?>(null) }

    LaunchedEffect(query, locale) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            result = null
            isSearching = false
            return@LaunchedEffect
        }
        delay(SEARCH_DEBOUNCE_MILLIS)
        isSearching = true
        result = CityGeocoder.search(
            context = context.applicationContext,
            query = trimmed,
            locale = locale
        )
        isSearching = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SalatiSpacing.md, vertical = SalatiSpacing.xs)
        ) {
            Text(
                text = stringResource(R.string.city_search_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.city_search_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (onUseCurrentLocation != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button, onClick = onUseCurrentLocation)
                        .padding(vertical = SalatiSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(SalatiSpacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.settings_location_use_gps),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(bottom = SalatiSpacing.xs),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.city_search_placeholder)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.city_search_clear)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = SalatiShapeTokens.Control,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = SalatiSpacing.sm)
            )

            when {
                isSearching -> StatusLine(
                    text = stringResource(R.string.city_search_searching),
                    showSpinner = true
                )

                query.trim().length < 2 -> StatusLine(
                    text = stringResource(R.string.city_search_hint)
                )

                result is CitySearchResult.Unavailable -> StatusLine(
                    text = stringResource(R.string.city_search_unavailable),
                    isError = true
                )

                result is CitySearchResult.Failure -> StatusLine(
                    text = stringResource(R.string.city_search_failed),
                    isError = true
                )

                result is CitySearchResult.NoMatches -> StatusLine(
                    text = stringResource(R.string.city_search_no_matches)
                )

                result is CitySearchResult.Success -> {
                    val suggestions = (result as CitySearchResult.Success).suggestions
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .padding(top = SalatiSpacing.xs)
                    ) {
                        items(suggestions, key = { "${it.displayName}${it.latitude}${it.longitude}" }) { suggestion ->
                            CitySuggestionRow(
                                suggestion = suggestion,
                                onClick = { onSelect(suggestion) }
                            )
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier.padding(bottom = SalatiSpacing.lg)
            )
        }
    }
}

@Composable
private fun CitySuggestionRow(
    suggestion: CitySuggestion,
    onClick: () -> Unit
) {
    val rowDescription = stringResource(
        R.string.city_search_result_accessibility,
        suggestion.displayName
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = rowDescription }
            .padding(vertical = SalatiSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(SalatiSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.LocationCity,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = suggestion.displayName,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatusLine(
    text: String,
    showSpinner: Boolean = false,
    isError: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SalatiSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(SalatiSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showSpinner) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}
