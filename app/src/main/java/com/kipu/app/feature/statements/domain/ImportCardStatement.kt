package com.kipu.app.feature.statements.domain

import android.net.Uri
import java.time.Clock
import javax.inject.Inject

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
    suspend operator fun invoke(ownerId: String, uri: Uri, userConsented: Boolean, password: CharArray? = null): StatementImportResult {
        try {
            if (!userConsented) return StatementImportResult.ConsentRequired
            if (!access.canImport(ownerId)) return StatementImportResult.PremiumRequired
            store.purgeExpired(ownerId, clock.millis())
            val pages = when (val read = reader.read(uri, password)) {
                StatementReadResult.PasswordRequired -> return StatementImportResult.PasswordRequired
                StatementReadResult.WrongPassword -> return StatementImportResult.WrongPassword
                StatementReadResult.NoExtractableText -> return StatementImportResult.NoExtractableText
                StatementReadResult.Unreadable -> return StatementImportResult.Invalid
                is StatementReadResult.Opened -> read.pages
            }
            val parsed = when (val result = parsers.parse(pages)) {
                StatementParseResult.Unsupported -> return StatementImportResult.Unsupported
                StatementParseResult.InvalidStatement -> return StatementImportResult.Invalid
                is StatementParseResult.Parsed -> result.statement
            }
            store.findByFingerprint(ownerId, parsed.fingerprint)?.let {
                return StatementImportResult.AlreadyImported(it.id, it.status == StatementImportStatus.COMPLETED)
            }
            val cards = matching.activeCreditCards(ownerId).filter { it.lastFour == parsed.cardLastFour }
            val card = cards.singleOrNull() ?: cards.filter {
                parsed.issuer.equals("BCP", ignoreCase = true) && it.issuer.equals("BCP", ignoreCase = true)
            }.singleOrNull()
            val candidates = parsed.lines.filter { it.kind != StatementLineKind.INSTALLMENT_MONTHLY_CHARGE }.map { line ->
                val duplicate = card != null && matching.hasDuplicate(ownerId, card.id, line.amountMinor, line.currency, line.occurredDate)
                StatementImportCandidate(line, line.defaultSelected && !duplicate &&
                    line.kind != StatementLineKind.CARD_PAYMENT && line.kind != StatementLineKind.POINTS_REDEMPTION &&
                    (card == null || card.currency == line.currency), duplicate)
            }
            val saved = store.create(ownerId, parsed, card?.id, candidates)
            return if (saved.created) StatementImportResult.Imported(saved.statement.id)
            else StatementImportResult.AlreadyImported(saved.statement.id, saved.statement.status == StatementImportStatus.COMPLETED)
        } finally {
            // Early consent/access denials and cancellation must also erase the caller-owned secret.
            password?.fill('\u0000')
        }
    }
}
