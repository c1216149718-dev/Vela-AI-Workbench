package com.deepseek.widget.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.deepseek.widget.R

enum class VelaMotif {
    GENERIC,
    HOME,
    TASKS,
    INSIGHTS,
    SETTINGS,
    FOCUS,
    FOCUS_HISTORY,
    DAILY_WALL,
    DAILY_REFLECTION,
    SOURCE_CENTER,
    USAGE_DETAIL,
    KEY_MANAGEMENT
}

enum class VelaTitleRole { PAGE, SECTION }

enum class VelaTitle(
    @param:DrawableRes val drawableRes: Int,
    val spokenLabel: String,
    val role: VelaTitleRole
) {
    TODAY(R.drawable.vela_title_today, "今天", VelaTitleRole.PAGE),
    TASKS(R.drawable.vela_title_tasks, "任务", VelaTitleRole.PAGE),
    INSIGHTS(R.drawable.vela_title_insights, "洞察", VelaTitleRole.PAGE),
    SETTINGS(R.drawable.vela_title_settings, "设置", VelaTitleRole.PAGE),
    TOOLS(R.drawable.vela_title_tools, "工具", VelaTitleRole.PAGE),
    NEXT(R.drawable.vela_title_next, "下一步", VelaTitleRole.SECTION),
    DAILY_REFLECTION(R.drawable.vela_title_daily_reflection, "今日复盘", VelaTitleRole.SECTION),
    DATA_SOURCES(R.drawable.vela_title_data_sources, "数据源", VelaTitleRole.SECTION),
    WIDGET(R.drawable.vela_title_widget, "小组件", VelaTitleRole.SECTION),
    SOURCE_CENTER(R.drawable.vela_title_source_center, "数据源中心", VelaTitleRole.SECTION),
    CONNECTIONS_CREDENTIALS(R.drawable.vela_title_connections_credentials, "连接与凭据", VelaTitleRole.SECTION),
    FOCUS(R.drawable.vela_title_focus, "专注", VelaTitleRole.SECTION),
    FOCUS_HISTORY(R.drawable.vela_title_focus_history, "专注历史", VelaTitleRole.SECTION),
    DAILY_WALL(R.drawable.vela_title_daily_wall, "每日留言墙", VelaTitleRole.SECTION),
    USAGE_DETAIL(R.drawable.vela_title_usage_detail, "用量详情", VelaTitleRole.SECTION),
    KEY_MANAGEMENT(R.drawable.vela_title_key_management, "密钥管理", VelaTitleRole.SECTION)
}

/** Locked title roles: callers select content, never local sizing. */
@Composable
fun VelaEditorialHeader(
    title: VelaTitle,
    modifier: Modifier = Modifier
) {
    val aspectRatio = if (title.role == VelaTitleRole.PAGE) 5f else 7.5f
    val maxWidth = if (title.role == VelaTitleRole.PAGE) 520.dp else 560.dp
    Image(
        painter = painterResource(title.drawableRes),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        alignment = Alignment.CenterStart,
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = maxWidth)
            .aspectRatio(aspectRatio)
            .semantics(mergeDescendants = true) {
                heading()
                contentDescription = title.spokenLabel
            }
    )
}

@Composable
fun VelaSectionOrnament(
    motif: VelaMotif,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(motif.ornamentScene().divider),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        alignment = Alignment.CenterEnd,
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .alpha(0.72f)
    )
}

@Composable
internal fun VelaBackdropDecoration(
    motif: VelaMotif,
    modifier: Modifier = Modifier
) {
    val scene = motif.ornamentScene()
    Box(modifier = modifier) {
        Image(
            painter = painterResource(scene.header),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            alignment = Alignment.TopEnd,
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .height(132.dp)
                .align(Alignment.TopEnd)
                .alpha(0.72f)
        )
        Image(
            painter = painterResource(scene.footer),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            alignment = Alignment.BottomCenter,
            modifier = Modifier
                .fillMaxWidth()
                .height(118.dp)
                .align(Alignment.BottomCenter)
                .alpha(0.58f)
        )
    }
}

private data class OrnamentScene(
    @param:DrawableRes val header: Int,
    @param:DrawableRes val divider: Int,
    @param:DrawableRes val footer: Int
)

private fun VelaMotif.ornamentScene(): OrnamentScene = when (this) {
    VelaMotif.HOME,
    VelaMotif.FOCUS,
    VelaMotif.FOCUS_HISTORY,
    VelaMotif.DAILY_WALL,
    VelaMotif.DAILY_REFLECTION,
    VelaMotif.GENERIC -> OrnamentScene(
        R.drawable.vela_ornament_home_header,
        R.drawable.vela_ornament_home_divider,
        R.drawable.vela_ornament_home_footer
    )

    VelaMotif.TASKS -> OrnamentScene(
        R.drawable.vela_ornament_tasks_header,
        R.drawable.vela_ornament_tasks_divider,
        R.drawable.vela_ornament_tasks_footer
    )

    VelaMotif.SETTINGS -> OrnamentScene(
        R.drawable.vela_ornament_settings_header,
        R.drawable.vela_ornament_settings_divider,
        R.drawable.vela_ornament_settings_footer
    )

    VelaMotif.INSIGHTS,
    VelaMotif.SOURCE_CENTER,
    VelaMotif.USAGE_DETAIL,
    VelaMotif.KEY_MANAGEMENT -> OrnamentScene(
        R.drawable.vela_ornament_insights_header,
        R.drawable.vela_ornament_insights_divider,
        R.drawable.vela_ornament_insights_footer
    )
}
