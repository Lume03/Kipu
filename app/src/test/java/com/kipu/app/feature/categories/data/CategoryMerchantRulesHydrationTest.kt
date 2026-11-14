package com.kipu.app.feature.categories.data

import android.content.Context
import com.kipu.app.core.database.DatabaseTransactionRunner
import com.kipu.app.feature.categories.data.local.*
import com.kipu.app.feature.categories.data.remote.*
import com.kipu.app.feature.categories.data.sync.CategoryHydration
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CategoryMerchantRulesHydrationTest {
    @Test fun normalPullAppliesNewerRulesAndTombstonesButPreservesPendingAndOtherOwners() = runTest {
        val categories = mockk<CategoryDao>(relaxed=true)
        val rules = mockk<MerchantRulesDao>(relaxed=true)
        val api = mockk<CategoriesApi>()
        val runner = object : DatabaseTransactionRunner { override suspend fun <R> invoke(block: suspend () -> R): R = block() }
        val hydration = CategoryHydration(categories, mockk(relaxed=true), mockk<Context>(), api, rules, runner)
        val now = "2026-11-14T12:00:00Z"
        fun remote(id: String, owner: String="owner", revision: Long=2, deleted: String?=null) = MerchantAliasRemoteDto(id, owner, "pattern", "merchant", revision, now, deleted)
        coEvery { api.fetchMerchantAliasRules("owner") } returns CategoryApiResponse.Success(listOf(
            remote("new"), remote("pending"), remote("stale"), remote("deleted", deleted=now), remote("foreign", owner="other")))
        coEvery { api.fetchMerchantPreferences("owner") } returns CategoryApiResponse.Success(listOf(
            MerchantPreferenceRemoteDto("pref", "owner", "merchant", "category", 2, now, now)))
        coEvery { rules.getAliasRule("owner", any()) } answers {
            when (val id=secondArg<String>()) {
                "pending" -> MerchantAliasRuleEntity(id,"owner","local","merchant",1,updatedAt=1,syncState="PENDING")
                "stale" -> MerchantAliasRuleEntity(id,"owner","newer","merchant",3,updatedAt=1,syncState="SYNCED")
                else -> null
            }
        }
        coEvery { rules.getPreference("owner", "merchant") } returns null
        hydration.hydrateMerchantRules("owner")
        coVerify(exactly=1) { rules.upsertAliasRule(match { it.id=="new" && it.syncState=="SYNCED" && it.remoteRevision==2L }) }
        coVerify(exactly=1) { rules.upsertAliasRule(match { it.id=="deleted" && it.deletedAt!=null }) }
        coVerify(exactly=0) { rules.upsertAliasRule(match { it.id in setOf("pending","stale","foreign") }) }
        coVerify(exactly=1) { rules.upsertPreference(match { it.deletedAt!=null && it.syncState=="SYNCED" }) }
    }
}
