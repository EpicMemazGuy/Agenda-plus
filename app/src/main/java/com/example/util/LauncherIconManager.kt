package com.example.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.example.model.AppThemeStyle

object LauncherIconManager {

    fun applyThemeIcon(context: Context, style: AppThemeStyle) {
        runCatching {
            val pm = context.packageManager
            val pkg = context.packageName
            val defaultComponent = ComponentName(pkg, "$pkg.MainActivityDefault")
            val nothingComponent = ComponentName(pkg, "$pkg.MainActivityNothingOs")
            val oneUiComponent = ComponentName(pkg, "$pkg.MainActivityOneUi")

            val useDefaultIcon = style != AppThemeStyle.NOTHING_OS && style != AppThemeStyle.ONE_UI
            val defaultState = if (useDefaultIcon) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            val nothingState = if (style == AppThemeStyle.NOTHING_OS) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            val oneUiState = if (style == AppThemeStyle.ONE_UI) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }

            if (pm.getComponentEnabledSetting(defaultComponent) != defaultState) {
                pm.setComponentEnabledSetting(
                    defaultComponent,
                    defaultState,
                    PackageManager.DONT_KILL_APP
                )
            }
            if (pm.getComponentEnabledSetting(nothingComponent) != nothingState) {
                pm.setComponentEnabledSetting(
                    nothingComponent,
                    nothingState,
                    PackageManager.DONT_KILL_APP
                )
            }
            if (pm.getComponentEnabledSetting(oneUiComponent) != oneUiState) {
                pm.setComponentEnabledSetting(
                    oneUiComponent,
                    oneUiState,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }
}
