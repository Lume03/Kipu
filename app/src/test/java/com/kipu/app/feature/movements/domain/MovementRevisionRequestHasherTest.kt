package com.kipu.app.feature.movements.domain

import com.kipu.app.feature.movements.domain.model.MovementRevisionCommand
import com.kipu.app.feature.movements.domain.model.MovementRevisionPayload
import com.kipu.app.feature.movements.domain.model.MovementType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class MovementRevisionRequestHasherTest {
    @Test fun `editor correction matches deployed SQL hash including default kind and ISO milliseconds`() {
        // Golden vector obtained by SELECT internal.movement_revision_hash_v1 on 2026-11-06.
        val command = MovementRevisionCommand.Revise(
            idempotencyKey = "33333333-3333-3333-3333-333333333333",
            transactionId = "22222222-2222-2222-2222-222222222222",
            expectedRevision = 1,
            payload = MovementRevisionPayload(
                type = MovementType.EXPENSE, operationKind = null, amountMinor = 141,
                currency = "PEN", sourceAccountId = "11111111-1111-1111-1111-111111111111",
                occurredAt = Instant.parse("2026-11-06T12:00:00.123Z").toEpochMilli(),
            ),
        )
        assertEquals("c2f268972b38dcd068708668bf49743926d372507bd3ba20dea1e87c369727ed",
            MovementRevisionRequestHasher().computeHash(command))
        assertEquals(MovementRevisionRequestHasher().computeHash(command),
            MovementRevisionRequestHasher().computeHash(command.copy(payload = command.payload.copy(operationKind = "STANDARD"))))
    }
}
