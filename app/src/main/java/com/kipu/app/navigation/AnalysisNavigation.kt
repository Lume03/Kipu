package com.kipu.app.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.kipu.app.feature.analysis.presentation.AnalysisScreen

const val ANALYSIS_ROUTE = "analysis"

fun NavGraphBuilder.analysisDestinations() {
    composable(ANALYSIS_ROUTE) { AnalysisScreen() }
}
