package com.kipu.app.feature.categories.data.remote

import com.kipu.app.core.network.AuthenticatedSessionProvider
import com.kipu.app.core.network.remoteCall
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import javax.inject.Inject
import javax.inject.Singleton

sealed interface CategoryApiResponse<out T> {
    data class Success<T>(val data: T) : CategoryApiResponse<T>
    data class Error(val statusCode: Int, val message: String?) : CategoryApiResponse<Nothing>
    data class NetworkFailure(val exception: Throwable) : CategoryApiResponse<Nothing>
}

@Singleton
class CategoriesApi @Inject constructor(
    private val httpClient: HttpClient,
    private val sessionProvider: AuthenticatedSessionProvider,
) {
    private suspend fun getAuthHeader(): String? {
        val session = sessionProvider.currentSession() ?: return null
        return "Bearer ${session.accessToken}"
    }

    suspend fun createCategory(request: CreateCategoryRequestDto): CategoryApiResponse<CategoryCommandResponseDto> {
        return callRpc("create_category_v1", request)
    }

    suspend fun updatePresentation(request: UpdateCategoryPresentationRequestDto): CategoryApiResponse<CategoryCommandResponseDto> {
        return callRpc("update_category_presentation_v1", request)
    }

    suspend fun setCategoryActive(request: SetCategoryActiveRequestDto): CategoryApiResponse<CategoryCommandResponseDto> {
        return callRpc("set_category_active_v1", request)
    }

    suspend fun updateMovementClassification(request: UpdateMovementClassificationRequestDto): CategoryApiResponse<CategoryCommandResponseDto> {
        return callRpc("update_movement_classification_v1", request)
    }

    suspend fun preserveMerchantSourceText(request: PreserveMerchantSourceTextRequestDto): CategoryApiResponse<CategoryCommandResponseDto> {
        return callRpc("preserve_merchant_source_text_v1", request)
    }

    suspend fun upsertMerchantAliasRule(request: UpsertMerchantAliasRuleRequestDto): CategoryApiResponse<CategoryCommandResponseDto> =
        callRpc("upsert_merchant_alias_rule_v1", request)

    suspend fun deleteMerchantAliasRule(request: DeleteMerchantAliasRuleRequestDto): CategoryApiResponse<CategoryCommandResponseDto> =
        callRpc("delete_merchant_alias_rule_v1", request)

    suspend fun upsertMerchantCategoryPreference(request: UpsertMerchantCategoryPreferenceRequestDto): CategoryApiResponse<CategoryCommandResponseDto> =
        callRpc("upsert_merchant_category_preference_v1", request)

    suspend fun deleteMerchantCategoryPreference(request: DeleteMerchantCategoryPreferenceRequestDto): CategoryApiResponse<CategoryCommandResponseDto> =
        callRpc("delete_merchant_category_preference_v1", request)

    suspend fun resolveConflict(request: ResolveCategoryConflictRequestDto): CategoryApiResponse<CategoryCommandResponseDto> {
        return callRpc("resolve_category_conflict_v1", request)
    }

    suspend fun fetchMerchantCatalog(sinceVersion: Long = 0L): CategoryApiResponse<List<MerchantCatalogItemDto>> = remoteCall(onFailure = { CategoryApiResponse.NetworkFailure(it) }) {
        val auth = getAuthHeader() ?: return CategoryApiResponse.Error(401, "No active session")
        fetchPages("merchant_services", "id,name,normalized_name,is_active,version,default_category_id,priority,logo_key,brand_color,logo_path,logo_dark_path,logo_source,user_id,domain,logo_status,catalog_origin", "id", auth, sinceVersion)
    }

    suspend fun fetchCategories(): CategoryApiResponse<List<CategoryCatalogItemDto>> = remoteCall(onFailure = { CategoryApiResponse.NetworkFailure(it) }) {
        val auth = getAuthHeader() ?: return CategoryApiResponse.Error(401, "No active session")
        fetchPages("categories", "id,name,icon_key,user_id,parent_id,origin,category_type,is_active,remote_revision,created_at,updated_at", "id", auth)
    }

    suspend fun fetchCategoryPresentations(): CategoryApiResponse<List<CategoryPresentationCatalogItemDto>> = remoteCall(onFailure = { CategoryApiResponse.NetworkFailure(it) }) {
        val auth = getAuthHeader() ?: return CategoryApiResponse.Error(401, "No active session")
        fetchPages("category_presentations", "category_id,user_id,name,icon,color,remote_revision,updated_at", "category_id,user_id", auth)
    }

    private suspend inline fun <reified T> fetchPages(table: String, columns: String, order: String, auth: String, sinceVersion: Long = 0L): CategoryApiResponse<List<T>> {
        val all = mutableListOf<T>()
        while (true) {
            val response = httpClient.get("rest/v1/$table") {
                header(HttpHeaders.Authorization, auth)
                url {
                    parameters.append("select", columns)
                    if (table == "merchant_services" && sinceVersion > 0) parameters.append("or", "(version.gt.$sinceVersion,user_id.not.is.null)")
                    parameters.append("order", order)
                    parameters.append("limit", "500")
                    parameters.append("offset", all.size.toString())
                }
            }
            if (response.status.value !in 200..299) return CategoryApiResponse.Error(response.status.value, null)
            val page = response.body<List<T>>()
            all.addAll(page)
            if (page.size < 500) return CategoryApiResponse.Success(all)
        }
    }

    private suspend inline fun <reified REQ : Any> callRpc(
        rpcName: String,
        body: REQ
    ): CategoryApiResponse<CategoryCommandResponseDto> {
        return remoteCall(onFailure = { CategoryApiResponse.NetworkFailure(it) }) {
            val auth = getAuthHeader() ?: return CategoryApiResponse.Error(401, "No active session")
            val response = httpClient.post("rest/v1/rpc/$rpcName") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, auth)
                setBody(CategoryRpcPayload(body))
            }
            response.toCategoryApiResponse()
        }
    }

    private suspend inline fun <reified T> HttpResponse.toCategoryApiResponse(): CategoryApiResponse<T> =
        if (status.value in 200..299) {
            CategoryApiResponse.Success(body<T>())
        } else {
            CategoryApiResponse.Error(status.value, bodyAsText())
        }
}
