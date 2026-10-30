package com.kipu.app.feature.statements.di

import com.kipu.app.feature.statements.data.PdfBoxStatementDocumentReader
import com.kipu.app.feature.statements.domain.BcpVisaStatementParser
import com.kipu.app.feature.statements.domain.StatementDocumentReader
import com.kipu.app.feature.statements.domain.StatementParser
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton
import dagger.Provides
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.statements.data.VerifiedStatementImportAccess
import com.kipu.app.feature.statements.data.local.RoomStatementImportStore
import com.kipu.app.feature.statements.data.local.RoomStatementImportMatching
import com.kipu.app.feature.statements.domain.StatementImportAccess
import com.kipu.app.feature.statements.domain.StatementImportStore
import com.kipu.app.feature.statements.domain.StatementImportMatching

@Module
@InstallIn(SingletonComponent::class)
abstract class StatementsModule {
    @Binds @Singleton abstract fun importAccess(value: VerifiedStatementImportAccess): StatementImportAccess
    @Binds @Singleton abstract fun importStore(value: RoomStatementImportStore): StatementImportStore
    @Binds @Singleton abstract fun importMatching(value: RoomStatementImportMatching): StatementImportMatching
    @Binds @Singleton abstract fun documentReader(value: PdfBoxStatementDocumentReader): StatementDocumentReader
    @Binds @IntoSet abstract fun bcpVisaParser(value: BcpVisaStatementParser): StatementParser

    companion object {
        @Provides fun importDao(database: KipuDatabase) = database.statementImportDao()
        @Provides fun matchingDao(database: KipuDatabase) = database.statementImportMatchingDao()
    }
}
