package com.deepseek.widget

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
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
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class ComposeShellJourneyTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun awaitHomeComposeTree() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onAllNodesWithContentDescription("下一步").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
    }

    @Test
    fun homeAndInsightsAreReachableFromPrimaryNavigation() {
        composeRule.onNodeWithContentDescription("下一步").assertIsDisplayed()

        composeRule.runOnUiThread {
            composeRule.activity
                .findViewById<BottomNavigationView>(R.id.bottom_nav)
                .selectedItemId = R.id.insightsFragment
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("洞察").assertIsDisplayed()
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
            R.id.dataSourceCenterFragment
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

    @Test
    fun providerCenterSurvivesRapidBidirectionalScrolling() {
        val repository = (composeRule.activity.application as DeepSeekWidgetApp)
            .container.providerProfileRepository
        runBlocking {
            repository.save(
                providerId = "deepseek",
                alias = "滚动测试 DeepSeek",
                values = mapOf("api_key" to "test-only"),
                id = "instrumentation-scroll-deepseek"
            )
            repository.save(
                providerId = "custom",
                alias = "滚动测试自定义接口",
                values = mapOf("api_key" to "test-only"),
                configJson = "{\"testUrl\":\"https://example.invalid/models\"}",
                backgroundSync = false,
                id = "instrumentation-scroll-custom"
            )
        }

        try {
            composeRule.runOnUiThread {
                val navHost = composeRule.activity.supportFragmentManager
                    .findFragmentById(R.id.nav_host_container) as NavHostFragment
                navHost.navController.navigate(R.id.dataSourceCenterFragment)
            }
            composeRule.waitForIdle()
            val list = composeRule.onNodeWithTag("provider_center_list")
            list.assertIsDisplayed()
            repeat(50) {
                list.performTouchInput { swipeUp(durationMillis = 90) }
                list.performTouchInput { swipeDown(durationMillis = 90) }
            }
            list.assertIsDisplayed()
        } finally {
            runBlocking {
                repository.delete("instrumentation-scroll-deepseek")
                repository.delete("instrumentation-scroll-custom")
            }
        }
    }
}
