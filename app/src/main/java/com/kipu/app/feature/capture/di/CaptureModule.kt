package com.kipu.app.feature.capture.di

import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.capture.data.VerifiedCaptureAccessGate
import com.kipu.app.feature.capture.data.local.CaptureCandidateDao
import com.kipu.app.feature.capture.data.local.CaptureSourceEventDao
import com.kipu.app.feature.capture.data.local.RoomCaptureCandidateStore
import com.kipu.app.feature.capture.data.local.RoomCaptureSourceEventStore
import com.kipu.app.feature.capture.data.local.RoomExpectedPaymentStore
import com.kipu.app.feature.capture.domain.CaptureAccessGate
import com.kipu.app.feature.capture.domain.CaptureCandidateStore
import com.kipu.app.feature.capture.domain.CaptureSourceEventStore
import com.kipu.app.feature.capture.domain.ExpectedPaymentStore
import com.kipu.app.feature.recurrences.data.local.ExpectedPaymentDao
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import com.kipu.app.BuildConfig
import com.kipu.app.feature.capture.data.RoomCaptureInstrumentResolver
import com.kipu.app.feature.capture.domain.CaptureCandidatePostProcessor
import com.kipu.app.feature.capture.domain.CaptureInstrumentResolver
import com.kipu.app.feature.capture.domain.CaptureSourceCatalog
import com.kipu.app.feature.capture.data.RoomCaptureWalletChannelResolver
import com.kipu.app.feature.capture.data.work.EvidencePurgeScheduler
import com.kipu.app.feature.capture.data.work.WorkManagerEvidencePurgeScheduler
import com.kipu.app.feature.capture.domain.CaptureCandidateResolvedListener
import com.kipu.app.feature.capture.domain.CaptureWalletChannelResolver
import com.kipu.app.feature.capture.domain.ScoreAndAutoRegisterPostProcessor
import com.kipu.app.feature.capture.data.CaptureReviewNotifier
import com.kipu.app.feature.capture.data.CaptureReviewSystemNotifier
import com.kipu.app.feature.capture.data.AndroidCaptureReviewSystemNotifier
import com.kipu.app.feature.capture.domain.LearnFromCaptureCorrection
import com.kipu.app.feature.capture.domain.CaptureCandidateDiscardedListener
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CaptureBindings {
    @Binds abstract fun receipts(value: com.kipu.app.feature.capture.data.RoomCaptureRegistrationReceipts): com.kipu.app.feature.capture.domain.CaptureRegistrationReceipts
    @Binds abstract fun instrumentResolver(value: RoomCaptureInstrumentResolver): CaptureInstrumentResolver
    @Multibinds abstract fun postProcessors(): Set<CaptureCandidatePostProcessor>
    @Binds @IntoSet abstract fun scoreAndAutoRegister(value: ScoreAndAutoRegisterPostProcessor): CaptureCandidatePostProcessor
    @Binds @IntoSet abstract fun reviewPostProcessor(value: CaptureReviewNotifier): CaptureCandidatePostProcessor
    @Binds @IntoSet abstract fun reviewDiscarded(value: CaptureReviewNotifier): CaptureCandidateDiscardedListener
    @Binds @IntoSet abstract fun reviewResolved(value: CaptureReviewNotifier): CaptureCandidateResolvedListener
    @Binds @IntoSet abstract fun learnResolved(value: LearnFromCaptureCorrection): CaptureCandidateResolvedListener
    @Binds abstract fun reviewSystem(value: AndroidCaptureReviewSystemNotifier): CaptureReviewSystemNotifier
    @Multibinds abstract fun resolvedListeners(): Set<CaptureCandidateResolvedListener>
    @Binds abstract fun walletChannelResolver(value: RoomCaptureWalletChannelResolver): CaptureWalletChannelResolver
    @Binds abstract fun evidencePurgeScheduler(value: WorkManagerEvidencePurgeScheduler): EvidencePurgeScheduler
    @Binds @Singleton abstract fun candidateStore(value: RoomCaptureCandidateStore): CaptureCandidateStore
    @Binds @Singleton abstract fun sourceEventStore(value: RoomCaptureSourceEventStore): CaptureSourceEventStore
    @Binds @Singleton abstract fun expectedPaymentStore(value: RoomExpectedPaymentStore): ExpectedPaymentStore
    @Binds @Singleton abstract fun accessGate(value: VerifiedCaptureAccessGate): CaptureAccessGate
}

@Module
@InstallIn(SingletonComponent::class)
object CaptureProviders {
    @Provides @Singleton fun sourceCatalog(): CaptureSourceCatalog = CaptureSourceCatalog(BuildConfig.DEBUG)
    @Provides @Singleton fun candidateDao(database: KipuDatabase): CaptureCandidateDao = database.captureCandidateDao()
    @Provides @Singleton fun sourceEventDao(database: KipuDatabase): CaptureSourceEventDao = database.captureSourceEventDao()
    @Provides @Singleton fun expectedPaymentDao(database: KipuDatabase): ExpectedPaymentDao = database.expectedPaymentDao()
}
