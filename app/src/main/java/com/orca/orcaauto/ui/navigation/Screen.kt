package com.orca.orcaauto.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.orca.orcaauto.ui.locale.AppStrings

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Dashboard : Screen("dashboard", "Trạng thái", Icons.Default.Dashboard)
    data object History : Screen("history", "Lịch sử AI", Icons.Default.History)
    data object Settings : Screen("settings", "Cấu hình", Icons.Default.Settings)

    fun getTitle(strings: AppStrings): String = when (this) {
        History -> strings.navHistory
        Dashboard -> strings.navDashboard
        Settings -> strings.navSettings
    }

    companion object {
        val items: List<Screen>
            get() = listOf(History, Dashboard, Settings)
    }
}
