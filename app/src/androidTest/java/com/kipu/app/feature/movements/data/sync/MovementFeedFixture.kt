package com.kipu.app.feature.movements.data.sync

import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.core.sync.SharedChangeFeed
import com.kipu.app.feature.accounts.data.remote.FinancialInstrumentsApi
import com.kipu.app.feature.movements.data.local.MovementDao
import io.mockk.*

/** Isolates money-worker tests from unrelated module appliers (covered by shared feed tests). */
fun movementFeedFixture(db: KipuDatabase, api: FinancialInstrumentsApi, session: SessionCoordinator,
                        dao: MovementDao = db.movementDao()): SharedChangeFeed {
    val hydration = MovementHydration(dao, api, db.accountDao(), db.cardDao(), db, session)
    return mockk { coEvery { pullChanges(any()) } coAnswers { hydration.pullChanges(firstArg()) } }
}
