package com.neubofy.remotemanager.ui.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

object BottomNavBridge {

    @JvmStatic
    fun setupBottomNav(
        composeView: ComposeView,
        currentTab: MainNavTab,
        visible: Boolean,
        onTabSelected: (MainNavTab) -> Unit
    ) {
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        composeView.setContent {
            com.neubofy.remotemanager.ui.theme.RemoteManagerTheme {
                MainBottomNavigationBar(
                    currentTab = currentTab,
                    onTabSelected = onTabSelected,
                    visible = visible
                )
            }
        }
    }
}
