package com.deepseek.widget.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.deepseek.widget.R
import com.deepseek.widget.data.AccountCache
import com.deepseek.widget.domain.model.FocusSession
import com.deepseek.widget.domain.model.FocusStatus
import com.deepseek.widget.domain.model.Task
import com.deepseek.widget.domain.model.TaskPriority
import com.deepseek.widget.domain.model.TaskSourceType
import com.deepseek.widget.domain.model.TaskStatus
import com.deepseek.widget.feature.workbench.WorkbenchUiState
import com.deepseek.widget.ui.components.GlassScreen
import com.deepseek.widget.ui.components.GlassSurface
import com.deepseek.widget.ui.components.ProviderBrand
import com.deepseek.widget.ui.components.ProviderIdentity
import com.deepseek.widget.ui.theme.LocalWorkbenchColors
import com.deepseek.widget.ui.theme.WorkbenchTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

private val GroupShape = RoundedCornerShape(24.dp)

private enum class HomePanel(val title: String, val transformOriginY: Float) {
    OVERVIEW("DAY AT A GLANCE", 0.2f),
    TASKS("NEXT", 0.42f),
    FOCUS("FOCUS", 0.58f),
    AI_RESOURCES("AI RESOURCES", 0.74f),
    REVIEW("DAILY REVIEW", 0.88f)
}

private enum class HomeWindowSize {
    COMPACT,
    WINDOWED,
    FULLSCREEN
}

@Composable
fun HomeScreen(
    state: WorkbenchUiState,
    onAddTask: () -> Unit,
    onTaskToggle: (Task) -> Unit,
    onTaskClick: (Task) -> Unit,
    onSeeAllTasks: () -> Unit,
    onFocusClick: () -> Unit,
    onDeepSeekClick: () -> Unit,
    onApiKeyFunClick: () -> Unit,
    onReviewSave: (String) -> Unit,
    onReviewArchive: () -> Unit,
    modifier: Modifier = Modifier,
    onWindowStateChanged: (open: Boolean, fullscreen: Boolean) -> Unit = { _, _ -> }
) {
    if (state.isLoading) {
        GlassScreen(modifier) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
            }
        }
        return
    }

    var selectedPanel by rememberSaveable { mutableStateOf<HomePanel?>(null) }
    var renderedPanel by remember { mutableStateOf<HomePanel?>(null) }
    var windowSize by rememberSaveable { mutableStateOf(HomeWindowSize.WINDOWED) }
    val windowOpen = selectedPanel != null
    val modalHazeState = rememberHazeState(blurEnabled = true)

    LaunchedEffect(selectedPanel) {
        if (selectedPanel != null) {
            renderedPanel = selectedPanel
        } else {
            delay(320)
            renderedPanel = null
        }
    }
    LaunchedEffect(windowOpen, windowSize) {
        onWindowStateChanged(windowOpen, windowOpen && windowSize == HomeWindowSize.FULLSCREEN)
    }
    BackHandler(enabled = windowOpen) {
        when (windowSize) {
            HomeWindowSize.FULLSCREEN -> windowSize = HomeWindowSize.WINDOWED
            HomeWindowSize.WINDOWED -> windowSize = HomeWindowSize.COMPACT
            HomeWindowSize.COMPACT -> selectedPanel = null
        }
    }

    GlassScreen(modifier) {
        val homeAlpha by animateFloatAsState(
            targetValue = if (windowOpen) 0.9f else 1f,
            animationSpec = tween(220),
            label = "home-dim"
        )
        HomeContent(
            state = state,
            onAddTask = onAddTask,
            onTaskToggle = onTaskToggle,
            onTaskClick = onTaskClick,
            onSeeAllTasks = onSeeAllTasks,
            onFocusClick = onFocusClick,
            onDeepSeekClick = onDeepSeekClick,
            onApiKeyFunClick = onApiKeyFunClick,
            onReviewSave = onReviewSave,
            onReviewArchive = onReviewArchive,
            onPanelClick = {
                windowSize = when (it) {
                    HomePanel.FOCUS, HomePanel.AI_RESOURCES -> HomeWindowSize.COMPACT
                    else -> HomeWindowSize.WINDOWED
                }
                selectedPanel = it
            },
            modifier = Modifier
                .hazeSource(modalHazeState)
                .alpha(homeAlpha)
        )

        val dark = isSystemInDarkTheme()
        val scrim by animateColorAsState(
            targetValue = if (windowOpen) {
                Color.Black.copy(alpha = if (dark) 0.14f else 0.08f)
            } else {
                Color.Transparent
            },
            animationSpec = tween(220),
            label = "home-window-scrim"
        )
        if (windowOpen || renderedPanel != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(scrim)
                    .pointerInput(windowOpen) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                            }
                        }
                    }
                    .testTag("home_window_scrim")
            )
        }

        AnimatedVisibility(
            visible = windowOpen,
            enter = fadeIn(tween(180)) + scaleIn(
                initialScale = 0.86f,
                transformOrigin = TransformOrigin(0.5f, renderedPanel?.transformOriginY ?: 0.5f),
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ),
            exit = fadeOut(tween(180)) + scaleOut(
                targetScale = 0.86f,
                transformOrigin = TransformOrigin(0.5f, renderedPanel?.transformOriginY ?: 0.5f),
                animationSpec = tween(260, easing = FastOutSlowInEasing)
            )
        ) {
            renderedPanel?.let { panel ->
                ExpandedHomeWindow(
                    panel = panel,
                    state = state,
                    windowSize = windowSize,
                    hazeState = modalHazeState,
                    onClose = {
                        windowSize = HomeWindowSize.WINDOWED
                        selectedPanel = null
                    },
                    onMinimize = {
                        when (windowSize) {
                            HomeWindowSize.FULLSCREEN -> windowSize = HomeWindowSize.WINDOWED
                            HomeWindowSize.WINDOWED -> windowSize = HomeWindowSize.COMPACT
                            HomeWindowSize.COMPACT -> selectedPanel = null
                        }
                    },
                    onExpand = {
                        windowSize = when (windowSize) {
                            HomeWindowSize.COMPACT -> HomeWindowSize.WINDOWED
                            HomeWindowSize.WINDOWED -> HomeWindowSize.FULLSCREEN
                            HomeWindowSize.FULLSCREEN -> HomeWindowSize.FULLSCREEN
                        }
                    },
                    onTaskToggle = onTaskToggle,
                    onTaskClick = onTaskClick,
                    onSeeAllTasks = onSeeAllTasks,
                    onFocusClick = {
                        windowSize = HomeWindowSize.WINDOWED
                        selectedPanel = null
                        onFocusClick()
                    },
                    onDeepSeekClick = onDeepSeekClick,
                    onApiKeyFunClick = onApiKeyFunClick,
                    onReviewSave = onReviewSave,
                    onReviewArchive = onReviewArchive
                )
            }
        }
    }
}

@Composable
private fun HomeContent(
    state: WorkbenchUiState,
    onAddTask: () -> Unit,
    onTaskToggle: (Task) -> Unit,
    onTaskClick: (Task) -> Unit,
    onSeeAllTasks: () -> Unit,
    onFocusClick: () -> Unit,
    onDeepSeekClick: () -> Unit,
    onApiKeyFunClick: () -> Unit,
    onReviewSave: (String) -> Unit,
    onReviewArchive: () -> Unit,
    onPanelClick: (HomePanel) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(30.dp)
    ) {
        item { LargeTitleHeader(state) }
        item { TodayOverview(state, onClick = { onPanelClick(HomePanel.OVERVIEW) }) }
        item { NewTaskStrip(onAddTask) }
        item {
            TodayTasks(
                state = state,
                onTaskToggle = onTaskToggle,
                onTaskClick = onTaskClick,
                onSeeAll = onSeeAllTasks,
                onPanelClick = { onPanelClick(HomePanel.TASKS) }
            )
        }
        item { FocusSurface(state.activeFocus, onClick = { onPanelClick(HomePanel.FOCUS) }) }
        item { AiResources(state, onClick = { onPanelClick(HomePanel.AI_RESOURCES) }) }
        item { DailyReview(state.todayReview?.note.orEmpty(), onClick = { onPanelClick(HomePanel.REVIEW) }) }
    }
}

@Composable
private fun LargeTitleHeader(state: WorkbenchUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = stringResource(R.string.home_kicker),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.workbench_section_today),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = state.dateText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TodayOverview(state: WorkbenchUiState, onClick: () -> Unit) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth().testTag("home_panel_overview"),
        shape = GroupShape,
        onClick = onClick
    ) {
        TodayOverviewContent(state, Modifier.padding(20.dp))
    }
}

@Composable
private fun TodayOverviewContent(state: WorkbenchUiState, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = LocalTime.now()
        }
    }
    val progress = if (state.todayTaskCount == 0) 0f else state.completedTaskCount.toFloat() / state.todayTaskCount
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.home_today_overview), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    now.format(DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.home_today_recorded_cost), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AnimatedContent(
                    targetState = if (state.hasRecordedAiUsage) "¥%.2f".format(state.todayRecordedAiCost) else "--",
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
                    label = "today-ai-cost"
                ) { value ->
                    Text(
                        text = value,
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum")
                    )
                }
                Text(stringResource(R.string.home_local_usage_source), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.64f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            OverviewMetric("${state.completedTaskCount}/${state.todayTaskCount}", stringResource(R.string.home_metric_tasks))
            OverviewMetric(state.todayFocusMinutes.toString(), stringResource(R.string.home_metric_focus_minutes))
        }
    }
}

@Composable
private fun OverviewMetric(value: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum"))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NewTaskStrip(onClick: () -> Unit) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(19.dp),
        blurRadius = 18.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(modifier = Modifier.size(34.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(19.dp))
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("新建任务", style = MaterialTheme.typography.titleMedium)
                Text("TODAY TASK", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun TodayTasks(
    state: WorkbenchUiState,
    onTaskToggle: (Task) -> Unit,
    onTaskClick: (Task) -> Unit,
    onSeeAll: () -> Unit,
    onPanelClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = stringResource(R.string.home_next_action),
            caption = null,
            meta = "${state.completedTaskCount}/${state.todayTaskCount}",
            action = stringResource(R.string.tasks_filter_all),
            onAction = onSeeAll
        )
        Spacer(Modifier.height(12.dp))
        GlassSurface(
            modifier = Modifier.fillMaxWidth().testTag("home_panel_tasks"),
            shape = GroupShape,
            onClick = onPanelClick
        ) {
            TaskListContent(state.todayTasks.take(3), onTaskToggle, onTaskClick)
        }
    }
}

@Composable
private fun TaskListContent(tasks: List<Task>, onTaskToggle: (Task) -> Unit, onTaskClick: (Task) -> Unit) {
    if (tasks.isEmpty()) {
        Text(
            text = stringResource(R.string.home_no_tasks),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 26.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Column {
            tasks.forEachIndexed { index, task ->
                TaskRow(task, onTaskToggle, onTaskClick)
                if (index < tasks.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(start = 58.dp), color = LocalWorkbenchColors.current.border)
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: Task, onToggle: (Task) -> Unit, onClick: (Task) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { onClick(task) }.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (task.status == TaskStatus.DONE) MaterialTheme.colorScheme.primary else Color.Transparent)
                .border(
                    width = 1.5.dp,
                    color = if (task.status == TaskStatus.DONE) MaterialTheme.colorScheme.primary else LocalWorkbenchColors.current.borderStrong,
                    shape = CircleShape
                )
                .clickable { onToggle(task) },
            contentAlignment = Alignment.Center
        ) {
            if (task.status == TaskStatus.DONE) {
                Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.action_complete), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(17.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = task.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (task.status == TaskStatus.DONE) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        task.estimateMinutes?.let {
            Text(stringResource(R.string.focus_minutes_value, it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = LocalWorkbenchColors.current.tertiaryText)
    }
}

@Composable
private fun FocusSurface(session: FocusSession?, onClick: () -> Unit) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth().animateContentSize().testTag("home_panel_focus"),
        shape = GroupShape,
        onClick = onClick
    ) {
        FocusContent(session)
    }
}

@Composable
private fun FocusContent(session: FocusSession?, onOpenFocus: (() -> Unit)? = null) {
    Column(
        modifier = Modifier.then(
            if (onOpenFocus != null) {
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenFocus)
                    .testTag("home_focus_open")
            } else {
                Modifier
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(modifier = Modifier.size(46.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (session?.status) {
                        FocusStatus.PAUSED -> stringResource(R.string.home_focus_paused)
                        FocusStatus.RUNNING -> stringResource(R.string.home_focus_running)
                        else -> stringResource(R.string.home_focus_start)
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = session?.let {
                        val remaining = ceil(it.remainingMillis(System.currentTimeMillis()) / 60_000.0).toInt().coerceAtLeast(0)
                        stringResource(R.string.workbench_focus_remaining, remaining, it.plannedMinutes)
                    } ?: stringResource(R.string.workbench_focus_ready),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AiResources(state: WorkbenchUiState, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = stringResource(R.string.workbench_section_ai),
            caption = null,
            meta = null
        )
        Spacer(Modifier.height(12.dp))
        GlassSurface(
            modifier = Modifier.fillMaxWidth().testTag("home_panel_ai"),
            shape = GroupShape,
            onClick = onClick
        ) {
            ProviderList(state, onDeepSeekClick = null, onApiKeyFunClick = null)
        }
    }
}

@Composable
private fun ProviderList(
    state: WorkbenchUiState,
    onDeepSeekClick: (() -> Unit)?,
    onApiKeyFunClick: (() -> Unit)?
) {
    Column {
        ProviderRow(ProviderBrand.DEEPSEEK, stringResource(R.string.deepseek_account), state.deepSeekAccount, onDeepSeekClick)
        HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = LocalWorkbenchColors.current.border)
        ProviderRow(ProviderBrand.APIKEY_FUN, stringResource(R.string.apikey_fun_account), state.apiKeyFunAccount, onApiKeyFunClick)
    }
}

@Composable
private fun ProviderRow(provider: ProviderBrand, name: String, cache: AccountCache, onClick: (() -> Unit)?) {
    val configured = cache.lastUpdated > 0
    val status = when {
        cache.errorMessage.isNotBlank() -> stringResource(R.string.status_error)
        !configured -> stringResource(R.string.not_configured)
        cache.isAvailable -> stringResource(R.string.status_available)
        else -> stringResource(R.string.status_unavailable)
    }
    val modifier = Modifier.fillMaxWidth().heightIn(min = 82.dp)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 16.dp)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        ProviderIdentity(provider)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = if (configured) "${currencySymbol(cache.currency)}${cache.totalBalance.ifBlank { "--" }}" else "--",
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum")
        )
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = LocalWorkbenchColors.current.tertiaryText)
    }
}

@Composable
private fun DailyReview(note: String, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = stringResource(R.string.workbench_section_review),
            caption = null,
            meta = null
        )
        Spacer(Modifier.height(12.dp))
        GlassSurface(
            modifier = Modifier.fillMaxWidth().testTag("home_panel_review"),
            shape = GroupShape,
            onClick = onClick
        ) {
            Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = note.ifBlank { stringResource(R.string.workbench_review_hint) },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (note.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = LocalWorkbenchColors.current.tertiaryText)
            }
        }
    }
}

@Composable
private fun ExpandedHomeWindow(
    panel: HomePanel,
    state: WorkbenchUiState,
    windowSize: HomeWindowSize,
    hazeState: HazeState,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onExpand: () -> Unit,
    onTaskToggle: (Task) -> Unit,
    onTaskClick: (Task) -> Unit,
    onSeeAllTasks: () -> Unit,
    onFocusClick: () -> Unit,
    onDeepSeekClick: () -> Unit,
    onApiKeyFunClick: () -> Unit,
    onReviewSave: (String) -> Unit,
    onReviewArchive: () -> Unit
) {
    val widthFraction by animateFloatAsState(
        targetValue = when (windowSize) {
            HomeWindowSize.COMPACT -> 0.86f
            HomeWindowSize.WINDOWED -> 0.92f
            HomeWindowSize.FULLSCREEN -> 1f
        },
        animationSpec = tween(360, easing = FastOutSlowInEasing),
        label = "window-width"
    )
    val heightFraction by animateFloatAsState(
        targetValue = when (windowSize) {
            HomeWindowSize.COMPACT -> 0.46f
            HomeWindowSize.WINDOWED -> 0.7f
            HomeWindowSize.FULLSCREEN -> 1f
        },
        animationSpec = tween(360, easing = FastOutSlowInEasing),
        label = "window-height"
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (windowSize == HomeWindowSize.FULLSCREEN) 0.dp else 28.dp,
        animationSpec = tween(360, easing = FastOutSlowInEasing),
        label = "window-radius"
    )
    Box(
        modifier = Modifier.fillMaxSize().testTag("home_expanded_window"),
        contentAlignment = Alignment.Center
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth(widthFraction)
                .fillMaxHeight(heightFraction)
                .testTag("home_window_${windowSize.name.lowercase()}"),
            shape = RoundedCornerShape(cornerRadius),
            blurRadius = 24.dp,
            hazeStateOverride = hazeState,
            glassTint = LocalWorkbenchColors.current.glassSurface.copy(
                alpha = if (isSystemInDarkTheme()) 0.16f else 0.1f
            )
        ) {
            Column(Modifier.fillMaxSize()) {
                WindowHeader(panel.title, windowSize, onClose, onMinimize, onExpand)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp), color = LocalWorkbenchColors.current.border)
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    when (panel) {
                        HomePanel.OVERVIEW -> {
                            TodayOverviewContent(state)
                            HorizontalDivider(color = LocalWorkbenchColors.current.border)
                            ProviderList(state, onDeepSeekClick, onApiKeyFunClick)
                        }
                        HomePanel.TASKS -> {
                            TaskListContent(state.todayTasks, onTaskToggle, onTaskClick)
                            TextButton(onClick = onSeeAllTasks, modifier = Modifier.align(Alignment.End)) { Text("查看全部任务") }
                        }
                        HomePanel.FOCUS -> FocusContent(state.activeFocus, onOpenFocus = onFocusClick)
                        HomePanel.AI_RESOURCES -> ProviderList(state, onDeepSeekClick, onApiKeyFunClick)
                        HomePanel.REVIEW -> ReviewEditor(state.todayReview?.note.orEmpty(), onReviewSave, onReviewArchive)
                    }
                }
            }
        }
    }
}

@Composable
private fun WindowHeader(
    title: String,
    windowSize: HomeWindowSize,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onExpand: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WindowDot(Color(0xFFFF5F57), "关闭", "close", onClose)
        WindowDot(Color(0xFFFFBD2E), "缩小", "minimize", onMinimize)
        WindowDot(
            color = Color(0xFF28C840),
            description = if (windowSize == HomeWindowSize.FULLSCREEN) "已全屏" else "放大",
            tag = "expand",
            onClick = onExpand,
            enabled = windowSize != HomeWindowSize.FULLSCREEN
        )
        Spacer(Modifier.weight(1f))
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(12.dp))
    }
}

@Composable
private fun WindowDot(
    color: Color,
    description: String,
    tag: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .alpha(if (enabled) 1f else 0.34f)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag("window_dot_$tag"),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
    }
}

@Composable
private fun ReviewEditor(initialNote: String, onSave: (String) -> Unit, onArchive: () -> Unit) {
    var note by rememberSaveable(initialNote) { mutableStateOf(initialNote) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onArchive) { Text("ARCHIVE") }
        }
        TextField(
            value = note,
            onValueChange = { note = it.take(4000) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.workbench_review_hint)) },
            minLines = 6,
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
        TextButton(onClick = { onSave(note.trim()) }, enabled = note.isNotBlank(), modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.action_save), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    caption: String?,
    meta: String?,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            caption?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        meta?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action, fontWeight = FontWeight.SemiBold) }
    }
}

private fun currencySymbol(currency: String): String = when (currency.uppercase()) {
    "CNY", "RMB" -> "¥"
    "EUR" -> "€"
    else -> "$"
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomePreview() {
    WorkbenchTheme(darkTheme = true) {
        HomeScreen(
            state = WorkbenchUiState(
                dateText = "8月7日 · 周五",
                greeting = "下午好",
                todayTasks = listOf(previewTask()),
                deepSeekAccount = AccountCache("82.30", currency = "CNY", isAvailable = true, lastUpdated = 1),
                apiKeyFunAccount = AccountCache("16.42", currency = "USD", isAvailable = true, lastUpdated = 1),
                todayTaskCount = 3,
                completedTaskCount = 1,
                todayFocusMinutes = 50,
                todayRecordedAiCost = 2.36,
                hasRecordedAiUsage = true,
                isLoading = false
            ),
            onAddTask = {}, onTaskToggle = {}, onTaskClick = {}, onSeeAllTasks = {}, onFocusClick = {},
            onDeepSeekClick = {}, onApiKeyFunClick = {}, onReviewSave = {}, onReviewArchive = {}
        )
    }
}

private fun previewTask() = Task(
    id = 1,
    title = "整理今天最重要的三个交付项",
    notes = "",
    projectId = null,
    status = TaskStatus.PLANNED,
    priority = TaskPriority.MEDIUM,
    plannedDate = "2026-08-07",
    dueAt = null,
    reminderAt = null,
    estimateMinutes = 30,
    sortOrder = 0L,
    sourceType = TaskSourceType.MANUAL,
    sourceUrl = null,
    createdAt = 0,
    updatedAt = 0,
    completedAt = null
)
