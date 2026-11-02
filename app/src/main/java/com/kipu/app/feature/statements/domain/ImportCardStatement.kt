package com.kipu.app.feature.statements.domain

import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface StatementImportResult {
    data object ConsentRequired : StatementImportResult
    data object PremiumRequired : StatementImportResult
    data object PasswordRequired : StatementImportResult
    data object WrongPassword : StatementImportResult
    data object NoExtractableText : StatementImportResult
    data object Unsupported : StatementImportResult
    data object Invalid : StatementImportResult
    data class AlreadyImported(val importId: String, val completed: Boolean) : StatementImportResult
    data class Imported(val importId: String) : StatementImportResult
}

class ImportCardStatement @Inject constructor(
    private val access: StatementImportAccess,
    private val reader: StatementDocumentReader,
    private val parsers: StatementParserRegistry,
    private val store: StatementImportStore,
    private val matching: StatementImportMatching,
    private val clock: Clock,
) {
    suspend operator fun invoke(ownerId: String, document: DocumentRef, userConsented: Boolean, password: CharArray? = null): StatementImportResult {
        try {
            if (!userConsented) return StatementImportResult.ConsentRequired
            if (!access.canImport(ownerId)) return StatementImportResult.PremiumRequired
            store.purgeExpired(ownerId, clock.millis())
            val pages = when (val read = reader.read(document, password)) {
                StatementReadResult.PasswordRequired -> return StatementImportResult.PasswordRequired
                StatementReadResult.WrongPassword -> return StatementImportResult.WrongPassword
                StatementReadResult.NoExtractableText -> return StatementImportResult.NoExtractableText
                StatementReadResult.Unreadable -> return StatementImportResult.Invalid
                is StatementReadResult.Opened -> read.pages
            }
            val parsed = when (val result = withContext(Dispatchers.Default) { parsers.parse(pages) }) {
                StatementParseResult.Unsupported -> return StatementImportResult.Unsupported
                StatementParseResult.InvalidStatement -> return StatementImportResult.Invalid
                is StatementParseResult.Parsed -> result.statement
            }
            val cards = matching.activeCreditCards(ownerId).filter { it.lastFour == parsed.cardLastFour }
            val card = cards.singleOrNull() ?: cards.filter {
                parsed.issuer.equals("BCP", ignoreCase = true) && it.issuer.equals("BCP", ignoreCase = true)
            }.singleOrNull()
            val keyed = parsed.copy(fingerprint = card?.let { statementCardFingerprint(parsed.fingerprint, it.id) } ?: parsed.fingerprint)
            store.findByFingerprint(ownerId, keyed.fingerprint)?.let {
                return StatementImportResult.AlreadyImported(it.id, it.status == StatementImportStatus.COMPLETED)
            }
            val candidates = parsed.lines.filter { it.kind != StatementLineKind.INSTALLMENT_MONTHLY_CHARGE }.map { line ->
                val duplicate = card != null && matching.hasDuplicate(ownerId, card.id, line.amountMinor, line.currency, line.occurredDate)
                StatementImportCandidate(line, line.defaultSelected && !duplicate &&
                    line.kind != StatementLineKind.CARD_PAYMENT && line.kind != StatementLineKind.POINTS_REDEMPTION &&
                    (card == null || card.currency == line.currency), duplicate)
            }
            val saved = store.create(ownerId, keyed, card?.id, candidates)
            return if (saved.created) StatementImportResult.Imported(saved.statement.id)
            else StatementImportResult.AlreadyImported(saved.statement.id, saved.statement.status == StatementImportStatus.COMPLETED)
        } finally {
            // Early consent/access denials and cancellation must also erase the caller-owned secret.
            password?.fill('\u0000')
        }
    }
}

internal fun statementCardFingerprint(fingerprint: String, cardId: String): String =
    "${fingerprint.substringBefore(':')}:$cardId"
