package com.moneykeeper.feature.forecast.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.moneykeeper.feature.forecast.ui.ForecastRoute

private const val ROUTE_FORECAST = "forecast"

fun NavGraphBuilder.forecastGraph(navController: NavController) {
    composable(ROUTE_FORECAST) {
        ForecastRoute()
    }
}
