package com.kipu.app.feature.movements.domain.model

enum class MovementType {
    EXPENSE,
    INCOME,
    TRANSFER;

    companion object {
        fun fromString(value: String): MovementType = when (value.uppercase()) {
            "EXPENSE" -> EXPENSE
            "INCOME" -> INCOME
            "TRANSFER" -> TRANSFER
            else -> throw IllegalArgumentException("Unknown movement type: $value")
        }
    }
}

enum class LedgerRole {
    SOURCE,
    DESTINATION,
    LIABILITY,
}

/** The server posts ordinary entries as PRIMARY/REVERSAL; locally the role follows the side of the movement. */
fun localLedgerRole(role: String, type: String, accountId: String, sourceAccountId: String?): LedgerRole =
    LedgerRole.entries.firstOrNull { it.name == role } ?: when {
        type == MovementType.INCOME.name -> LedgerRole.DESTINATION
        type == MovementType.TRANSFER.name && accountId != sourceAccountId -> LedgerRole.DESTINATION
        else -> LedgerRole.SOURCE
    }

enum class MovementSyncStatus {
    PENDING,
    IN_FLIGHT,
    SYNCED,
    MIGRATED_LOCAL,
    CONFLICT,
    FAILED_PERMANENT;

    companion object {
        fun fromString(value: String): MovementSyncStatus = when (value.uppercase()) {
            "PENDING" -> PENDING
            "IN_FLIGHT" -> IN_FLIGHT
            "SYNCED" -> SYNCED
            "MIGRATED_LOCAL" -> MIGRATED_LOCAL
            "CONFLICT" -> CONFLICT
            "FAILED_PERMANENT" -> FAILED_PERMANENT
            else -> PENDING
        }
    }
}

enum class TransactionStatus {
    ACTIVE,
    CONFIRMED,
    REVISED,
    VOIDED,
    FAILED;

    companion object {
        fun fromString(value: String): TransactionStatus = when (value.uppercase()) {
            "ACTIVE" -> ACTIVE
            "CONFIRMED" -> CONFIRMED
            "REVISED" -> REVISED
            "VOIDED" -> VOIDED
            "FAILED" -> FAILED
            else -> ACTIVE
        }
    }
}

data class Transaction(
    val id: String,
    val userId: String,
    val type: MovementType,
    val amountMinor: Long,
    val currency: String,
    val sourceAccountId: String? = null,
    val destinationAccountId: String? = null,
    val categoryId: String? = null,
    val merchantId: String? = null,
    val merchantProvisionalText: String? = null,
    val legacyKind: String? = null,
    val occurredAt: Long,
    val note: String? = null,
    val status: TransactionStatus = TransactionStatus.ACTIVE,
    val syncStatus: MovementSyncStatus = MovementSyncStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val cardId: String? = null,
    val operationKind: String? = null,
    val installmentCount: Int? = null,
    val paymentChannel: String? = null,
) {
    init {
        require(amountMinor > 0) { "Amount must be strictly positive: $amountMinor" }
        require(currency.isNotBlank()) { "Currency must not be blank" }
        if (installmentCount != null) {
            require(installmentCount in 1..36) { "Installment count must be between 1 and 36" }
        }
        when (type) {
            MovementType.EXPENSE -> {
                if (operationKind.equals("CARD_PURCHASE", ignoreCase = true)) {
                    require(sourceAccountId.isNullOrBlank()) { "Card purchase cannot debit a liquid account" }
                    require(!cardId.isNullOrBlank()) { "Card purchase requires a credit card" }
                    require(destinationAccountId.isNullOrBlank()) { "Card purchase cannot have a destination account" }
                    require(!categoryId.isNullOrBlank()) { "Card purchase requires a category" }
                    require(installmentCount in 1..36) { "Card purchase requires a valid installment count" }
                } else {
                    require(!sourceAccountId.isNullOrBlank()) { "Expense requires a source account" }
                    require(cardId.isNullOrBlank()) { "Standard expense cannot carry a card source" }
                    val isDebtPrincipal = operationKind.equals("DEBT_DISBURSEMENT", ignoreCase = true) ||
                        operationKind.equals("DEBT_PAYMENT", ignoreCase = true)
                    require(legacyKind != null || isDebtPrincipal || !categoryId.isNullOrBlank()) { "Expense requires a category" }
                }
            }
            MovementType.INCOME -> {
                if (operationKind.equals("REFUND", ignoreCase = true)) {
                    require(
                        (!sourceAccountId.isNullOrBlank() && cardId.isNullOrBlank()) ||
                            (sourceAccountId.isNullOrBlank() && !cardId.isNullOrBlank()),
                    ) { "Refund income requires exactly one original account or credit card" }
                    require(destinationAccountId.isNullOrBlank()) { "Refund income cannot have a second destination" }
                } else {
                    require(!sourceAccountId.isNullOrBlank()) { "Income requires a destination/source account" }
                    require(cardId.isNullOrBlank()) { "Income cannot use a credit card" }
                }
            }
            MovementType.TRANSFER -> {
                require(!sourceAccountId.isNullOrBlank()) { "Transfer requires a source account" }
                if (operationKind.equals("CARD_PAYMENT", ignoreCase = true)) {
                    require(!cardId.isNullOrBlank()) { "Card payment requires a credit card destination" }
                    require(destinationAccountId.isNullOrBlank()) { "Card payment cannot have a liquid destination account" }
                } else {
                    require(!destinationAccountId.isNullOrBlank()) { "Transfer requires a destination account" }
                    require(cardId.isNullOrBlank()) { "Standard transfer cannot carry a card destination" }
                    require(sourceAccountId != destinationAccountId) { "Source and destination accounts must be different" }
                }
            }
        }
    }
}

data class RefundTransactionCommand(
    val idempotencyKey: String,
    val userId: String,
    val purchaseTransactionId: String,
    val refundTransactionId: String,
    val amountMinor: Long,
    val currency: String,
    val occurredAt: Long,
)

enum class RefundTransactionError {
    INVALID_COMMAND,
    INVALID_AMOUNT,
    PURCHASE_NOT_FOUND,
    PURCHASE_NOT_ELIGIBLE,
    OWNER_MISMATCH,
    CURRENCY_MISMATCH,
    REFUND_EXCEEDS_REMAINDER,
    IDEMPOTENCY_CONFLICT,
    RECEIPT_INCONSISTENT,
    REFUND_NOT_REJECTED,
}

sealed interface RefundTransactionResult {
    data class Success(
        val transaction: Transaction,
        val isDuplicate: Boolean = false,
    ) : RefundTransactionResult

    data class Rejected(
        val error: RefundTransactionError,
        val remainingMinor: Long? = null,
    ) : RefundTransactionResult

    data class Conflict(val error: RefundTransactionError) : RefundTransactionResult
}

data class LedgerEntry(
    val id: String,
    val transactionId: String,
    val accountId: String,
    val role: LedgerRole,
    val signedAmountMinor: Long,
    val currency: String,
    val createdAt: Long = System.currentTimeMillis(),
) {
    init {
        require(signedAmountMinor != 0L) { "Signed amount cannot be zero" }
        require(accountId.isNotBlank()) { "Account ID must not be blank" }
        require(currency.isNotBlank()) { "Currency must not be blank" }
    }
}

data class TransactionItem(
    val transaction: Transaction,
    val sourceAccountAlias: String? = null,
    val destinationAccountAlias: String? = null,
    val categoryName: String? = null,
    val categoryIcon: String? = null,
    val merchantName: String? = null,
    val merchantLogoKey: String? = null,
    val merchantLogoUrl: String? = null,
    val merchantBrandColor: String? = null,
    val cardAlias: String? = null,
    val cardLastFourDigits: String? = null,
)
