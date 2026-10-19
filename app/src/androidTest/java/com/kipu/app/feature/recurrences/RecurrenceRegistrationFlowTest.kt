package com.kipu.app.feature.recurrences

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.Transaction
import com.kipu.app.feature.movements.domain.model.TransactionItem
import com.kipu.app.feature.movements.presentation.MovementDetailSheet
import com.kipu.app.feature.recurrences.presentation.RecurrenceRegistrationScreen
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RecurrenceRegistrationFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun registrationCanStartFromExpenseDetailsAndKeepsSourceMovementUnchanged() {
        val source = movement()
        var opened = false
        var registered = false
        compose.setContent {
            MovementDetailSheet(
                item = source,
                revisions = emptyList(),
                loading = false,
                error = false,
                onDismiss = {},
                onEdit = {},
                onVoid = {},
                onRegisterRecurrence = { opened = true },
            )
        }
        compose.onNodeWithTag("detail_register_recurrence").performClick()
        compose.runOnIdle {
            assertEquals(true, opened)
            assertEquals(false, registered)
            assertEquals(1_250L, source.transaction.amountMinor)
            assertEquals("source-movement", source.transaction.id)
        }
    }

    @Test
    fun registrationRequiresNextDateAndLabelsApproximateAmountAsEstimate() {
        var submittedDraft: com.kipu.app.feature.recurrences.domain.RecurrenceDraft? = null
        compose.setContent {
            RecurrenceRegistrationScreen(
                source = movement(),
                onDismiss = {},
                onCreate = { submittedDraft = it },
            )
        }
        compose.onNodeWithTag("recurrence_amount_mode_approximate").performClick()
        compose.onNodeWithText("Monto aproximado · no es un pago realizado").assertIsDisplayed()
        compose.onNodeWithTag("recurrence_save").performClick()
        compose.onNodeWithText("Selecciona la próxima fecha de vencimiento").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("validation leaves command unissued", null, submittedDraft)
        }
        val selectedDate = LocalDate.now().plusDays(1)
        compose.onNodeWithTag("recurrence_next_due_date").performTextInput(selectedDate.toString())
        compose.onNodeWithTag("recurrence_save").performClick()
        compose.runOnIdle {
            assertEquals("APPROXIMATE", submittedDraft?.amountMode)
            assertEquals(selectedDate, submittedDraft?.nextDueDate)
            assertEquals(selectedDate, submittedDraft?.startDate)
            assertEquals("account-1", submittedDraft?.templateTransaction?.get("source_account_id")?.toString()?.trim('"'))
            assertEquals("1250", submittedDraft?.templateTransaction?.get("amount_minor")?.toString())
            assertEquals(2, submittedDraft?.reminderLeadDays)
        }
    }

    @Test
    fun reminderLeadCanBeEditedWithinExistingRangeAndZeroDisablesItWithoutChangingSource() {
        var draft: com.kipu.app.feature.recurrences.domain.RecurrenceDraft? = null
        val source = movement()
        compose.setContent { RecurrenceRegistrationScreen(source, {}, { draft = it }) }

        compose.onNodeWithTag("recurrence_reminder_lead_days").performTextClearance()
        compose.onNodeWithTag("recurrence_reminder_lead_days").performTextInput("30")
        compose.onNodeWithTag("recurrence_next_due_date").performTextInput("2026-11-10")
        compose.onNodeWithTag("recurrence_save").performClick()
        compose.runOnIdle {
            assertEquals(30, draft?.reminderLeadDays)
            assertEquals("source-movement", source.transaction.id)
            assertEquals(1_250L, source.transaction.amountMinor)
        }

        draft = null
        compose.onNodeWithTag("recurrence_reminder_lead_days").performTextClearance()
        compose.onNodeWithTag("recurrence_reminder_lead_days").performTextInput("0")
        compose.onNodeWithTag("recurrence_save").performClick()
        compose.runOnIdle { assertEquals(0, draft?.reminderLeadDays) }
    }

    @Test
    fun reminderLeadOutsideExistingRangeIsRejected() {
        var draft: com.kipu.app.feature.recurrences.domain.RecurrenceDraft? = null
        compose.setContent { RecurrenceRegistrationScreen(movement(), {}, { draft = it }) }
        compose.onNodeWithTag("recurrence_reminder_lead_days").performTextClearance()
        compose.onNodeWithTag("recurrence_reminder_lead_days").performTextInput("31")
        compose.onNodeWithTag("recurrence_next_due_date").performTextInput("2026-11-10")
        compose.onNodeWithTag("recurrence_save").performClick()
        compose.onNodeWithText("El aviso debe estar entre 0 y 30 días").assertIsDisplayed()
        compose.runOnIdle { assertEquals(null, draft) }
    }

    @Test
    fun customWeeklyIntervalSubmitsUnitAndUpperBoundary() {
        var draft: com.kipu.app.feature.recurrences.domain.RecurrenceDraft? = null
        val date = LocalDate.of(2026, 1, 2)
        compose.setContent { RecurrenceRegistrationScreen(movement(), {}, { draft = it }) }
        compose.onNodeWithTag("recurrence_frequency_custom").performClick()
        compose.onNodeWithTag("recurrence_custom_unit_week").performClick()
        compose.onNodeWithTag("recurrence_custom_interval").performTextClearance()
        compose.onNodeWithTag("recurrence_custom_interval").performTextInput("5200")
        compose.onNodeWithTag("recurrence_next_due_date").performTextInput(date.toString())
        compose.onNodeWithTag("recurrence_save").performClick()
        compose.runOnIdle {
            assertEquals("CUSTOM", draft?.frequency)
            assertEquals(5200L, draft?.intervalCount)
            assertEquals("WEEK", draft?.customIntervalUnit)
            assertEquals(date, draft?.nextDueDate)
        }
    }

    @Test
    fun customMonthlyIntervalSubmitsUnitAndLowerBoundary() {
        var draft: com.kipu.app.feature.recurrences.domain.RecurrenceDraft? = null
        val date = LocalDate.of(2026, 1, 31)
        compose.setContent { RecurrenceRegistrationScreen(movement(), {}, { draft = it }) }
        compose.onNodeWithTag("recurrence_frequency_custom").performClick()
        compose.onNodeWithTag("recurrence_custom_unit_month").performClick()
        compose.onNodeWithTag("recurrence_custom_interval").performTextClearance()
        compose.onNodeWithTag("recurrence_custom_interval").performTextInput("1")
        compose.onNodeWithTag("recurrence_next_due_date").performTextInput(date.toString())
        compose.onNodeWithTag("recurrence_save").performClick()
        compose.runOnIdle {
            assertEquals("CUSTOM", draft?.frequency)
            assertEquals(1L, draft?.intervalCount)
            assertEquals("MONTH", draft?.customIntervalUnit)
            assertEquals(date, draft?.startDate)
            assertEquals(date, draft?.nextDueDate)
        }
    }

    @Test
    fun customWeeklyAboveUpperBoundaryIsRejected() {
        var draft: com.kipu.app.feature.recurrences.domain.RecurrenceDraft? = null
        compose.setContent { RecurrenceRegistrationScreen(movement(), {}, { draft = it }) }
        compose.onNodeWithTag("recurrence_frequency_custom").performClick()
        compose.onNodeWithTag("recurrence_custom_interval").performTextClearance()
        compose.onNodeWithTag("recurrence_custom_interval").performTextInput("5201")
        compose.onNodeWithTag("recurrence_next_due_date").performTextInput("2026-01-02")
        compose.onNodeWithTag("recurrence_save").performClick()
        compose.onNodeWithText("El intervalo debe estar dentro del rango permitido").assertIsDisplayed()
        compose.runOnIdle { assertEquals(null, draft) }
    }

    @Test
    fun customMonthlyAboveUpperBoundaryIsRejected() {
        var draft: com.kipu.app.feature.recurrences.domain.RecurrenceDraft? = null
        compose.setContent { RecurrenceRegistrationScreen(movement(), {}, { draft = it }) }
        compose.onNodeWithTag("recurrence_frequency_custom").performClick()
        compose.onNodeWithTag("recurrence_custom_unit_month").performClick()
        compose.onNodeWithTag("recurrence_custom_interval").performTextClearance()
        compose.onNodeWithTag("recurrence_custom_interval").performTextInput("1201")
        compose.onNodeWithTag("recurrence_next_due_date").performTextInput("2026-01-31")
        compose.onNodeWithTag("recurrence_save").performClick()
        compose.onNodeWithText("El intervalo debe estar dentro del rango permitido").assertIsDisplayed()
        compose.runOnIdle { assertEquals(null, draft) }
    }

    @Test
    fun customZeroIntervalWithValidDateIsRejected() {
        var draft: com.kipu.app.feature.recurrences.domain.RecurrenceDraft? = null
        compose.setContent { RecurrenceRegistrationScreen(movement(), {}, { draft = it }) }
        compose.onNodeWithTag("recurrence_frequency_custom").performClick()
        compose.onNodeWithTag("recurrence_custom_interval").performTextClearance()
        compose.onNodeWithTag("recurrence_custom_interval").performTextInput("0")
        compose.onNodeWithTag("recurrence_next_due_date").performTextInput("2026-02-28")
        compose.onNodeWithTag("recurrence_save").performClick()
        compose.onNodeWithText("El intervalo debe estar dentro del rango permitido").assertIsDisplayed()
        compose.runOnIdle { assertEquals(null, draft) }
    }

    @Test
    fun impossibleDateWithValidCustomIntervalIsRejected() {
        var draft: com.kipu.app.feature.recurrences.domain.RecurrenceDraft? = null
        compose.setContent { RecurrenceRegistrationScreen(movement(), {}, { draft = it }) }
        compose.onNodeWithTag("recurrence_frequency_custom").performClick()
        compose.onNodeWithTag("recurrence_custom_interval").performTextClearance()
        compose.onNodeWithTag("recurrence_custom_interval").performTextInput("1")
        compose.onNodeWithTag("recurrence_next_due_date").performTextInput("2026-02-30")
        compose.onNodeWithTag("recurrence_save").performClick()
        compose.onNodeWithText("Selecciona la próxima fecha de vencimiento").assertIsDisplayed()
        compose.runOnIdle { assertEquals(null, draft) }
    }

    private fun movement() = TransactionItem(
        transaction = Transaction(
            id = "source-movement",
            userId = "user-1",
            type = MovementType.EXPENSE,
            amountMinor = 1_250L,
            currency = "PEN",
            sourceAccountId = "account-1",
            categoryId = "category-1",
            occurredAt = 1_760_000_000_000L,
        ),
        merchantName = "Mercado",
        categoryName = "Alimentos",
    )
}
