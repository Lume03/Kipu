package com.kipu.app.feature.capture.domain

import javax.inject.Inject

/** Keeps the privacy boundary testable: [readSignal] must not run before package and gate checks. */
class NotificationCaptureIngress @Inject constructor(
    private val catalog: CaptureSourceCatalog,
    private val gate: CaptureAccessGate,
    private val extract: ExtractCaptureCandidate,
) {
    suspend fun receive(packageName: String, ownerId: String, readSignal: () -> CaptureSignal?) {
        if (!catalog.supportsPackage(packageName)) return
        if (gate.evaluate(ownerId) !is CaptureAccessDecision.Allowed) return
        if (gate.liveDenial(ownerId) != null) return
        val signal = readSignal() ?: return
        if (signal.sourcePackage != packageName) return
        extract(ownerId, signal)
    }
}
