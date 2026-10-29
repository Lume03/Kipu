package com.kipu.app.feature.capture.domain

/** Versioned allow-list. Debug shell routing is resolved only AFTER the access gate. */
class CaptureSourceCatalog(private val debug: Boolean) {
    fun supportsPackage(packageName: String): Boolean = packageName in packages ||
        (debug && packageName == DEBUG_SHELL)

    fun provider(packageName: String, title: String, text: String): CaptureProvider? {
        if (!supportsPackage(packageName)) return null
        if (packageName == DEBUG_SHELL) {
            return CaptureProvider.entries.singleOrNull { it.name.equals(title.trim(), ignoreCase = true) }
        }
        val base = packages[packageName] ?: return null
        return if (base != CaptureProvider.YAPE && Regex("(?i)\\bplin\\b").containsMatchIn("$title $text")) {
            CaptureProvider.PLIN
        } else base
    }

    companion object {
        const val CATALOG_VERSION = 1
        const val DEBUG_SHELL = "com.android.shell"
        private val packages = mapOf(
            "com.bcp.innovacxion.yapeapp" to CaptureProvider.YAPE,
            "com.bcp.bank.bcp" to CaptureProvider.BCP,
            "pe.com.interbank.mobilebanking" to CaptureProvider.INTERBANK,
            "com.bbva.nxt_peru" to CaptureProvider.BBVA,
            "pe.com.scotiabank.blpm.android.client" to CaptureProvider.SCOTIABANK,
        )
    }
}

/** Sensitive payload: ephemeral only; deliberately has no generated toString. */
class CaptureSignal(
    val provider: CaptureProvider,
    val sourcePackage: String,
    val notificationKey: String,
    val postTimeMillis: Long,
    val title: String,
    val text: String,
)
