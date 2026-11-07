package com.kipu.app.feature.movements.presentation

import com.kipu.app.feature.movements.domain.model.MovementType

internal fun duplicateWarningType(proposed: MovementType?, existing: MovementType?): MovementType =
    proposed ?: existing ?: MovementType.EXPENSE

internal fun duplicateAmountSign(type: MovementType): String = when (type) {
    MovementType.EXPENSE -> "−"
    MovementType.INCOME -> "+"
    MovementType.TRANSFER -> ""
}
