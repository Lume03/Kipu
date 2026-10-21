package com.kipu.app.navigation

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/** Shared URIs are untrusted input, not proof that a payment originated in Yape. */
object ReceiptShareIntent {
    fun imageUri(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) return null
        return runCatching {
            val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?: intent.clipData?.takeIf { it.itemCount == 1 }?.getItemAt(0)?.uri
            uri?.takeIf { it.scheme == "content" && !it.authority.isNullOrBlank() }
        }.getOrNull()
    }
}
