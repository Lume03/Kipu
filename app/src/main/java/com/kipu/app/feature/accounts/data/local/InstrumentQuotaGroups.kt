package com.kipu.app.feature.accounts.data.local

/** Channels share their bank account's quota slot; credit and standalone wallets own one. */
internal fun instrumentQuotaGroups(accounts: List<AccountEntity>, cards: List<CardEntity>): Map<String, String> {
    val activeAccounts = accounts.filter { !it.isArchived && it.type !in setOf("CASH", "GOALS_VIRTUAL", "CREDIT_LIABILITY") }
    val bankIds = activeAccounts.filter { it.type in setOf("BANK", "SAVINGS") }.map { it.id }.toSet()
    return buildMap {
        activeAccounts.forEach { account ->
            put(account.id, account.linkedAccountId?.takeIf { it in bankIds } ?: account.id)
        }
        cards.filterNot { it.isArchived }.forEach { card ->
            put(card.id, if (card.type == "DEBIT") card.accountId?.takeIf { it in bankIds } ?: card.id else card.id)
        }
    }
}
