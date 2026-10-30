package com.kipu.app.feature.statements.domain

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatementParserRegistry @Inject constructor(private val parsers: Set<@JvmSuppressWildcards StatementParser>) {
    fun parse(pages: List<StatementPage>): StatementParseResult =
        parsers.firstOrNull { it.canParse(pages) }?.parse(pages) ?: StatementParseResult.Unsupported
}
