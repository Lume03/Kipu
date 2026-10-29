package com.kipu.app.feature.capture.domain

import java.time.LocalDate

enum class CaptureProvider { YAPE, PLIN, BCP, INTERBANK, BBVA, SCOTIABANK }

enum class CaptureCandidateStatus { PENDING, CONFIRMED, AUTO_REGISTERED, DISCARDED }

enum class CaptureConfidenceLevel { HIGH, MEDIUM, LOW }

enum class CaptureDirection { EXPENSE, INCOME }

enum class CaptureInstrumentKind { DEBIT_ACCOUNT, CREDIT_CARD, WALLET }

enum class CreditMode { DIRECT, INSTALLMENTS }

enum class CaptureSourceOutcome { CANDIDATE_CREATED, IGNORED_UNSUPPORTED, IGNORED_UNPARSEABLE, DUPLICATE }

/** Explainable codes behind a confidence level or a pending review; [label] is shown to the user. */
enum class CaptureReason(val label: String) {
    AMOUNT_MISSING("No se pudo leer el monto"),
    CURRENCY_MISSING("No se pudo leer la moneda"),
    DIRECTION_MISSING("No se pudo saber si es gasto o ingreso"),
    INSTRUMENT_UNRESOLVED("No se pudo identificar la cuenta o tarjeta"),
    INSTRUMENT_AMBIGUOUS("Varias cuentas o tarjetas coinciden con los últimos 4 dígitos"),
    DATE_INFERRED("La fecha se tomó de la hora de la notificación"),
    CATEGORY_UNRESOLVED("No hay una categoría segura para este comercio"),
    MERCHANT_UNRESOLVED("No se reconoció el comercio"),
    MERCHANT_AMBIGUOUS("El comercio coincide con varios alias"),
    CREDIT_CARD_REQUIRES_REVIEW("Las compras con tarjeta de crédito siempre se revisan"),
    POSSIBLE_DUPLICATE("Parece un movimiento ya registrado"),
    AUTO_REGISTER_DENIED("La captura automática no estaba autorizada al guardar"),
    ;

    companion object {
        fun fromCodes(codes: List<String>): List<CaptureReason> =
            codes.mapNotNull { code -> entries.firstOrNull { it.name == code } }
    }
}

/** Persisted inbox model. Evidence is minimal, masked and purged on resolution or expiry. */
data class CaptureCandidateRecord(
    val id: String,
    val ownerId: String,
    val sourcePackage: String,
    val provider: CaptureProvider,
    val sourceEventFingerprint: String,
    val status: CaptureCandidateStatus,
    val direction: CaptureDirection?,
    val amountMinor: Long?,
    val currency: String?,
    val occurredAt: Long?,
    val occurredAtInferred: Boolean,
    val merchantSourceText: String?,
    val merchantId: String?,
    val categoryId: String?,
    val cardLast4: String?,
    val instrumentKind: CaptureInstrumentKind?,
    val accountId: String?,
    val cardId: String?,
    val instrumentAmbiguous: Boolean,
    val confidence: CaptureConfidenceLevel,
    val confidenceReasons: List<CaptureReason>,
    val creditMode: CreditMode?,
    val installmentCount: Int?,
    val resolvedTransactionId: String?,
    val resolutionKey: String?,
    val evidenceSnippet: String?,
    val evidenceExpiresAt: Long?,
    val userCorrectedFields: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
    val resolvedAt: Long?,
    val expectedMatchState: ExpectedMatchState? = null,
    val followupsPending: Int = 0,
)

data class CaptureSourceEvent(
    val ownerId: String,
    val eventFingerprint: String,
    val sourcePackage: String,
    val receivedAt: Long,
    val outcome: CaptureSourceOutcome,
)

enum class ExpectedMatchState { PENDING_CHOICE, LINKED, NONE }

enum class ExpectedPaymentStatus { EXPECTED, ATTENDED, SKIPPED }

/** One expected occurrence of an active recurrence (HU-34). Never touches ledger, balances or budgets. */
data class ExpectedPayment(
    val id: String,
    val ownerId: String,
    val recurrenceId: String,
    val dueDate: LocalDate,
    val expectedAmountMinor: Long?,
    val currency: String,
    val status: ExpectedPaymentStatus,
    val matchedTransactionId: String?,
    val matchedCandidateId: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
