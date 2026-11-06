package com.kipu.app.feature.movements.data.local

import com.kipu.app.feature.movements.domain.model.MovementRevisionAudit

internal fun List<TransactionRevisionEntity>.toMovementRevisionAudit(currentRevision: Long? = null): List<MovementRevisionAudit> =
    sortedByDescending { it.createdAt }.map { revision ->
        val correctionRejected = revision.provenance == "RECONCILIATION" &&
            any { it.commandType == "REVISE" && it.localRevision == revision.baseRevision && it.createdAt <= revision.createdAt }
        MovementRevisionAudit(
            revision.localRevision,
            if (correctionRejected) "CORRECTION_REJECTED" else revision.commandType,
            revision.createdAt,
            revision.changeReason,
            revision.previousPayload.takeIf { correctionRejected && (currentRevision == null || currentRevision < revision.baseRevision) },
        )
    }
