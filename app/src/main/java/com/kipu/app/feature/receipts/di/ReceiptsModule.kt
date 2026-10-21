package com.kipu.app.feature.receipts.di

import com.kipu.app.feature.receipts.data.AndroidReceiptImageRecognizer
import com.kipu.app.feature.receipts.data.LocalReceiptScanAccess
import com.kipu.app.feature.receipts.data.local.ReceiptCandidateDao
import com.kipu.app.feature.receipts.data.local.RoomReceiptCandidateStore
import com.kipu.app.feature.receipts.domain.ReceiptCandidateStore
import com.kipu.app.feature.receipts.domain.ReceiptImageRecognizer
import com.kipu.app.feature.receipts.domain.ReceiptScanAccess
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.Provides
import com.kipu.app.core.database.KipuDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReceiptsBindings {
    @Binds @Singleton abstract fun candidateStore(value: RoomReceiptCandidateStore): ReceiptCandidateStore
    @Binds @Singleton abstract fun imageRecognizer(value: AndroidReceiptImageRecognizer): ReceiptImageRecognizer
    @Binds @Singleton abstract fun scanAccess(value: LocalReceiptScanAccess): ReceiptScanAccess
}

@Module
@InstallIn(SingletonComponent::class)
object ReceiptsProviders {
    @Provides @Singleton fun candidateDao(database: KipuDatabase): ReceiptCandidateDao = database.receiptCandidateDao()
}
