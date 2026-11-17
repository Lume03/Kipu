package com.kipu.app.feature.accounts.presentation.detail

internal data class DetailLoadState<T>(
    val value: T? = null,
    val isLoading: Boolean = true,
    val failed: Boolean = false,
)
