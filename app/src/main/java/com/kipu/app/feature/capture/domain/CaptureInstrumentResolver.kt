package com.kipu.app.feature.capture.domain

data class CaptureInstrumentResolution(
    val kind: CaptureInstrumentKind? = null,
    val accountId: String? = null,
    val cardId: String? = null,
    val ambiguous: Boolean = false,
)

fun interface CaptureInstrumentResolver {
    suspend fun resolve(ownerId: String, provider: CaptureProvider, parsed: ParsedCapture): CaptureInstrumentResolution
}

/** Ordered extension points: scoring/registration precedes review notifications. Empty is a no-op. */
interface CaptureCandidatePostProcessor {
    val order: Int get() = 0
    suspend fun process(candidate: CaptureCandidateRecord)
}
