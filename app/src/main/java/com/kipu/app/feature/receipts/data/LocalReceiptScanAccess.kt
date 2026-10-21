package com.kipu.app.feature.receipts.data

import com.kipu.app.feature.receipts.domain.ReceiptScanAccess
import com.kipu.app.feature.receipts.domain.ReceiptScanAccessPolicy
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalReceiptScanAccess @Inject constructor(
    private val policy: ReceiptScanAccessPolicy,
) : ReceiptScanAccess {
    override suspend fun canScan(ownerId: String): Boolean {
        if (runCatching { UUID.fromString(ownerId) }.isFailure) return false
        return policy.allows()
    }
}
