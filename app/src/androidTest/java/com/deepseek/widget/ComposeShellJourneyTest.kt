package com.deepseek.widget

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.navigation.NavigationView
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposeShellJourneyTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun awaitHomeComposeTree() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onAllNodesWithText("下一步").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
    }

    @Test
    fun homeAndInsightsAreReachableFromPrimaryNavigation() {
        composeRule.onNodeWithText("下一步").assertIsDisplayed()

        composeRule.runOnUiThread {
            composeRule.activity
                .findViewById<BottomNavigationView>(R.id.bottom_nav)
                .selectedItemId = R.id.insightsFragment
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("INSIGHTS").assertIsDisplayed()
    }

    @Test
    fun homePanelMovesThroughWindowFullscreenCompactAndCloseStates() {
        composeRule.onNodeWithTag("home_panel_overview").performClick()
        composeRule.onNodeWithTag("home_expanded_window").assertIsDisplayed()
        composeRule.onNodeWithTag("home_window_windowed").assertIsDisplayed()

        composeRule.onNodeWithTag("window_dot_expand").performClick()
        composeRule.onNodeWithTag("home_window_fullscreen").assertIsDisplayed()
        composeRule.onNodeWithTag("window_dot_expand").assertIsNotEnabled()

        composeRule.onNodeWithTag("window_dot_minimize").performClick()
        composeRule.onNodeWithTag("home_window_windowed").assertIsDisplayed()

        composeRule.onNodeWithTag("window_dot_minimize").performClick()
        composeRule.onNodeWithTag("home_window_compact").assertIsDisplayed()

        composeRule.onNodeWithTag("window_dot_minimize").performClick()
        composeRule.onAllNodesWithTag("home_expanded_window").assertCountEquals(0)
    }

    @Test
    fun rightToolDrawerDestinationsNavigateAndCloseWithoutCrash() {
        val destinations = listOf(
            R.id.focusFragment,
            R.id.focusHistoryFragment,
            R.id.reviewArchiveFragment,
            R.id.deepSeekFragment,
            R.id.apiKeyFunFragment,
            R.id.apiKeyFunKeysFragment
        )

        destinations.forEach { destinationId ->
            composeRule.runOnUiThread {
                val activity = composeRule.activity
                val drawer = activity.findViewById<DrawerLayout>(R.id.navigation_drawer)
                val navigation = activity.findViewById<NavigationView>(R.id.side_navigation)
                drawer.openDrawer(GravityCompat.END)
                navigation.menu.performIdentifierAction(destinationId, 0)
            }
            composeRule.waitForIdle()

            composeRule.runOnUiThread {
                val activity = composeRule.activity
                val drawer = activity.findViewById<DrawerLayout>(R.id.navigation_drawer)
                val navHost = activity.supportFragmentManager
                    .findFragmentById(R.id.nav_host_container) as NavHostFragment
                assertEquals(destinationId, navHost.navController.currentDestination?.id)
                assertFalse(drawer.isDrawerOpen(GravityCompat.END))
                navHost.navController.popBackStack(R.id.workbenchFragment, false)
            }
            composeRule.waitForIdle()
        }
    }
}
