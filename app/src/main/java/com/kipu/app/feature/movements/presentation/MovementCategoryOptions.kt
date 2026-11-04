package com.kipu.app.feature.movements.presentation

import com.kipu.app.feature.categories.domain.model.CategoryType
import com.kipu.app.feature.movements.domain.model.MovementType

internal fun List<CategoryOption>.forMovementType(type: MovementType): List<CategoryOption> = filter {
    when (type) {
        MovementType.EXPENSE -> it.categoryType != CategoryType.INCOME
        MovementType.INCOME -> it.categoryType != CategoryType.EXPENSE
        MovementType.TRANSFER -> false
    }
}
