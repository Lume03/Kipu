package com.kipu.app.feature.capture.domain

import org.junit.Assert.*
import org.junit.Test

class CaptureSourceCatalogTest {
    @Test fun `real packages are allow listed and Plin belongs to a bank package`() {
        val catalog = CaptureSourceCatalog(false)
        mapOf(
            "com.bcp.innovacxion.yapeapp" to CaptureProvider.YAPE,
            "com.bcp.bank.bcp" to CaptureProvider.BCP,
            "pe.com.interbank.mobilebanking" to CaptureProvider.INTERBANK,
            "com.bbva.nxt_peru" to CaptureProvider.BBVA,
            "pe.com.scotiabank.blpm.android.client" to CaptureProvider.SCOTIABANK,
        ).forEach { (name, provider) ->
            assertTrue(catalog.supportsPackage(name))
            assertEquals(provider, catalog.provider(name, "", "Compra S/ 10"))
            if (provider != CaptureProvider.YAPE) assertEquals(CaptureProvider.PLIN, catalog.provider(name, "", "Enviaste S/ 10 con Plin"))
        }
        assertFalse(catalog.supportsPackage("com.fake.yape"))
        assertEquals(CaptureProvider.PLIN, catalog.provider("com.bcp.bank.bcp", "Plin", "Enviaste S/ 10"))
        assertNull(catalog.provider("com.fake.yape", "Yape", "Yapeaste S/ 10"))
    }

    @Test fun `debug shell uses explicit title and is unavailable in release`() {
        val debug = CaptureSourceCatalog(true)
        CaptureProvider.entries.forEach {
            assertEquals(it, debug.provider(CaptureSourceCatalog.DEBUG_SHELL, it.name.lowercase(), "Compra S/ 10"))
        }
        assertNull(debug.provider(CaptureSourceCatalog.DEBUG_SHELL, "Otro", "Yapeaste S/ 10"))
        val release = CaptureSourceCatalog(false)
        assertFalse(release.supportsPackage(CaptureSourceCatalog.DEBUG_SHELL))
        assertNull(release.provider(CaptureSourceCatalog.DEBUG_SHELL, "Yape", "Yapeaste S/ 10"))
        assertEquals(1, CaptureSourceCatalog.CATALOG_VERSION)
    }
}
