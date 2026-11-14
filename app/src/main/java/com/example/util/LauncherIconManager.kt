package com.example.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.example.model.AppThemeStyle

object LauncherIconManager {

    private const val ALIAS_DEFAULT = "com.example.MainActivityDefault"
    private const val ALIAS_NOTHING_OS = "com.example.MainActivityNothingOs"

    fun applyThemeIcon(context: Context, style: AppThemeStyle) {
        runCatching {
            val pm = context.packageManager
            val defaultComponent = ComponentName(context.packageName, ALIAS_DEFAULT)
            val nothingComponent = ComponentName(context.packageName, ALIAS_NOTHING_OS)

            val defaultState = if (style == AppThemeStyle.STANDARD) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            val nothingState = if (style == AppThemeStyle.NOTHING_OS) {
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
        }
    }
}
