package com.kipu.app.feature.plans.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.feature.plans.domain.ConfirmPlanSelection
import com.kipu.app.feature.plans.domain.PlanPreferencesRepository
import com.kipu.app.feature.plans.domain.model.CommercialOption
import com.kipu.app.feature.plans.domain.model.PlanSelection
import com.kipu.app.feature.plans.domain.model.TrialEligibilitySnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@HiltViewModel
class PlanSelectionViewModel @Inject constructor(
    private val repository: PlanPreferencesRepository,
    private val confirmPlanSelection: ConfirmPlanSelection,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PlanSelectionUiState())
    val uiState: StateFlow<PlanSelectionUiState> = mutableState.asStateFlow()
    private val eventChannel = Channel<PlanSelectionEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    init {
        viewModelScope.launch {
            val preference = try {
                repository.currentPreference.first()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            mutableState.update {
                it.copy(
                    selectedOption = when (preference) {
                        PlanSelection.FREE -> CommercialOption.FREE
                        PlanSelection.TRIAL_INTENT, PlanSelection.PREMIUM_INTENT, null -> CommercialOption.ANNUAL
                    },
                    isLoadingPreference = false,
                )
            }
        }
        viewModelScope.launch {
            val eligibility = try {
                repository.getTrialEligibility()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                TrialEligibilitySnapshot.UNKNOWN
            }
            mutableState.update { it.copy(eligibility = eligibility, isLoadingEligibility = false) }
        }
    }

    fun onOptionSelected(option: CommercialOption) = mutableState.update {
        if (it.isConfirming || it.isLoadingPreference) it else it.copy(
            selectedOption = option,
            premiumOption = if (option == CommercialOption.FREE) it.premiumOption else option,
            error = null,
        )
    }
    fun confirmSelection() = confirm(mutableState.value.selectedOption)
    fun continueWithFree() = confirm(CommercialOption.FREE)
    private fun confirm(option: CommercialOption) {
        val confirmation = mutableState.value
        if (!confirmation.canConfirm) return
        mutableState.update { it.copy(isConfirming = true, error = null) }
        viewModelScope.launch {
            try {
                confirmPlanSelection(option, confirmation.eligibility)
                eventChannel.send(PlanSelectionEvent.Confirmed)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(error = PlanSelectionError.CONFIRMATION_FAILED) }
            } finally {
                mutableState.update { it.copy(isConfirming = false) }
            }
        }
    }
}
