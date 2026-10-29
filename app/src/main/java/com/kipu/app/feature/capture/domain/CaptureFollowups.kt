package com.kipu.app.feature.capture.domain

import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/** Stable persisted bits, independent of class names/obfuscation. */
object CaptureFollowup {
    const val LEARN = 1
    const val EXPECTED_PAYMENT = 2
    const val REVIEW_NOTICE = 4
    const val DISCARD_NOTICE = 8
    const val POST_PROCESS = 16
    const val PUBLISH_REVIEW = 32
    const val RESOLVED = LEARN or EXPECTED_PAYMENT or REVIEW_NOTICE
}

/** Failures leave their bit set; successful effects are individually acknowledged. */
class CaptureResolutionFollowups @Inject constructor(
    private val candidates: CaptureCandidateStore,
    private val resolved: Set<@JvmSuppressWildcards CaptureCandidateResolvedListener>,
    private val discarded: Set<@JvmSuppressWildcards CaptureCandidateDiscardedListener>,
) {
    suspend fun process(ownerId: String, id: String, ownerAvailable: () -> Boolean = { true }): Boolean {
        if (!ownerAvailable()) return false
        var complete = true
        val candidate = candidates.get(ownerId, id) ?: return true
        if (candidate.status == CaptureCandidateStatus.DISCARDED) {
            if (candidate.followupsPending and CaptureFollowup.DISCARD_NOTICE != 0) {
                try {
                    for (listener in discarded) listener.onDiscarded(candidate)
                    candidates.completeFollowup(ownerId, id, CaptureFollowup.DISCARD_NOTICE)
                } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { complete = false }
            }
        } else if (candidate.status != CaptureCandidateStatus.PENDING) {
            val transactionId = candidate.resolvedTransactionId ?: return false
            for (listener in resolved) {
                if (!ownerAvailable()) return false
                if (candidate.followupsPending and listener.followupFlag == 0) continue
                try {
                    listener.onResolved(candidate, transactionId)
                    candidates.completeFollowup(ownerId, id, listener.followupFlag)
                } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { complete = false }
            }
        }
        return complete
    }
}

/** Consults only committed receipts before similarity checks; never registers another movement. */
fun interface CaptureRegistrationReceipts {
    suspend fun find(ownerId: String, resolutionKey: String): com.kipu.app.feature.movements.domain.model.Transaction?
}
