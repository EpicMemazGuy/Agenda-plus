package com.example.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AxolaTypographyStyle
import com.example.ui.AppTab
import com.example.ui.theme.LocalAxolaCustomTheme
import com.example.ui.theme.LocalDarkThemeActive
import com.example.ui.theme.NothingAsh
import com.example.ui.theme.NothingBlack
import com.example.ui.theme.NothingMatteElevated
import com.example.ui.theme.NothingWhite

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape

data class NavItem(
    val tab: AppTab,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val oneUiSelectedIcon: ImageVector,
    val oneUiUnselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun AppBottomNavigationBar(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val nothingMode = isNothingOs
    val oneUiMode = isOneUi85
    val windowsPhoneMode = isWindowsPhone
    val iosMode = isIosLiquidGlass
    val axolaMode = isAxolaCustom
    val axolaTheme = LocalAxolaCustomTheme.current
    val isDark = LocalDarkThemeActive.current

    val navItems = remember {
        listOf(
            NavItem(
                tab = AppTab.TODAY,
                selectedIcon = Icons.Filled.Today,
                unselectedIcon = Icons.Outlined.Today,
                oneUiSelectedIcon = OneUiIcons.TodayFilled,
                oneUiUnselectedIcon = OneUiIcons.TodayOutline,
                testTag = "nav_tab_today"
            ),
            NavItem(
                tab = AppTab.ROOSTER,
                selectedIcon = Icons.Filled.CalendarMonth,
                unselectedIcon = Icons.Outlined.CalendarMonth,
                oneUiSelectedIcon = OneUiIcons.RoosterFilled,
                oneUiUnselectedIcon = OneUiIcons.RoosterOutline,
                testTag = "nav_tab_rooster"
            ),
            NavItem(
                tab = AppTab.HUISWERK,
                selectedIcon = Icons.Filled.ListAlt,
                unselectedIcon = Icons.Outlined.ListAlt,
                oneUiSelectedIcon = OneUiIcons.HuiswerkFilled,
                oneUiUnselectedIcon = OneUiIcons.HuiswerkOutline,
                testTag = "nav_tab_huiswerk"
            ),
            NavItem(
                tab = AppTab.CIJFERS,
                selectedIcon = Icons.Filled.Analytics,
                unselectedIcon = Icons.Outlined.Analytics,
                oneUiSelectedIcon = OneUiIcons.CijfersFilled,
                oneUiUnselectedIcon = OneUiIcons.CijfersOutline,
                testTag = "nav_tab_cijfers"
            ),
            NavItem(
                tab = AppTab.MEER,
                selectedIcon = Icons.Filled.Apps,
                unselectedIcon = Icons.Outlined.Apps,
                oneUiSelectedIcon = OneUiIcons.MeerFilled,
                oneUiUnselectedIcon = OneUiIcons.MeerOutline,
                testTag = "nav_tab_meer"
            )
        )
    }

    if (oneUiMode || iosMode) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = if (iosMode) 18.dp else 24.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            val dockShape = if (iosMode) CircleShape else SquircleShape(26.dp)
            val specularBorder = if (iosMode) {
                BorderStroke(
                    width = 1.dp,
                    color = if (isDark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.70f)
                )
            } else {
                oneUiInnerHighlightBorder()
            }
            OneUiFrostedSurface(
                shape = dockShape,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(
                    alpha = if (isDark) 0.78f else 0.82f
                ),
                border = specularBorder,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("main_navigation_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    navItems.forEach { item ->
                        val isSelected = currentTab == item.tab
                        val pillColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.22f else 0.13f)
                            } else {
                                Color.Transparent
                            },
                            animationSpec = oneUiSpring(),
                            label = "floating_nav_pill_${item.tab.name}"
                        )
                        val tintColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            animationSpec = oneUiTween(240),
                            label = "floating_nav_tint_${item.tab.name}"
                        )
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.08f else 1.0f,
                            animationSpec = oneUiSpring(),
                            label = "floating_nav_scale_${item.tab.name}"
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(if (iosMode) CircleShape else SquircleShape(20.dp))
                                .background(pillColor)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = true)
                                ) { onTabSelected(item.tab) }
                                .padding(vertical = 7.dp, horizontal = 2.dp)
                                .testTag(item.testTag)
                        ) {
                            Crossfade(
                                targetState = isSelected,
                                animationSpec = oneUiTween(220),
                                label = "floating_nav_icon_${item.tab.name}"
                            ) { selected ->
                                Icon(
                                    imageVector = if (selected) item.oneUiSelectedIcon else item.oneUiUnselectedIcon,
                                    contentDescription = item.tab.title,
                                    tint = tintColor,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .graphicsLayer {
                                            scaleX = iconScale
                                            scaleY = iconScale
                                        }
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.tab.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = tintColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    } else if (windowsPhoneMode) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RectangleShape,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = modifier
                .fillMaxWidth()
                .testTag("main_navigation_bar")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(60.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                navItems.forEach { item ->
                    val isSelected = currentTab == item.tab
                    val fgColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top,
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                            .clickable { onTabSelected(item.tab) }
                            .testTag(item.testTag)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else Color.Transparent
                                )
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.tab.title,
                            tint = fgColor,
                            modifier = Modifier.size(21.dp)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = item.tab.title.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = fgColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    } else {
        Column(modifier = modifier) {
            if (nothingMode) {
                NothingDottedDivider()
            }
            NavigationBar(
                modifier = Modifier.testTag("main_navigation_bar"),
                windowInsets = WindowInsets.navigationBars,
                containerColor = when {
                    nothingMode -> NothingBlack
                    axolaMode -> MaterialTheme.colorScheme.surfaceContainer
                    else -> MaterialTheme.colorScheme.surfaceContainer
                },
                tonalElevation = if (nothingMode || axolaMode) 0.dp else NavigationBarDefaults.Elevation
            ) {
                navItems.forEach { item ->
                    val isSelected = currentTab == item.tab
                    val labelText = when {
                        nothingMode -> item.tab.title.uppercase()
                        axolaMode && axolaTheme.typographyStyle == AxolaTypographyStyle.NOTHING_DOT_MATRIX ->
                            item.tab.title.uppercase()
                        axolaMode && axolaTheme.typographyStyle == AxolaTypographyStyle.WINDOWS_LIGHT_LOWERCASE ->
                            item.tab.title.lowercase()
                        else -> item.tab.title
                    }
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
                                text = labelText,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
}
