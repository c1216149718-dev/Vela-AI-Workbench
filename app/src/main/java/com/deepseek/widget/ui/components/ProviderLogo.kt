package com.deepseek.widget.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.deepseek.widget.R
import com.deepseek.widget.data.provider.ProviderId
import com.deepseek.widget.data.provider.ProviderRegistry
import com.deepseek.widget.ui.theme.LocalWorkbenchColors

/** Bundled official marks. Marks keep original ratio and brand color inside one optical frame. */
object ProviderLogoManifest {
    @DrawableRes
    fun drawable(providerId: ProviderId): Int = when (providerId) {
        ProviderRegistry.DEEPSEEK -> R.drawable.provider_deepseek
        ProviderRegistry.APIKEY_FUN -> R.drawable.provider_apikey_fun
        ProviderRegistry.SILICON_FLOW -> R.drawable.provider_siliconflow
        ProviderRegistry.MOONSHOT -> R.drawable.provider_moonshot
        ProviderRegistry.ZHIPU -> R.drawable.provider_zhipu
        ProviderRegistry.BAILIAN -> R.drawable.provider_bailian
        ProviderRegistry.ARK -> R.drawable.provider_volcengine
        ProviderRegistry.TOKENHUB -> R.drawable.provider_tokenhub
        ProviderRegistry.QIANFAN -> R.drawable.provider_qianfan
        ProviderRegistry.OPENAI -> R.drawable.provider_openai
        ProviderRegistry.CUSTOM -> R.drawable.ic_drawer_data_sources
        else -> R.drawable.ic_drawer_data_sources
    }
}

@Composable
fun ProviderLogo(providerId: ProviderId, name: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(15.dp)
    Box(
        modifier = modifier.size(48.dp).clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(0.8.dp, LocalWorkbenchColors.current.border, shape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(ProviderLogoManifest.drawable(providerId)),
            contentDescription = if (providerId == ProviderRegistry.CUSTOM) "$name 连接图标" else "$name 官方图标",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(30.dp)
        )
    }
}
