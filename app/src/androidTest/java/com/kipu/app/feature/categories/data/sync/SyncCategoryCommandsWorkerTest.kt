package com.kipu.app.feature.categories.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.categories.data.local.CategoryDao
import com.kipu.app.feature.categories.data.local.CategorySyncOutboxEntity
import com.kipu.app.feature.categories.data.local.MerchantCategoryPreferenceEntity
import com.kipu.app.feature.categories.data.local.MerchantCatalogDao
import com.kipu.app.feature.categories.data.local.MerchantRulesDao
import com.kipu.app.feature.categories.data.remote.CategoriesApi
import com.kipu.app.feature.categories.data.remote.CategoryApiResponse
import com.kipu.app.feature.categories.data.remote.CategoryCommandResponseDto
import com.kipu.app.feature.categories.data.remote.CategoryCatalogItemDto
import com.kipu.app.feature.categories.data.remote.MerchantCatalogItemDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncCategoryCommandsWorkerTest {
    private val transactionRunner = object : com.kipu.app.core.database.DatabaseTransactionRunner {
        override suspend fun <R> invoke(block: suspend () -> R): R = block()
    }


    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val categoryDao: CategoryDao = mockk(relaxed = true)
    private val merchantDao: MerchantCatalogDao = mockk(relaxed = true)
    private val merchantRulesDao: MerchantRulesDao = mockk(relaxed = true)
    private val api: CategoriesApi = mockk(relaxed = true)
    private val sessionCoordinator: SessionCoordinator = mockk(relaxed = true)
    private val workerParams: WorkerParameters = mockk(relaxed = true)

    private val testUserId = UUID.randomUUID().toString()

    @Before
    fun setup() {
        io.mockk.clearMocks(categoryDao, merchantDao, merchantRulesDao, api, sessionCoordinator, workerParams)
        io.mockk.every { workerParams.inputData } returns workDataOf(
            SyncCategoryCommandsWorker.KEY_USER_ID to testUserId
        )
        io.mockk.every { sessionCoordinator.localAccess } returns MutableStateFlow(
            LocalAccess.Available(testUserId, RemoteSession.Absent),
        )
        io.mockk.every { sessionCoordinator.remoteSession } returns MutableStateFlow<RemoteSession>(
            RemoteSession.Valid(testUserId, java.time.Instant.now().plusSeconds(3600)),
        )
        coEvery { api.fetchMerchantAliasRules(testUserId) } returns CategoryApiResponse.Success(emptyList())
        coEvery { api.fetchMerchantPreferences(testUserId) } returns CategoryApiResponse.Success(emptyList())
        coEvery { api.fetchMerchantCatalog(any()) } returns CategoryApiResponse.Success(emptyList())
        coEvery { categoryDao.claim(testUserId, any()) } returns null
        coEvery { categoryDao.getUnresolvedCommands(testUserId) } returns emptyList()
        io.mockk.every { sessionCoordinator.currentOwner } returns com.kipu.app.core.session.LocalOwner(testUserId)
    }

    @Test
    fun workerProcessesPendingCommandAndHydratesCatalog() = runTest {
        val opId = UUID.randomUUID().toString()
        val pendingCmd = CategorySyncOutboxEntity(
            operationId = opId,
            userId = testUserId,
            commandType = "CREATE_CATEGORY",
            aggregateType = "CATEGORY",
            aggregateId = UUID.randomUUID().toString(),
            payloadJson = """{"operation_id":"$opId","category_id":"cat-1","name":"Alimentacion","icon":"food","color":"#FF0000","payload_hash":"hash1"}""",
            payloadHash = "hash1",
            state = "PENDING",
            createdAt = 1000L,
            updatedAt = 1000L
        )

        prepareCommands(listOf(pendingCmd))
        coEvery { api.createCategory(any(), any()) } returns CategoryApiResponse.Success(
            CategoryCommandResponseDto(success = true)
        )
        coEvery { merchantDao.getLatestVersion() } returns 0L
        coEvery { api.fetchMerchantCatalog(any()) } returns CategoryApiResponse.Success(
            listOf(
                MerchantCatalogItemDto("m1", "Tambo", "tambo", true, 1L)
            )
        )
        coEvery { api.fetchCategories() } returns CategoryApiResponse.Success(
            listOf(
                CategoryCatalogItemDto(
                    id = "00000000-0000-0000-0000-000000000001", name = "Alimentación", origin = "SYSTEM",
                    isActive = true, remoteRevision = 1L, createdAt = "2026-09-23T00:00:00.000000+00:00", updatedAt = "2026-09-23T00:00:00.000000+00:00",
                ),
                CategoryCatalogItemDto(
                    id = "00000000-0000-0000-0000-000000000002", name = "Transporte", origin = "SYSTEM",
                    isActive = true, remoteRevision = 1L, createdAt = "2026-09-23T00:00:00Z", updatedAt = "2026-09-23T00:00:00Z",
                ),
                CategoryCatalogItemDto(
                    id = "00000000-0000-0000-0000-000000000003", name = "Servicios", origin = "SYSTEM",
                    isActive = true, remoteRevision = 1L, createdAt = "2026-09-23T00:00:00Z", updatedAt = "2026-09-23T00:00:00Z",
                ),
            ),
        )
        coEvery { api.fetchCategoryPresentations() } returns CategoryApiResponse.Success(emptyList())
        coEvery { categoryDao.getCategoryById(any()) } returns null

        val worker = SyncCategoryCommandsWorker(
            appContext = context,
            workerParams = workerParams,
            categoryDao = categoryDao,
            merchantDao = merchantDao,
            merchantRulesDao = merchantRulesDao,
            api = api,
            sessionCoordinator = sessionCoordinator,
            transactionRunner = transactionRunner,
        )

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)

        coVerify { categoryDao.complete(any(), "COMPLETED", null, null, any()) }
        coVerify { merchantDao.insertMerchants(match { it.size == 1 && it.first().id == "m1" }) }
        coVerify {
            categoryDao.insertCategory(match { it.id == "00000000-0000-0000-0000-000000000001" })
            categoryDao.insertCategory(match { it.id == "00000000-0000-0000-0000-000000000002" })
            categoryDao.insertCategory(match { it.id == "00000000-0000-0000-0000-000000000003" })
        }
        coVerify(exactly = 3) { categoryDao.insertPresentation(any()) }
    }

    @Test
    fun workerDispatchesSourceAliasAndPreferenceCommandsAndAdvancesLocalRevisions() = runTest {
        val preserveOperation = UUID.randomUUID().toString()
        val aliasUpsertOperation = UUID.randomUUID().toString()
        val aliasDeleteOperation = UUID.randomUUID().toString()
        val preferenceUpsertOperation = UUID.randomUUID().toString()
        val preferenceDeleteOperation = UUID.randomUUID().toString()
        val commands = listOf(
            command(preserveOperation, "PRESERVE_MERCHANT_SOURCE_TEXT", "movement-1", """{"operation_id":"$preserveOperation","movement_id":"movement-1","merchant_raw_text":"  IZIPAY*TÁMBO  ","payload_hash":"h1"}"""),
            command(aliasUpsertOperation, "UPSERT_MERCHANT_ALIAS_RULE", "rule-1", """{"operation_id":"$aliasUpsertOperation","rule_id":"rule-1","normalized_pattern":"izipay*tambo","merchant_id":"merchant-1","payload_hash":"h2"}"""),
            command(aliasDeleteOperation, "DELETE_MERCHANT_ALIAS_RULE", "rule-1", """{"operation_id":"$aliasDeleteOperation","rule_id":"rule-1","expected_revision":1,"payload_hash":"h3"}"""),
            command(preferenceUpsertOperation, "UPSERT_MERCHANT_CATEGORY_PREFERENCE", "merchant-1", """{"operation_id":"$preferenceUpsertOperation","preference_id":"preference-1","merchant_id":"merchant-1","category_id":"category-1","payload_hash":"h4"}"""),
            command(preferenceDeleteOperation, "DELETE_MERCHANT_CATEGORY_PREFERENCE", "merchant-1", """{"operation_id":"$preferenceDeleteOperation","preference_id":"preference-1","expected_revision":1,"payload_hash":"h5"}"""),
        )
        prepareCommands(commands)
        coEvery { api.fetchCategories() } returns CategoryApiResponse.Success(emptyList())
        coEvery { api.fetchCategoryPresentations() } returns CategoryApiResponse.Success(emptyList())
        coEvery { merchantDao.getLatestVersion() } returns 0L
        coEvery { api.fetchMerchantCatalog(any()) } returns CategoryApiResponse.Success(emptyList())
        coEvery { merchantRulesDao.getPreference(testUserId, "merchant-1") } returns MerchantCategoryPreferenceEntity(
            userId = testUserId,
            merchantId = "merchant-1",
            id = "preference-1",
            categoryId = "category-1",
            remoteRevision = 1,
            updatedAt = 1000L,
        )
        coEvery { api.preserveMerchantSourceText(testUserId, any()) } returns CategoryApiResponse.Success(CategoryCommandResponseDto(success = true, status = "APPLIED"))
        coEvery { api.upsertMerchantAliasRule(testUserId, any()) } returns CategoryApiResponse.Success(CategoryCommandResponseDto(success = true, status = "APPLIED"))
        coEvery { api.deleteMerchantAliasRule(testUserId, any()) } returns CategoryApiResponse.Success(CategoryCommandResponseDto(success = true, status = "APPLIED"))
        coEvery { api.upsertMerchantCategoryPreference(testUserId, any()) } returns CategoryApiResponse.Success(CategoryCommandResponseDto(success = true, status = "APPLIED"))
        coEvery { api.deleteMerchantCategoryPreference(testUserId, any()) } returns CategoryApiResponse.Success(CategoryCommandResponseDto(success = true, status = "APPLIED"))

        val worker = SyncCategoryCommandsWorker(context, workerParams, categoryDao, merchantDao, merchantRulesDao, api, sessionCoordinator, transactionRunner)
        assertEquals(ListenableWorker.Result.success(), worker.doWork())

        coVerify { api.preserveMerchantSourceText(testUserId, match { it.merchantRawText == "  IZIPAY*TÁMBO  " }) }
        coVerify { api.upsertMerchantAliasRule(testUserId, match { it.ruleId == "rule-1" }) }
        coVerify { api.deleteMerchantAliasRule(testUserId, match { it.ruleId == "rule-1" }) }
        coVerify { api.upsertMerchantCategoryPreference(testUserId, match { it.merchantId == "merchant-1" }) }
        coVerify { api.deleteMerchantCategoryPreference(testUserId, match { it.preferenceId == "preference-1" }) }
        coVerify { merchantRulesDao.setAliasSyncState(testUserId, "rule-1", "SYNCED", 1L, null) }
        coVerify { merchantRulesDao.setAliasSyncState(testUserId, "rule-1", "SYNCED", 2L, null) }
        coVerify { merchantRulesDao.setPreferenceSyncState(testUserId, "merchant-1", "SYNCED", 1L, null) }
        coVerify { merchantRulesDao.setPreferenceSyncState(testUserId, "merchant-1", "SYNCED", 2L, null) }
        coVerify(exactly = 5) { categoryDao.complete(any(), "COMPLETED", null, null, any()) }
    }

    @Test
    fun workerMarksServerRejectedAliasAsConflictInsteadOfRetryingForever() = runTest {
        val operationId = UUID.randomUUID().toString()
        val pending = command(
            operationId,
            "UPSERT_MERCHANT_ALIAS_RULE",
            "rule-rejected",
            """{"operation_id":"$operationId","rule_id":"rule-rejected","normalized_pattern":"tambo","merchant_id":"merchant-1","payload_hash":"hash"}""",
        )
        prepareCommands(listOf(pending))
        coEvery { api.fetchCategories() } returns CategoryApiResponse.Success(emptyList())
        coEvery { api.fetchCategoryPresentations() } returns CategoryApiResponse.Success(emptyList())
        coEvery { merchantDao.getLatestVersion() } returns 0L
        coEvery { api.fetchMerchantCatalog(any()) } returns CategoryApiResponse.Success(emptyList())
        coEvery { api.upsertMerchantAliasRule(testUserId, any()) } returns CategoryApiResponse.Error(400, "PREMIUM_REQUIRED")

        val worker = SyncCategoryCommandsWorker(context, workerParams, categoryDao, merchantDao, merchantRulesDao, api, sessionCoordinator, transactionRunner)
        assertEquals(ListenableWorker.Result.success(), worker.doWork())

        coVerify { merchantRulesDao.setAliasSyncState(testUserId, "rule-rejected", "FAILED", null, "HTTP_400") }
        coVerify { categoryDao.complete(match { it.operationId == operationId }, "FAILED_PERMANENT", "HTTP_400", null, any()) }
    }

    private fun prepareCommands(commands: List<CategorySyncOutboxEntity>) {
        val leased = commands.map { it.copy(state="IN_FLIGHT", nextAttemptAt=61_000, updatedAt=1_000) }
        coEvery { categoryDao.claim(testUserId, any()) } returnsMany (leased + listOf(null))
        coEvery { categoryDao.getUnresolvedCommands(testUserId) } returns commands
        coEvery { categoryDao.getCommand(testUserId, any()) } answers { leased.firstOrNull { it.operationId == secondArg<String>() } }
    }

    private fun command(operationId: String, type: String, aggregateId: String, payload: String) = CategorySyncOutboxEntity(
        operationId = operationId,
        userId = testUserId,
        commandType = type,
        aggregateType = when {
            type.contains("ALIAS_RULE") -> "MERCHANT_ALIAS_RULE"
            type.contains("CATEGORY_PREFERENCE") -> "MERCHANT_CATEGORY_PREFERENCE"
            else -> "MOVEMENT"
        },
        aggregateId = aggregateId,
        payloadJson = payload,
        payloadHash = "hash-$operationId",
        state = "PENDING",
        createdAt = 1000L,
        updatedAt = 1000L,
    )

    @Test
    fun workerDoesNotOverwritePendingLocalCategoryDuringHydration() = runTest {
        val pendingCatId = "00000000-0000-0000-0000-000000000001"
        val pendingCmd = CategorySyncOutboxEntity(
            operationId = UUID.randomUUID().toString(),
            userId = testUserId,
            commandType = "SET_CATEGORY_ACTIVE",
            aggregateType = "CATEGORY",
            aggregateId = pendingCatId,
            payloadJson = "{}",
            payloadHash = "hash-pending",
            state = "PENDING",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val existingLocal = com.kipu.app.feature.categories.data.local.CategoryEntity(
            id = pendingCatId,
            userId = testUserId,
            parentId = null,
            origin = "CUSTOM",
            categoryType = "EXPENSE",
            isActive = false,
            remoteRevision = 1L,
            createdAt = 1000L,
            updatedAt = 1000L,
        )

        prepareCommands(listOf(pendingCmd))
        coEvery { categoryDao.getCategoryById(pendingCatId) } returns existingLocal
        coEvery { api.fetchCategories() } returns CategoryApiResponse.Success(
            listOf(
                CategoryCatalogItemDto(
                    id = pendingCatId,
                    name = "Pending Cat Remote",
                    origin = "CUSTOM",
                    isActive = true,
                    remoteRevision = 2L,
                    createdAt = "2026-09-23T00:00:00Z",
                    updatedAt = "2026-09-23T00:00:00Z",
                )
            )
        )
        coEvery { api.fetchCategoryPresentations() } returns CategoryApiResponse.Success(emptyList())

        val worker = SyncCategoryCommandsWorker(context, workerParams, categoryDao, merchantDao, merchantRulesDao, api, sessionCoordinator, transactionRunner)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { categoryDao.updateCategory(match { it.id == pendingCatId }) }
        coVerify(exactly = 0) { categoryDao.insertCategory(match { it.id == pendingCatId }) }
    }

    @Test
    fun workerSkipsPersistedWorkForAnInactiveOwner() = runTest {
        io.mockk.every { sessionCoordinator.localAccess } returns MutableStateFlow(LocalAccess.NoOwner)
        val worker = SyncCategoryCommandsWorker(context, workerParams, categoryDao, merchantDao, merchantRulesDao, api, sessionCoordinator, transactionRunner)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { api.fetchCategories() }
        coVerify(exactly = 0) { api.fetchMerchantCatalog(any()) }
    }
}
