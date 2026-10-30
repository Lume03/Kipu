package com.kipu.app.feature.statements.presentation

import com.kipu.app.feature.statements.domain.StatementLineFailureReason
import com.kipu.app.feature.statements.domain.StatementLineKind

internal fun StatementLineFailureReason.message(): String = when (this) {
    StatementLineFailureReason.PremiumRequired -> "Necesitas Premium vigente para registrar este estado de cuenta."
    StatementLineFailureReason.CardRequired -> "Elige la tarjeta antes de registrar los movimientos."
    StatementLineFailureReason.CardUnavailable -> "La tarjeta ya no está disponible. Elige una tarjeta de crédito activa."
    StatementLineFailureReason.CurrencyMismatch -> "La moneda del movimiento es distinta a la de la tarjeta."
    StatementLineFailureReason.CategoryRequired -> "Elige una categoría para este movimiento."
    StatementLineFailureReason.SourceAccountRequired -> "Elige la cuenta de origen del pago."
    StatementLineFailureReason.SourceAccountInvalid -> "La cuenta de origen no está disponible o tiene otra moneda."
    StatementLineFailureReason.InsufficientFunds -> "La cuenta de origen no tiene saldo suficiente para este pago."
    StatementLineFailureReason.PaymentExceedsDebt -> "El pago supera la deuda actual de la tarjeta."
    StatementLineFailureReason.RefundPurchaseRequired -> "No encontramos la compra original; desmárcala o regístrala desde la compra"
    StatementLineFailureReason.ClassificationRequired -> "Revisa y elige el tipo de este movimiento."
    StatementLineFailureReason.InvalidInstallments -> "La compra debe tener entre 2 y 36 cuotas."
    StatementLineFailureReason.InvalidLine -> "Revisa la descripción, la fecha y el importe del movimiento."
    StatementLineFailureReason.NotSupported -> "Este tipo de movimiento es informativo y no se puede registrar."
    StatementLineFailureReason.NotFound -> "No encontramos esta importación. Puede haber vencido."
    StatementLineFailureReason.ImportNotPending -> "Esta importación ya no está pendiente. Vuelve a la bandeja."
    StatementLineFailureReason.RegistrationFailed -> "No pudimos registrar el movimiento. Revisa sus datos e inténtalo otra vez."
}

internal fun StatementLineKind.label(installments: Int? = null): String = when (this) {
    StatementLineKind.PURCHASE -> "Compra"
    StatementLineKind.INSTALLMENT_PURCHASE -> "Cuotas ${installments ?: ""}".trim()
    StatementLineKind.REFUND -> "Devolución"
    StatementLineKind.CARD_PAYMENT -> "Pago de tarjeta"
    StatementLineKind.INTEREST -> "Interés"
    StatementLineKind.INSURANCE -> "Seguro"
    StatementLineKind.FEE -> "Comisión"
    StatementLineKind.POINTS_REDEMPTION -> "Canje de puntos"
    StatementLineKind.INSTALLMENT_MONTHLY_CHARGE -> "Cuota del mes"
    StatementLineKind.UNKNOWN -> "Revisar"
}

internal val reclassifiableKinds = setOf(StatementLineKind.PURCHASE, StatementLineKind.REFUND,
    StatementLineKind.INTEREST, StatementLineKind.INSURANCE, StatementLineKind.FEE, StatementLineKind.UNKNOWN)
