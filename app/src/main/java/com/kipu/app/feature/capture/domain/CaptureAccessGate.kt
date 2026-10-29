package com.kipu.app.feature.capture.domain

import com.kipu.app.feature.plans.domain.model.EffectiveEntitlement

sealed interface CaptureAccessDecision {
    data object NoSession : CaptureAccessDecision
    data object PermissionMissing : CaptureAccessDecision
    data object ConsentMissing : CaptureAccessDecision
    data object PremiumRequired : CaptureAccessDecision

    /** [entitlement] is verified and carries its known expiry, ready for CaptureCandidate.authorize. */
    data class Allowed(val entitlement: EffectiveEntitlement) : CaptureAccessDecision
}

/**
 * Decides whether a signal may be read/processed and whether it may be auto-registered.
 * Evaluated per callback so revoking permission, consent or Premium stops capture immediately.
 */
interface CaptureAccessGate {
    /** Synchronous check at the lazy extras boundary: there must be no suspension after this check. */
    fun liveDenial(ownerId: String): CaptureAccessDecision? =
        if (currentOwnerId() == ownerId) null else CaptureAccessDecision.NoSession
    /** Owner of the active, unlocked local session, or null. */
    fun currentOwnerId(): String?

    /** Evaluates the session owner; [CaptureAccessDecision.NoSession] when there is none. */
    suspend fun evaluateCurrent(): CaptureAccessDecision

    /** Evaluates [ownerId]; denies with NoSession unless it is the active session owner. */
    suspend fun evaluate(ownerId: String): CaptureAccessDecision
}
