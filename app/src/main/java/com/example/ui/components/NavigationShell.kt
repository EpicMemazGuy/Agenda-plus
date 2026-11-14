package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.AppTab
import com.example.ui.theme.NothingAsh
import com.example.ui.theme.NothingBlack
import com.example.ui.theme.NothingMatteElevated
import com.example.ui.theme.NothingWhite

data class NavItem(
    val tab: AppTab,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun AppBottomNavigationBar(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val nothingMode = isNothingOs
    val navItems = listOf(
        NavItem(
            tab = AppTab.TODAY,
            selectedIcon = Icons.Filled.Today,
            unselectedIcon = Icons.Outlined.Today,
            testTag = "nav_tab_today"
        ),
        NavItem(
            tab = AppTab.ROOSTER,
            selectedIcon = Icons.Filled.CalendarMonth,
            unselectedIcon = Icons.Outlined.CalendarMonth,
            testTag = "nav_tab_rooster"
        ),
        NavItem(
            tab = AppTab.HUISWERK,
            selectedIcon = Icons.Filled.ListAlt,
            unselectedIcon = Icons.Outlined.ListAlt,
            testTag = "nav_tab_huiswerk"
        ),
        NavItem(
            tab = AppTab.CIJFERS,
            selectedIcon = Icons.Filled.Analytics,
            unselectedIcon = Icons.Outlined.Analytics,
            testTag = "nav_tab_cijfers"
        ),
        NavItem(
            tab = AppTab.MEER,
            selectedIcon = Icons.Filled.Apps,
            unselectedIcon = Icons.Outlined.Apps,
            testTag = "nav_tab_meer"
        )
    )

    Column(modifier = modifier) {
        if (nothingMode) {
            NothingDottedDivider()
        }
        NavigationBar(
            modifier = Modifier.testTag("main_navigation_bar"),
            windowInsets = WindowInsets.navigationBars,
            containerColor = if (nothingMode) NothingBlack else MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = if (nothingMode) 0.dp else NavigationBarDefaults.Elevation
        ) {
            navItems.forEach { item ->
                val isSelected = currentTab == item.tab
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onTabSelected(item.tab) },
                    icon = {
                        Icon(
                            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.tab.title
                        )
                    },
                    label = {
                        Text(
                            text = if (nothingMode) item.tab.title.uppercase() else item.tab.title,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = if (nothingMode) {
                        NavigationBarItemDefaults.colors(
                            selectedIconColor = NothingWhite,
                            selectedTextColor = NothingWhite,
                            indicatorColor = NothingMatteElevated,
                            unselectedIconColor = NothingAsh,
                            unselectedTextColor = NothingAsh
                        )
                    } else {
                        NavigationBarItemDefaults.colors()
                    },
                    alwaysShowLabel = true,
                    modifier = Modifier.testTag(item.testTag)
                )
            }
        }
    }
}
