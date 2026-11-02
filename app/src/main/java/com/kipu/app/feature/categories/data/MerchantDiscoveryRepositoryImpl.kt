package com.kipu.app.feature.categories.data

import com.kipu.app.core.network.AuthenticatedSessionProvider
import com.kipu.app.core.storage.data.MerchantLogoUrlResolver
import com.kipu.app.BuildConfig
import com.kipu.app.feature.categories.data.local.MerchantCatalogDao
import com.kipu.app.feature.categories.data.local.MerchantCatalogEntity
import com.kipu.app.feature.categories.data.remote.MerchantCatalogItemDto
import com.kipu.app.feature.categories.domain.*
import com.kipu.app.feature.categories.domain.model.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.*
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class DiscoveryRequest(val action: String, val query: String? = null, val internet: Boolean = false, val domain: String? = null, val name: String? = null)
@Serializable
internal data class CandidateDto(val name: String, val domain: String, @SerialName("logo_url") val logoUrl: String? = null, val alreadyInCatalog: Boolean = false, val merchantId: String? = null, val query: String)
@Serializable
internal data class DiscoveryResponse(val candidates: List<CandidateDto> = emptyList(), val merchant: MerchantCatalogItemDto? = null)
@Serializable
internal data class DiscoveryError(val code: String = "SERVICE_UNAVAILABLE")

@Singleton
class MerchantDiscoveryRepositoryImpl @Inject constructor(
    private val http: HttpClient,
    private val sessions: AuthenticatedSessionProvider,
    private val dao: MerchantCatalogDao,
) : MerchantDiscoveryRepository {
    private suspend fun request(request: DiscoveryRequest): DiscoveryResponse {
        val session = sessions.currentSession() ?: error("Inicia sesi\u00f3n para continuar")
        try {
            val response = http.post("functions/v1/merchant-search") {
                header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            check(sessions.currentSession()?.userId == session.userId) { "La sesi\u00f3n cambi\u00f3" }
            if (response.status.value !in 200..299) {
                val code = response.body<DiscoveryError>().code
                error(when (code) {
                    "MERCHANT_RATE_LIMITED" -> "Alcanzaste el l\u00edmite de b\u00fasquedas o altas. Int\u00e9ntalo m\u00e1s tarde"
                    "PROVIDER_RATE_LIMITED" -> "El buscador est\u00e1 ocupado. Int\u00e9ntalo m\u00e1s tarde"
                    "CANDIDATE_NOT_CACHED" -> "Vuelve a buscar el comercio antes de agregarlo"
                    "DOMAIN_EXCLUDED" -> "Este comercio no est\u00e1 disponible. Puedes crearlo como privado"
                    "INVALID_MERCHANT_NAME" -> "Escribe un nombre v\u00e1lido sin datos de tarjeta"
                    else -> "No pudimos completar la solicitud. Int\u00e9ntalo m\u00e1s tarde"
                })
            }
            return response.body()
        } catch (e: CancellationException) { throw e
        } catch (e: IOException) { throw IllegalStateException("Necesitas conexi\u00f3n a internet para buscar o crear un comercio", e) }
    }
    override suspend fun searchInternet(query: String): List<MerchantSearchCandidate> =
        request(DiscoveryRequest("search", query = query, internet = true)).candidates.map {
            MerchantSearchCandidate(it.name, it.domain, it.logoUrl?.takeIf { url ->
                runCatching { java.net.URI(url).let { u -> u.scheme == "https" && u.host == "img.logo.dev" && u.userInfo == null && u.port == -1 } }.getOrDefault(false)
            }, it.alreadyInCatalog, it.merchantId, it.query)
        }
    override suspend fun add(candidate: MerchantSearchCandidate): MerchantCatalogEntry =
        persist(request(DiscoveryRequest("add", query = candidate.query, domain = candidate.domain)))
    override suspend fun createPrivate(name: String): MerchantCatalogEntry =
        persist(request(DiscoveryRequest("private", name = name)))
    private suspend fun persist(response: DiscoveryResponse): MerchantCatalogEntry {
        val dto = checkNotNull(response.merchant) { "Respuesta de comercio incompleta" }
        val session = sessions.currentSession() ?: error("Inicia sesi\u00f3n para continuar")
        check(dto.userId == null || dto.userId == session.userId.toString()) { "El comercio pertenece a otra sesi\u00f3n" }
        // An immediate add must not advance the incremental hydration cursor past unseen rows.
        dao.insertMerchants(listOf(MerchantCatalogEntity(
            id = dto.id, name = dto.name, normalizedName = CategoryRules.normalizeText(dto.normalizedName),
            isActive = dto.isActive, version = 0, lastSyncedAt = System.currentTimeMillis(),
            defaultCategoryId = dto.defaultCategoryId, priority = dto.priority, logoKey = dto.logoKey,
            brandColor = dto.brandColor, logoPath = dto.logoPath, logoDarkPath = dto.logoDarkPath,
            logoSource = dto.logoSource, userId = dto.userId,
        )))
        return MerchantCatalogEntry(MerchantId(dto.id), dto.name, CategoryRules.normalizeText(dto.normalizedName),
            dto.isActive, dto.defaultCategoryId?.let(::CategoryId), dto.priority, dto.logoKey, dto.brandColor,
            MerchantLogoUrlResolver.resolve(BuildConfig.SUPABASE_URL, dto.logoPath),
            MerchantLogoUrlResolver.resolve(BuildConfig.SUPABASE_URL, dto.logoDarkPath))
    }
}
