package com.kipu.app.core.security

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Local binding check for tokens received from Auth; server verification remains authoritative. */
internal fun accessTokenSubject(token: String): String? = runCatching {
    val parts = token.split('.')
    require(parts.size == 3 && parts.all { it.isNotBlank() })
    val payload = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
    Json.parseToJsonElement(payload).jsonObject["sub"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
}.getOrNull()
