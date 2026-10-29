package com.kipu.app.feature.plans.presentation

import androidx.annotation.StringRes
import com.kipu.app.R
import com.kipu.app.feature.plans.domain.model.*

enum class PlanSelectionError { CONFIRMATION_FAILED }
sealed interface PlanSelectionEvent { data object Confirmed : PlanSelectionEvent }

data class PlanSelectionUiState(
    val selectedOption: CommercialOption = CommercialOption.ANNUAL,
    val eligibility: TrialEligibilitySnapshot = TrialEligibilitySnapshot.UNKNOWN,
    val isLoadingEligibility: Boolean = true,
    val isLoadingPreference: Boolean = true,
    val isConfirming: Boolean = false,
    val error: PlanSelectionError? = null,
    val freeLimits: FreePlanLimits = FreePlanLimits(),
    val premiumOption: CommercialOption = CommercialOption.ANNUAL,
) {
    val isSubmitting get() = isConfirming
    val canConfirm get() = !isLoadingPreference && !isLoadingEligibility && !isConfirming

    val premiumNote: Int
        @StringRes get() = when (selectedOption) {
            CommercialOption.LIFETIME -> R.string.plan_display_lifetime_note
            CommercialOption.MONTHLY -> if (!isLoadingEligibility && eligibility.isEligible()) {
                R.string.plan_display_monthly_trial
            } else R.string.plan_display_monthly_note
            else -> if (!isLoadingEligibility && eligibility.isEligible()) {
                R.string.plan_display_annual_trial
            } else R.string.plan_display_annual_note
        }
}
