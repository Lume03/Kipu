package com.kipu.app.feature.recurrences

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.Transaction
import com.kipu.app.feature.movements.domain.model.TransactionItem
import com.kipu.app.feature.movements.presentation.MovementDetailSheet
import com.kipu.app.feature.recurrences.domain.RecurrenceDraft
import com.kipu.app.feature.recurrences.presentation.RecurrenceRegistrationScreen
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RecurrenceRegistrationFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fixedAmountIsAnExpectedTemplateAndDoesNotAlterSourcePayment() {
        var submitted: RecurrenceDraft? = null
        val source = movement()
        compose.setContent { RecurrenceRegistrationScreen(source, {}, { submitted = it }) }
        compose.onNodeWithTag("recurrence_amount_mode_fixed").performScrollTo().performClick()
        compose.onNodeWithText("Monto fijo esperado · no se registra como pago").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("recurrence_save").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("FIXED", submitted?.amountMode)
            assertEquals("1250", submitted?.templateTransaction?.get("amount_minor")?.toString())
            assertEquals(movement(), source)
            assertNull(submitted?.templateTransaction?.get("paid_at"))
            assertNull(submitted?.templateTransaction?.get("transaction_id"))
        }
    }

    @Test fun registrationCanStartFromExpenseDetailsAndKeepsSourceUnchanged() {
        var opened = false
        val source = movement()
        compose.setContent { MovementDetailSheet(item = source, revisions = emptyList(), loading = false, error = false,
            onDismiss = {}, onEdit = {}, onVoid = {}, onRegisterRecurrence = { opened = true }) }
        compose.onNodeWithTag("detail_register_recurrence").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(opened); assertEquals(1250L, source.transaction.amountMinor) }
    }

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Test fun calendarChangesRealDateAndApproximateModePreservesTemplate() {
        var submitted: RecurrenceDraft? = null
        val today = LocalDate.now(com.kipu.app.core.di.LIMA_BUSINESS_ZONE)
        val day = if (today.dayOfMonth == 1) 2 else 1
        showRegistration { submitted = it }
        compose.onNodeWithTag("recurrence_amount_mode_approximate").performScrollTo().performClick()
        compose.onNodeWithTag("recurrence_next_due_date").performScrollTo().performClick()
        compose.onNodeWithTag("recurrence_date_picker").assertIsDisplayed()
        val targetMillis = today.withDayOfMonth(day).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val locale = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.locales[0]
        val dateDescription = androidx.compose.material3.DatePickerDefaults.dateFormatter().formatDate(targetMillis, locale, forContentDescription = true)!!
        compose.onNode(hasText(dateDescription, substring = true, ignoreCase = true) or
            hasContentDescription(dateDescription, substring = true, ignoreCase = true), useUnmergedTree = true).performClick()
        compose.onNodeWithTag("recurrence_date_confirm").performClick()
        compose.onNodeWithTag("recurrence_save").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(today.withDayOfMonth(day), submitted?.nextDueDate)
            assertEquals(submitted?.nextDueDate, submitted?.startDate)
            assertEquals("APPROXIMATE", submitted?.amountMode)
            assertEquals("1250", submitted?.templateTransaction?.get("amount_minor")?.toString())
            assertEquals(2, submitted?.reminderLeadDays)
        }
    }

    @Test fun biweeklyMenuMakesFourteenDayMeaningExplicit() {
        var submitted: RecurrenceDraft? = null
        showRegistration { submitted = it }
        compose.onNodeWithTag("recurrence_frequency_selector").performScrollTo().performClick()
        compose.onNodeWithTag("recurrence_frequency_biweekly").performClick()
        compose.onNodeWithText("Quincenal: cada 2 semanas (14 d\u00edas), desde la fecha elegida.").assertIsDisplayed()
        captureFixture("recurrence-form.png")
        compose.onNodeWithTag("recurrence_save").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("BIWEEKLY", submitted?.frequency); assertEquals(1L, submitted?.intervalCount) }
    }

    @Test fun reminderBoundsAndZeroArePreserved() {
        var submitted: RecurrenceDraft? = null
        showRegistration { submitted = it }
        setLead("31")
        compose.onNodeWithTag("recurrence_save").performScrollTo().performClick()
        compose.runOnIdle { assertNull(submitted) }
        setLead("30")
        compose.onNodeWithTag("recurrence_save").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(30, submitted?.reminderLeadDays) }
        setLead("0")
        compose.onNodeWithTag("recurrence_save").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, submitted?.reminderLeadDays) }
    }

    @Test fun customWeeklyUpperBoundaryAndInvalidZeroAreValidated() {
        var submitted: RecurrenceDraft? = null
        showRegistration { submitted = it }
        compose.onNodeWithTag("recurrence_frequency_selector").performScrollTo().performClick()
        compose.onNodeWithTag("recurrence_frequency_custom").performClick()
        setInterval("0")
        compose.onNodeWithTag("recurrence_save").performScrollTo().performClick()
        compose.runOnIdle { assertNull(submitted) }
        setInterval("5200")
        compose.onNodeWithTag("recurrence_save").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("WEEK", submitted?.customIntervalUnit); assertEquals(5200L, submitted?.intervalCount) }
    }

    @Test fun customMonthlyUpperBoundaryAndOverflowAreValidated() {
        var submitted: RecurrenceDraft? = null
        showRegistration { submitted = it }
        compose.onNodeWithTag("recurrence_frequency_selector").performScrollTo().performClick()
        compose.onNodeWithTag("recurrence_frequency_custom").performClick()
        compose.onNodeWithTag("recurrence_custom_unit_month").performScrollTo().performClick()
        setInterval("1201")
        compose.onNodeWithTag("recurrence_save").performScrollTo().performClick()
        compose.runOnIdle { assertNull(submitted) }
        setInterval("1200")
        compose.onNodeWithTag("recurrence_save").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("MONTH", submitted?.customIntervalUnit); assertEquals(1200L, submitted?.intervalCount) }
    }

    @Test fun darkThemeRetainsReadableFields() {
        showRegistration(dark = true) { }
        compose.onNodeWithTag("recurrence_concept").assertIsDisplayed()
        compose.onNodeWithTag("recurrence_next_due_date").performScrollTo().assertIsDisplayed()
        captureFixture("recurrence-form-dark.png")
    }

    private fun showRegistration(dark: Boolean = false, create: (RecurrenceDraft) -> Unit) {
        compose.setContent {
            com.kipu.app.ui.theme.KipuTheme(darkTheme = dark) {
                androidx.compose.material3.Surface(color = com.kipu.app.ui.theme.rememberCalmEmeraldColors().surfaceCard) {
                    RecurrenceRegistrationScreen(movement(), {}, create)
                }
            }
        }
    }
    private fun captureFixture(name: String) {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.getExternalFilesDir(null), name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun setLead(value: String) {
        compose.onNodeWithTag("recurrence_reminder_lead_days").performScrollTo().performTextClearance()
        compose.onNodeWithTag("recurrence_reminder_lead_days").performTextInput(value)
    }
    private fun setInterval(value: String) {
        compose.onNodeWithTag("recurrence_custom_interval").performScrollTo().performTextClearance()
        compose.onNodeWithTag("recurrence_custom_interval").performTextInput(value)
    }
    private fun movement() = TransactionItem(
        transaction = Transaction(id = "source-movement", userId = "user-1", type = MovementType.EXPENSE,
            amountMinor = 1250L, currency = "PEN", sourceAccountId = "account-1", categoryId = "category-1", occurredAt = 1760000000000L, createdAt = 1760000000000L, updatedAt = 1760000000000L),
        merchantName = "Netflix", merchantLogoKey = "ic_merchant_netflix", categoryName = "Entretenimiento",
    )
}
