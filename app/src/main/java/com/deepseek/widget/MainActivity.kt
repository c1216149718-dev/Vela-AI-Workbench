package com.deepseek.widget

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.deepseek.widget.data.AppPreferences
import com.deepseek.widget.data.ThemeMode
import com.deepseek.widget.databinding.ActivityMainBinding
import com.deepseek.widget.worker.WidgetUpdateWorker
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private var currentRootIndex = 0
    private var indicatorAnimator: ObjectAnimator? = null
    private var sideHandleSleepRunnable: Runnable? = null
    private val useNavigationRail by lazy { resources.configuration.smallestScreenWidthDp >= 600 }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupThemeMode()
        setupBottomNavigationBlur()

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_container)
            as? NavHostFragment
            ?: error("NavHostFragment is missing from activity_main")
        navController = navHost.navController
        setupNavigation()
        setupDrawerBackHandling()

        WidgetUpdateWorker.schedulePeriodic(this)
    }

    private fun setupNavigation() {
        binding.bottomNav.isItemActiveIndicatorEnabled = false
        binding.bottomNav.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        binding.bottomNav.setupWithNavController(navController)
        binding.navigationRail.setupWithNavController(navController)
        setupSideNavigation()
        setupSideHandle()
        val railPadding = if (useNavigationRail) (80 * resources.displayMetrics.density).toInt() else 0
        binding.blurTarget.setPadding(railPadding, 0, 0, 0)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val rootIndex = ROOT_DESTINATIONS.indexOf(destination.id)
            binding.bottomNavBlur.isVisible = rootIndex >= 0 && !useNavigationRail
            binding.navigationRail.isVisible = rootIndex >= 0 && useNavigationRail
            binding.sideMenuHandleTarget.isVisible = rootIndex >= 0
            if (rootIndex >= 0) {
                currentRootIndex = rootIndex
                binding.bottomNav.post { moveBottomNavigationIndicator(rootIndex, animated = true) }
            }
        }
        binding.bottomNav.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            moveBottomNavigationIndicator(currentRootIndex, animated = false)
        }
        handleDeepLink(intent, navController)
    }

    private fun setupSideNavigation() {
        binding.sideNavigation.setNavigationItemSelectedListener { item ->
            val targetId = item.itemId
            val currentId = navController.currentDestination?.id
            if (currentId != targetId) {
                val options = NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .build()
                runCatching { navController.navigate(targetId, null, options) }
                    .onFailure { return@setNavigationItemSelectedListener false }
            }
            binding.navigationDrawer.closeDrawer(GravityCompat.END)
            true
        }
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.sideNavigation.setCheckedItem(destination.id)
        }
    }

    private fun setupSideHandle() {
        val target = binding.sideMenuHandleTarget
        val handle = binding.sideMenuButton
        val density = resources.displayMetrics.density
        val sleepOffset = 35f * density
        val dragThreshold = 24f * density
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var draggedOpen = false

        fun motionEnabled(): Boolean = runCatching {
            Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }.getOrDefault(true)

        fun reveal(animated: Boolean) {
            sideHandleSleepRunnable?.let(target::removeCallbacks)
            if (animated && motionEnabled()) {
                handle.animate().cancel()
                handle.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(180L)
                    .setInterpolator(FastOutSlowInInterpolator())
                    .start()
            } else {
                handle.animate().cancel()
                handle.translationX = 0f
                handle.alpha = 1f
            }
        }

        fun sleep(animated: Boolean) {
            if (animated && motionEnabled()) {
                handle.animate().cancel()
                handle.animate()
                    .translationX(sleepOffset)
                    .alpha(0.7f)
                    .setDuration(240L)
                    .setInterpolator(FastOutSlowInInterpolator())
                    .start()
            } else {
                handle.animate().cancel()
                handle.translationX = sleepOffset
                handle.alpha = 0.7f
            }
        }

        fun scheduleSleep() {
            sideHandleSleepRunnable?.let(target::removeCallbacks)
            sideHandleSleepRunnable = Runnable { sleep(animated = true) }.also {
                target.postDelayed(it, SIDE_HANDLE_AWAKE_MS)
            }
        }

        fun openDrawer() {
            sideHandleSleepRunnable?.let(target::removeCallbacks)
            binding.navigationDrawer.openDrawer(GravityCompat.END, motionEnabled())
        }

        target.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    target.parent?.requestDisallowInterceptTouchEvent(true)
                    downX = event.rawX
                    draggedOpen = false
                    handle.isPressed = true
                    reveal(animated = true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val inwardDistance = downX - event.rawX
                    if (!draggedOpen && inwardDistance > dragThreshold) {
                        draggedOpen = true
                        openDrawer()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    target.parent?.requestDisallowInterceptTouchEvent(false)
                    handle.isPressed = false
                    val moved = kotlin.math.abs(event.rawX - downX)
                    if (!draggedOpen && moved <= touchSlop) {
                        target.performClick()
                    } else if (!draggedOpen) {
                        scheduleSleep()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    target.parent?.requestDisallowInterceptTouchEvent(false)
                    handle.isPressed = false
                    if (!draggedOpen) scheduleSleep()
                    true
                }
                else -> false
            }
        }
        target.setOnClickListener { openDrawer() }
        target.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) reveal(animated = true) else scheduleSleep()
        }
        binding.navigationDrawer.addDrawerListener(
            object : androidx.drawerlayout.widget.DrawerLayout.SimpleDrawerListener() {
                override fun onDrawerClosed(drawerView: View) {
                    scheduleSleep()
                }
            }
        )
        target.post {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                target.systemGestureExclusionRects = listOf(Rect(0, 0, target.width, target.height))
            }
            sleep(animated = false)
        }
    }

    private fun setupDrawerBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.navigationDrawer.isDrawerOpen(GravityCompat.END)) {
                    binding.navigationDrawer.closeDrawer(GravityCompat.END)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    private fun setupBottomNavigationBlur() {
        val nav = binding.bottomNav
        val startPadding = nav.paddingStart
        val topPadding = nav.paddingTop
        val endPadding = nav.paddingEnd
        val bottomPadding = nav.paddingBottom
        nav.background = ColorDrawable(Color.TRANSPARENT)
        ViewCompat.setOnApplyWindowInsetsListener(nav) { view, insets ->
            view.setPaddingRelative(startPadding, topPadding, endPadding, bottomPadding)
            insets
        }
        ViewCompat.requestApplyInsets(nav)
        binding.bottomNavBlur
            .setupWith(binding.blurTarget)
            .setFrameClearDrawable(window.decorView.background)
            .setBlurRadius(52f)
            .setOverlayColor(ContextCompat.getColor(this, R.color.bottom_nav_blur_overlay))
            .setBlurEnabled(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
    }

    private fun setupThemeMode() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppPreferences(applicationContext).themeMode.distinctUntilChanged().collect { mode ->
                    val target = when (mode) {
                        ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                        ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                    }
                    val current = AppCompatDelegate.getDefaultNightMode()
                    val alreadyFollowingSystem = mode == ThemeMode.SYSTEM &&
                        current == AppCompatDelegate.MODE_NIGHT_UNSPECIFIED
                    if (!alreadyFollowingSystem && current != target) {
                        AppCompatDelegate.setDefaultNightMode(target)
                    }
                }
            }
        }
    }

    fun setBottomNavigationWindowState(open: Boolean, fullscreen: Boolean) {
        binding.bottomNav.menu.setGroupEnabled(0, !open)
        binding.navigationRail.menu.setGroupEnabled(0, !open)
        binding.sideMenuHandleTarget.isEnabled = !open

        indicatorAnimator?.cancel()
        binding.bottomNavBlur.animate().cancel()
        binding.bottomNavIndicator.animate().cancel()
        if (fullscreen) {
            binding.sideMenuHandleTarget.isVisible = false
            binding.bottomNavIndicator.animate()
                .scaleX(1.24f)
                .scaleY(0.94f)
                .alpha(0f)
                .setDuration(180L)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
            binding.bottomNavBlur.animate()
                .translationY(binding.bottomNavBlur.height.toFloat() + 28f)
                .alpha(0f)
                .setStartDelay(70L)
                .setDuration(300L)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        } else {
            binding.sideMenuHandleTarget.isVisible = navController.currentDestination?.id in ROOT_DESTINATIONS
            binding.bottomNavBlur.animate()
                .translationY(0f)
                .alpha(if (open) 0.72f else 1f)
                .setStartDelay(0L)
                .setDuration(300L)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
            binding.bottomNavIndicator.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(260L)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        }
    }

    fun setFocusImmersive(active: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (active) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(window, true)
        }
    }

    private fun moveBottomNavigationIndicator(index: Int, animated: Boolean) {
        if (binding.bottomNav.width == 0 || binding.bottomNavIndicator.width == 0) return
        val itemWidth = binding.bottomNav.width.toFloat() / binding.bottomNav.menu.size()
        val target = itemWidth * index + (itemWidth - binding.bottomNavIndicator.width) / 2f
        val indicator = binding.bottomNavIndicator
        if (!animated || kotlin.math.abs(indicator.translationX - target) < 1f) {
            indicator.translationX = target
            return
        }

        indicator.animate().cancel()
        indicatorAnimator?.cancel()
        indicatorAnimator = ObjectAnimator.ofPropertyValuesHolder(
            indicator,
            PropertyValuesHolder.ofFloat(View.TRANSLATION_X, indicator.translationX, target),
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.2f, 0.96f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.94f, 1.02f, 1f)
        ).apply {
            duration = 360L
            interpolator = FastOutSlowInInterpolator()
            start()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent, navController)
    }

    /**
     * 处理 Intent extra [EXTRA_OPEN_DESTINATION]，从桌面小组件点击余额区域时
     * 直接进入对应供应商页。
     */
    private fun handleDeepLink(intent: Intent?, navController: NavController) {
        val sourceIntent = intent ?: return
        val destination = sourceIntent.getIntExtra(EXTRA_OPEN_DESTINATION, -1)
        if (destination == -1 || navController.graph.findNode(destination) == null) return

        val entityId = sourceIntent.getLongExtra(EXTRA_ENTITY_ID, -1L)
        val args = Bundle().apply {
            when (destination) {
                R.id.taskEditFragment -> putLong("taskId", entityId)
                R.id.focusFragment -> putLong("taskId", entityId)
            }
        }
        if (destination in ROOT_DESTINATIONS) {
            binding.bottomNav.selectedItemId = destination
        } else if (navController.currentDestination?.id != destination || entityId > 0) {
            navController.navigate(destination, args)
        }
        sourceIntent.removeExtra(EXTRA_OPEN_DESTINATION)
        sourceIntent.removeExtra(EXTRA_ENTITY_ID)
    }

    companion object {
        const val EXTRA_OPEN_DESTINATION = "open_destination"
        const val EXTRA_ENTITY_ID = "entity_id"
        private const val SIDE_HANDLE_AWAKE_MS = 1_800L

        private val ROOT_DESTINATIONS = listOf(
            R.id.workbenchFragment,
            R.id.taskListFragment,
            R.id.insightsFragment,
            R.id.settingsFragment
        )
    }
}
