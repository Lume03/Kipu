package com.kipu.app.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kipu.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Keeps the form's raw ISO input authoritative; the calendar owns only an unconfirmed draft. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KipuOptionalDateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    calendarContentDescription: String,
    tag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    shape: Shape = RoundedCornerShape(14.dp),
) {
    var showPicker by rememberSaveable(tag) { mutableStateOf(false) }
    var restoreCalendarFocus by remember(tag) { mutableStateOf(false) }
    val calendarFocus = remember(tag) { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    fun closePicker() {
        showPicker = false
        restoreCalendarFocus = true
    }

    LaunchedEffect(enabled) {
        if (!enabled) showPicker = false
    }
    LaunchedEffect(showPicker, restoreCalendarFocus, enabled) {
        if (!showPicker && restoreCalendarFocus) {
            if (enabled) calendarFocus.requestFocus()
            restoreCalendarFocus = false
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            label = { Text(label) },
            placeholder = { Text(stringResource(R.string.optional_date_iso_placeholder)) },
            singleLine = true,
            isError = isError,
            supportingText = if (isError) {
                { Text(stringResource(R.string.optional_date_invalid)) }
            } else null,
            shape = shape,
            trailingIcon = {
                IconButton(
                    onClick = {
                        keyboard?.hide()
                        focusManager.clearFocus()
                        showPicker = true
                    },
                    enabled = enabled,
                    modifier = Modifier.size(48.dp).focusRequester(calendarFocus).testTag("$tag-calendar"),
                ) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = calendarContentDescription)
                }
            },
            modifier = Modifier.fillMaxWidth().testTag(tag),
        )
        if (value.isNotEmpty()) {
            TextButton(
                onClick = {
                    keyboard?.hide()
                    onValueChange("")
                    restoreCalendarFocus = true
                },
                enabled = enabled,
                modifier = Modifier.testTag("$tag-clear"),
            ) {
                Text(stringResource(R.string.optional_date_clear))
            }
        }
    }

    if (showPicker && enabled) {
        // Composing this state only while open also discards a cancelled selection on reopening.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = optionalDatePickerInitialMillis(value),
            yearRange = OptionalDatePickerYears,
        )
        DatePickerDialog(
            onDismissRequest = ::closePicker,
            modifier = Modifier.testTag("$tag-dialog"),
            confirmButton = {
                TextButton(
                    onClick = {
                        if (enabled) {
                            pickerState.selectedDateMillis?.let { onValueChange(optionalDatePickerIso(it)) }
                            closePicker()
                        }
                    },
                    enabled = enabled && pickerState.selectedDateMillis != null,
                    modifier = Modifier.testTag("$tag-confirm"),
                ) { Text(stringResource(R.string.optional_date_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = ::closePicker, modifier = Modifier.testTag("$tag-cancel")) {
                    Text(stringResource(R.string.optional_date_cancel))
                }
            },
        ) {
            Column {
                if (optionalDateIsOutsidePickerRange(value)) {
                    Text(stringResource(R.string.optional_date_outside_calendar_range))
                }
                DatePicker(
                    state = pickerState,
                    showModeToggle = false,
                    modifier = Modifier.testTag("$tag-picker"),
                )
            }
        }
    }
}

internal val OptionalDatePickerYears = 1..9999

internal fun optionalDatePickerInitialMillis(value: String): Long? =
    runCatching { LocalDate.parse(value) }.getOrNull()
        ?.takeIf { it.year in OptionalDatePickerYears }
        ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()

internal fun optionalDatePickerIso(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()

private fun optionalDateIsOutsidePickerRange(value: String): Boolean =
    runCatching { LocalDate.parse(value) }.getOrNull()?.let { it.year !in OptionalDatePickerYears } == true
