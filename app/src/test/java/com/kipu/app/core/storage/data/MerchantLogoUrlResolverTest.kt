package com.kipu.app.core.storage.data

import com.kipu.app.feature.categories.data.local.MerchantCatalogEntity
import com.kipu.app.feature.categories.data.sync.MerchantLogoPrefetchPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MerchantLogoUrlResolverTest {
    private val base = "https://abc.supabase.co/"

    private fun merchant(
        id: String,
        path: String? = null,
        darkPath: String? = null,
        active: Boolean = true,
    ) = MerchantCatalogEntity(
        id = id,
        name = id,
        normalizedName = id,
        isActive = active,
        lastSyncedAt = 1L,
        logoPath = path,
        logoDarkPath = darkPath,
    )

    @Test
    fun resolvesPublicBucketUrlFromLogoPath() {
        assertEquals(
            "https://abc.supabase.co/storage/v1/object/public/merchant-logos/logo-dev/tambo.pe/etag-1.webp",
            MerchantLogoUrlResolver.resolve(base, "logo-dev/tambo.pe/etag-1.webp"),
        )
    }

    @Test
    fun returnsNullWithoutPathOrBaseUrl() {
        assertNull(MerchantLogoUrlResolver.resolve(base, null))
        assertNull(MerchantLogoUrlResolver.resolve(base, " "))
        assertNull(MerchantLogoUrlResolver.resolve("", "logo-dev/a.webp"))
    }

    @Test
    fun prefetchPlanListsLightAndDarkUrlsOnceForActiveMerchants() {
        val urls = MerchantLogoPrefetchPlan.urlsFor(
            listOf(
                merchant("a", "logo-dev/google.com/e1.webp", "logo-dev/google.com/e1-dark.webp"),
                merchant("b", "logo-dev/google.com/e1.webp", "logo-dev/google.com/e1-dark.webp"),
                merchant("c", path = "logo-dev/tambo.pe/e2.webp"),
                merchant("d", "logo-dev/off.com/e3.webp", active = false),
                merchant("e"),
            ),
            base,
        )
        assertEquals(3, urls.size)
        assertTrue(urls.all { it.startsWith("https://abc.supabase.co/storage/v1/object/public/merchant-logos/logo-dev/") })
    }

    @Test
    fun prefetchIsOnlyNeededWhenSomeMerchantHasRemoteLogo() {
        assertFalse(MerchantLogoPrefetchPlan.hasRemoteLogos(listOf(merchant("a"))))
        assertFalse(MerchantLogoPrefetchPlan.hasRemoteLogos(listOf(merchant("a", "p", active = false))))
        assertTrue(MerchantLogoPrefetchPlan.hasRemoteLogos(listOf(merchant("a"), merchant("b", darkPath = "p"))))
    }
}
