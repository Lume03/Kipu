package com.kipu.app.feature.movements.data

import com.kipu.app.feature.movements.data.local.TransactionEntity
import com.kipu.app.feature.movements.domain.RefundTransactionRequestHasher
import com.kipu.app.feature.movements.domain.model.RefundTransactionCommand
import java.time.Instant
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class RefundWireContractTest {
    @Test fun wireMatchesRealPurchasePgTapFixture() {
        val command = RefundTransactionCommand(userId="99000000-0000-0000-0000-000000000001", idempotencyKey="99000000-0000-0000-0000-000000000021", purchaseTransactionId="99000000-0000-0000-0000-000000000011", refundTransactionId="99000000-0000-0000-0000-000000000022", amountMinor=100, currency="PEN", occurredAt=Instant.parse("2026-09-20T12:01:00Z").toEpochMilli())
        val hash = RefundTransactionRequestHasher.hash(command)
        assertEquals("1862cc6e047a19c7813c7a2d15fac94a84390aeb36a041544338437847863e42", hash)
        val refund = TransactionEntity(id=command.refundTransactionId,userId=command.userId,type="INCOME",amountMinor=100,currencyCode="PEN",occurredAt=command.occurredAt)
        val wire = Json.parseToJsonElement(MovementOutboxPayloadFactory.buildRefund(command.purchaseTransactionId,refund,command.idempotencyKey,hash)).jsonObject
        assertEquals(command.purchaseTransactionId, wire.getValue("purchase_transaction_id").jsonPrimitive.content)
        assertEquals(hash, wire.getValue("request_hash").jsonPrimitive.content)
        assertEquals(setOf("id", "amount_minor", "currency_code", "occurred_at"), wire.getValue("refund_transaction").jsonObject.keys)
    }
}
