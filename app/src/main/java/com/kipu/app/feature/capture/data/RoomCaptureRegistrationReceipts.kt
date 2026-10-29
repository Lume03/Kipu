package com.kipu.app.feature.capture.data

import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.capture.domain.CaptureRegistrationReceipts
import com.kipu.app.feature.movements.data.local.toDomain
import javax.inject.Inject

class RoomCaptureRegistrationReceipts @Inject constructor(private val database: KipuDatabase) : CaptureRegistrationReceipts {
    override suspend fun find(ownerId: String, resolutionKey: String) =
        database.movementDao().getReceipt(ownerId, resolutionKey)?.let { receipt ->
            check(receipt.status == "APPLIED") { "Capture receipt requires reconciliation" }
            val id = checkNotNull(receipt.transactionId) { "Capture receipt has no transaction" }
            checkNotNull(database.movementDao().getTransactionById(ownerId, id)).toDomain()
        }
}
