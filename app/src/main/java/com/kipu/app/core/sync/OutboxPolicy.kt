package com.kipu.app.core.sync

/** Shared decisions only; persistence and compensation belong to each module. */
object OutboxPolicy {
    enum class Failure { RETRYABLE, WAITING_FOR_AUTH, CONFLICT, PERMANENT }

    fun classify(httpStatus: Int? = null, status: String? = null, retryable: Boolean? = null,
                 hasSession: Boolean = true, invalidPayload: Boolean = false): Failure = when {
        invalidPayload -> Failure.PERMANENT
        !hasSession || httpStatus == 401 -> Failure.WAITING_FOR_AUTH
        httpStatus == 409 || status == "CONFLICT" -> Failure.CONFLICT
        httpStatus == 404 || httpStatus in listOf(408, 425, 429) || (httpStatus != null && httpStatus >= 500) -> Failure.RETRYABLE
        httpStatus != null && httpStatus in 400..499 -> Failure.PERMANENT
        status == "REJECTED" && retryable != true -> Failure.PERMANENT
        else -> Failure.RETRYABLE
    }

    fun isTerminal(state: String): Boolean = state in setOf("FAILED_PERMANENT", "CONFLICT", "REJECTED")
    fun canRetry(state: String): Boolean = state in setOf("ERROR", "RETRY", "WAITING_FOR_AUTH")
    fun canPrecede(state: String): Boolean = !isTerminal(state) && state !in setOf("BLOCKED", "DISCARDED")
    const val BLOCKED_BY_PREVIOUS = "BLOCKED_BY_PREVIOUS"
}
