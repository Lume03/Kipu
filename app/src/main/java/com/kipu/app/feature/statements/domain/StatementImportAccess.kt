package com.kipu.app.feature.statements.domain

fun interface StatementImportAccess {
    suspend fun canImport(ownerId: String): Boolean
}
