package com.kipu.app.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.capture.domain.CaptureAccessDecision
import com.kipu.app.feature.capture.domain.CaptureAccessGate
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.receipts.domain.OCR_CONSENT_VERSION
import com.kipu.app.feature.settings.data.local.AccountSourceConsentEntity
import com.kipu.app.feature.settings.data.local.PermissionConsentDao
import com.kipu.app.feature.settings.domain.PermissionSourceGateway
import com.kipu.app.feature.settings.domain.model.CapabilityState
import com.kipu.app.feature.settings.domain.model.ConsentState
import com.kipu.app.feature.settings.domain.model.DeviceAuthorization
import com.kipu.app.feature.settings.domain.model.PermissionSource
import com.kipu.app.feature.settings.domain.model.ProcessingAuthorization
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val dao: PermissionConsentDao,
    private val gateway: PermissionSourceGateway,
    private val sessionCoordinator: SessionCoordinator,
    private val captureGate: CaptureAccessGate,
    private val categories: CategoriesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PermissionsUiState(isLoading = true))
    val uiState: StateFlow<PermissionsUiState> = _uiState.asStateFlow()
    private var refreshJob: Job? = null
    private var awaitingListenerSettings = false

    private val currentUserId: UUID?
        get() = sessionCoordinator.currentOwner?.verifiedUserId?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        }

    init {
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        val userId = currentUserId
        if (userId == null) {
            _uiState.update { it.copy(isLoading = false, message = "No hay usuario activo") }
            return
        }

        refreshJob = viewModelScope.launch {
            var reportSettingsResult = awaitingListenerSettings
            awaitingListenerSettings = false
            gateway.refreshAndSaveDeviceAuthorizations()
            combine(dao.observeAccountConsents(userId), categories.observePremiumVerified(UserId(userId.toString()))) { consents, premium ->
                consents to premium
            }.collectLatest { (consents, isPremium) ->
                val consentMap = consents.associateBy { it.source }
                val captureAllowed = captureGate.evaluate(userId.toString()) is CaptureAccessDecision.Allowed

                val items = PermissionSource.entries.map { source ->
                    val deviceAuth = gateway.checkDeviceAuthorization(source)
                    val consentEntity = consentMap[source.name]

                    val storedConsentState = consentEntity?.let {
                        runCatching { ConsentState.valueOf(it.consentState) }.getOrDefault(ConsentState.NOT_EXPLAINED)
                    } ?: ConsentState.NOT_EXPLAINED
                    val consentState = if (source == PermissionSource.YAPE_RECEIPT_OCR &&
                        consentEntity?.explanationVersion != OCR_CONSENT_VERSION
                    ) ConsentState.NOT_EXPLAINED else storedConsentState

                    val capabilityState = consentEntity?.let {
                        runCatching { CapabilityState.valueOf(it.capabilityState) }.getOrDefault(CapabilityState.UNKNOWN)
                    } ?: CapabilityState.UNKNOWN

                    val accountConsentModel = com.kipu.app.feature.settings.domain.model.AccountConsent(
                        userId = userId,
                        source = source,
                        consentState = consentState,
                        capabilityState = capabilityState,
                        explanationVersion = consentEntity?.explanationVersion ?: 1,
                    )

                    val authEval = ProcessingAuthorization.evaluate(
                        source = source,
                        deviceAuth = deviceAuth,
                        accountConsent = accountConsentModel,
                        isPremiumUser = isPremium,
                    )

                    when (source) {
                        PermissionSource.YAPE_RECEIPT_OCR -> PermissionItemUiState(
                            source = source, title = "OCR local de comprobantes Yape",
                            description = "Leer imágenes que eliges o compartes para preparar un comprobante.",
                            rationale = "Kipu leerá los comprobantes Yape que elijas en este dispositivo. El OCR está disponible en Free; no subimos ni conservamos la imagen. Revisarás los datos antes de registrar y puedes revocar este consentimiento aquí.",
                            deviceAuth = deviceAuth, consentState = consentState, capabilityState = capabilityState,
                            isProcessingAuthorized = authEval.isAuthorized && capabilityState == CapabilityState.ALLOWED,
                            requiresPremium = false,
                        )
                        PermissionSource.OWN_NOTIFICATIONS -> PermissionItemUiState(
                            source = source,
                            title = "Notificaciones de Kipu",
                            description = "Recordatorios de presupuesto, vencimientos de deudas y balances.",
                            rationale = "Para enviarte alertas oportunas sobre tus finanzas personales, necesitamos permiso para mostrar notificaciones en este dispositivo. Puedes activarlo o desactivarlo en cualquier momento.",
                            deviceAuth = deviceAuth,
                            consentState = consentState,
                            capabilityState = capabilityState,
                            isProcessingAuthorized = authEval.isAuthorized,
                            requiresPremium = false,
                        )
                        PermissionSource.OTHER_APP_NOTIFICATION_CONTENT -> PermissionItemUiState(
                            source = source,
                            title = "Lectura de Notificaciones Bancarias",
                            description = "Sugerir transacciones automáticamente desde notificaciones bancarias.",
                            rationale = "Kipu puede interpretar únicamente las alertas emitidas por aplicaciones bancarias compatibles para sugerir borradores de gastos. Ningún dato sensible sale de tu dispositivo. Esta función requiere el plan Premium.",
                            deviceAuth = deviceAuth,
                            consentState = consentState,
                            capabilityState = capabilityState,
                            isProcessingAuthorized = captureAllowed && authEval.isAuthorized,
                            requiresPremium = true,
                        )
                    }
                }

                val listener = items.first { it.source == PermissionSource.OTHER_APP_NOTIFICATION_CONTENT }
                _uiState.update {
                    it.copy(isLoading = false, items = items, isPremiumUser = isPremium,
                        message = if (reportSettingsResult) {
                            when {
                                listener.isProcessingAuthorized -> "Captura automática activada."
                                listener.needsAndroidActivation -> "El acceso sigue desactivado en Android. Puedes activarlo cuando quieras."
                                else -> null
                            }
                        } else it.message,
                    )
                }
                reportSettingsResult = false
            }
        }
    }

    fun showRationale(source: PermissionSource) {
        _uiState.update { it.copy(showRationaleFor = source) }
    }

    fun dismissRationale() {
        _uiState.update { it.copy(showRationaleFor = null) }
    }

    fun grantConsent(source: PermissionSource) {
        val userId = currentUserId ?: return
        viewModelScope.launch {
            if (source == PermissionSource.OTHER_APP_NOTIFICATION_CONTENT &&
                (captureGate.currentOwnerId() != userId.toString() ||
                    !categories.observePremiumVerified(UserId(userId.toString())).first())
            ) {
                _uiState.update {
                    it.copy(showRationaleFor = null, message = "Esta fuente de automatización requiere una suscripción Premium activa.")
                }
                return@launch
            }
            dao.putAccountConsent(
                AccountSourceConsentEntity(
                    userId = userId,
                    source = source.name,
                    consentState = ConsentState.GRANTED.name,
                    capabilityState = CapabilityState.ALLOWED.name,
                    explanationVersion = if (source == PermissionSource.YAPE_RECEIPT_OCR) OCR_CONSENT_VERSION else 1,
                    updatedAt = Instant.now(),
                )
            )
            val isListener = source == PermissionSource.OTHER_APP_NOTIFICATION_CONTENT
            val needsSettings = isListener && gateway.checkDeviceAuthorization(source) != DeviceAuthorization.GRANTED
            val listenerActive = isListener && !needsSettings && captureGate.evaluate(userId.toString()) is CaptureAccessDecision.Allowed
            _uiState.update {
                it.copy(showRationaleFor = null, openSettingsFor = source.takeIf { needsSettings },
                    message = when {
                        needsSettings -> "Tu consentimiento está guardado. Falta activar el acceso en Android."
                        listenerActive -> "Captura automática activada."
                        else -> null
                    },
                )
            }
        }
    }

    fun revokeConsent(source: PermissionSource) {
        val userId = currentUserId ?: return
        viewModelScope.launch {
            dao.putAccountConsent(
                AccountSourceConsentEntity(
                    userId = userId,
                    source = source.name,
                    consentState = ConsentState.REVOKED.name,
                    capabilityState = CapabilityState.DENIED.name,
                    explanationVersion = if (source == PermissionSource.YAPE_RECEIPT_OCR) OCR_CONSENT_VERSION else 1,
                    updatedAt = Instant.now(),
                )
            )
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun onSettingsOpened(source: PermissionSource) {
        awaitingListenerSettings = source == PermissionSource.OTHER_APP_NOTIFICATION_CONTENT
        _uiState.update { it.copy(openSettingsFor = null) }
    }

    fun onSettingsUnavailable() {
        awaitingListenerSettings = false
        _uiState.update { it.copy(openSettingsFor = null, message = "No se pudieron abrir los ajustes de Android. Puedes abrirlos desde Ajustes > Acceso a notificaciones.") }
    }
}
