package com.deepseek.widget.data.provider

object ProviderRegistry {
    val DEEPSEEK = ProviderId("deepseek")
    val APIKEY_FUN = ProviderId("apikey_fun")
    val SILICON_FLOW = ProviderId("siliconflow")
    val MOONSHOT = ProviderId("moonshot")
    val ZHIPU = ProviderId("zhipu")
    val BAILIAN = ProviderId("bailian")
    val ARK = ProviderId("volcengine_ark")
    val TOKENHUB = ProviderId("tencent_tokenhub")
    val HUNYUAN_LEGACY = ProviderId("tencent_hunyuan")
    val QIANFAN = ProviderId("baidu_qianfan")
    val OPENAI = ProviderId("openai")
    val CUSTOM = ProviderId("custom")

    private val apiKey = listOf(CredentialField("api_key", "API Key", CredentialKind.API_KEY))
    private val optionalAkSk = listOf(
        CredentialField("access_key", "Access Key（可选）", CredentialKind.ACCESS_KEY, required = false),
        CredentialField("secret_key", "Secret Key（可选）", CredentialKind.SECRET_KEY, required = false)
    )

    val descriptors: List<ProviderDescriptor> = listOf(
        ProviderDescriptor(
            DEEPSEEK,
            "DeepSeek",
            setOf(ProviderCapability.CONNECTION, ProviderCapability.BALANCE, ProviderCapability.ESTIMATED_COST, ProviderCapability.BILL_IMPORT),
            apiKey,
            "https://api.deepseek.com/user/balance",
            "历史用量需导入官方月度 CSV；余额差仅作为估算",
            officialDocs = "https://api-docs.deepseek.com/api/get-user-balance/",
            legacyIds = setOf("DEEPSEEK")
        ),
        ProviderDescriptor(
            APIKEY_FUN,
            "APIKEY.FUN",
            setOf(ProviderCapability.CONNECTION, ProviderCapability.BALANCE, ProviderCapability.ACTUAL_COST, ProviderCapability.REQUESTS, ProviderCapability.TOKENS, ProviderCapability.MODELS, ProviderCapability.MULTI_KEY, ProviderCapability.HISTORICAL_USAGE),
            apiKey,
            "https://api.apikey.fun/v1/models",
            "使用已验证的 /v1/usage 契约；响应结构变化时保留旧缓存",
            legacyIds = setOf("APIKEY_FUN")
        ),
        ProviderDescriptor(
            SILICON_FLOW,
            "SiliconFlow",
            setOf(ProviderCapability.CONNECTION, ProviderCapability.BALANCE, ProviderCapability.ESTIMATED_COST, ProviderCapability.BILL_IMPORT),
            apiKey,
            "https://api.siliconflow.cn/v1/user/info",
            "官方未公开历史账单 API",
            officialDocs = "https://docs.siliconflow.cn/en/release-notes/overview"
        ),
        ProviderDescriptor(
            MOONSHOT,
            "Moonshot / Kimi",
            setOf(ProviderCapability.CONNECTION, ProviderCapability.BALANCE, ProviderCapability.ESTIMATED_COST, ProviderCapability.BILL_IMPORT),
            apiKey + CredentialField("region", "端点区域", CredentialKind.REGION, required = false, secret = false),
            "https://api.moonshot.cn/v1/users/me/balance",
            "国内与国际账号互不相通；历史费用需账单导入",
            officialDocs = "https://platform.kimi.com/docs/api/balance"
        ),
        ProviderDescriptor(
            ZHIPU,
            "智谱 GLM",
            setOf(ProviderCapability.CONNECTION, ProviderCapability.MODELS, ProviderCapability.BILL_IMPORT),
            apiKey,
            "https://open.bigmodel.cn/api/paas/v4/models",
            "普通 API Key 不提供历史账单与余额接口",
            officialDocs = "https://docs.bigmodel.cn/cn/faq/fee-issues"
        ),
        ProviderDescriptor(
            BAILIAN,
            "阿里百炼",
            setOf(ProviderCapability.CONNECTION, ProviderCapability.BALANCE, ProviderCapability.ACTUAL_COST, ProviderCapability.REQUESTS, ProviderCapability.TOKENS, ProviderCapability.MODELS, ProviderCapability.HISTORICAL_USAGE, ProviderCapability.CLOUD_ACCOUNT_BALANCE, ProviderCapability.BILL_IMPORT),
            apiKey + optionalAkSk + listOf(
                CredentialField("monitor_endpoint", "Prometheus HTTP API（可选）", CredentialKind.ENDPOINT, required = false, secret = false),
                CredentialField("workspace_id", "Workspace ID（可选）", CredentialKind.WORKSPACE_ID, required = false, secret = false)
            ),
            "https://dashscope.aliyuncs.com/compatible-mode/v1/models",
            "用量监控仅作参考；费用以 BSS 账单为准",
            officialDocs = "https://help.aliyun.com/en/model-studio/model-telemetry"
        ),
        ProviderDescriptor(
            ARK,
            "火山方舟",
            setOf(ProviderCapability.CONNECTION, ProviderCapability.BALANCE, ProviderCapability.ACTUAL_COST, ProviderCapability.REQUESTS, ProviderCapability.TOKENS, ProviderCapability.MODELS, ProviderCapability.HISTORICAL_USAGE, ProviderCapability.CLOUD_ACCOUNT_BALANCE, ProviderCapability.BILL_IMPORT),
            apiKey + optionalAkSk + listOf(
                CredentialField("region", "Region（可选）", CredentialKind.REGION, required = false, secret = false),
                CredentialField("project_id", "Project ID（可选）", CredentialKind.PROJECT_ID, required = false, secret = false)
            ),
            "https://ark.cn-beijing.volces.com/api/v3/models",
            "历史用量与费用中心需要额外配置 AK/SK",
            officialDocs = "https://www.volcengine.com/docs/82379/2116766?lang=zh"
        ),
        ProviderDescriptor(
            TOKENHUB,
            "腾讯 TokenHub",
            setOf(ProviderCapability.CONNECTION, ProviderCapability.BALANCE, ProviderCapability.ACTUAL_COST, ProviderCapability.REQUESTS, ProviderCapability.TOKENS, ProviderCapability.MODELS, ProviderCapability.HISTORICAL_USAGE, ProviderCapability.CLOUD_ACCOUNT_BALANCE, ProviderCapability.BILL_IMPORT),
            apiKey + listOf(
                CredentialField("secret_id", "SecretId（可选）", CredentialKind.ACCESS_KEY, required = false),
                CredentialField("secret_key", "SecretKey（可选）", CredentialKind.SECRET_KEY, required = false),
                CredentialField("region", "Region（可选）", CredentialKind.REGION, required = false, secret = false),
                CredentialField("team_id", "Token Plan Team ID（可选）", CredentialKind.TEAM_ID, required = false, secret = false)
            ),
            "https://tokenhub.tencentmaas.com/v1/models",
            "旧混元配置只读保留；TokenHub 管控与账单需要 SecretId/SecretKey",
            officialDocs = "https://cloud.tencent.com/document/product/1823/130088"
        ),
        ProviderDescriptor(
            QIANFAN,
            "百度千帆",
            setOf(ProviderCapability.CONNECTION, ProviderCapability.BALANCE, ProviderCapability.ACTUAL_COST, ProviderCapability.REQUESTS, ProviderCapability.TOKENS, ProviderCapability.MODELS, ProviderCapability.HISTORICAL_USAGE, ProviderCapability.CLOUD_ACCOUNT_BALANCE, ProviderCapability.BILL_IMPORT),
            apiKey.map { it.copy(required = false) } + listOf(
                CredentialField("access_key", "BCE Access Key", CredentialKind.ACCESS_KEY),
                CredentialField("secret_key", "BCE Secret Key", CredentialKind.SECRET_KEY)
            ),
            null,
            "服务指标、财务账单与账户余额均使用 BCE AK/SK",
            officialDocs = "https://cloud.baidu.com/doc/qianfan-api/s/4mm33t0kj"
        ),
        ProviderDescriptor(
            OPENAI,
            "OpenAI",
            setOf(ProviderCapability.CONNECTION, ProviderCapability.ACTUAL_COST, ProviderCapability.REQUESTS, ProviderCapability.TOKENS, ProviderCapability.MODELS, ProviderCapability.HISTORICAL_USAGE),
            listOf(
                CredentialField("admin_key", "Admin Key", CredentialKind.ADMIN_KEY),
                CredentialField("organization_id", "Organization ID（可选）", CredentialKind.ORGANIZATION_ID, required = false, secret = false),
                CredentialField("project_id", "Project ID（可选）", CredentialKind.PROJECT_ID, required = false, secret = false)
            ),
            "https://api.openai.com/v1/models",
            "组织用量与 Costs API 需要 Admin Key；官方无余额接口",
            officialDocs = "https://platform.openai.com/docs/api-reference/usage/audio_transcriptions_object"
        ),
        ProviderDescriptor(
            CUSTOM,
            "自定义 API",
            setOf(ProviderCapability.CONNECTION),
            apiKey,
            null,
            "只展示测试通过且字段映射有效的能力",
            supportsCustomBaseUrl = true
        )
    )

    val presetDescriptors: List<ProviderDescriptor> = descriptors.filterNot { it.id == CUSTOM }

    private val legacyHunyuanDescriptor = ProviderDescriptor(
        HUNYUAN_LEGACY,
        "腾讯混元（旧版只读）",
        setOf(ProviderCapability.CONNECTION),
        emptyList(),
        null,
        "旧配置与历史数据仅只读保留，不迁移为 TokenHub"
    )

    fun descriptor(id: String): ProviderDescriptor? = if (id == HUNYUAN_LEGACY.value) legacyHunyuanDescriptor else descriptors.firstOrNull { descriptor ->
        descriptor.id.value == id || id in descriptor.legacyIds
    }

    fun canonicalId(raw: String): ProviderId? {
        val normalized = raw.trim()
        return descriptor(normalized)?.id
            ?: when (normalized.uppercase()) {
                "DEEPSEEK" -> DEEPSEEK
                "APIKEY_FUN" -> APIKEY_FUN
                "TENCENT_HUNYUAN" -> HUNYUAN_LEGACY
                else -> null
            }
    }
}
