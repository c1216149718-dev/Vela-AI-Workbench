package com.deepseek.widget.data.provider

import java.math.BigDecimal
import java.time.LocalDate

@JvmInline
value class ProviderId(val value: String) {
    init { require(value.matches(Regex("[a-z0-9_\\-]+"))) { "Invalid provider id" } }
    override fun toString(): String = value
}

enum class ProviderCapability(val label: String) {
    CONNECTION("连接"), BALANCE("余额"), ACTUAL_COST("实扣"), ESTIMATED_COST("估算"),
    REQUESTS("请求"), TOKENS("Token"), MODELS("模型"), MULTI_KEY("多 Key"),
    HISTORICAL_USAGE("历史用量"), BILL_IMPORT("账单导入"), CLOUD_ACCOUNT_BALANCE("云账户余额")
}

enum class CredentialKind {
    API_KEY, ADMIN_KEY, ACCESS_KEY, SECRET_KEY, ORGANIZATION_ID, PROJECT_ID,
    WORKSPACE_ID, REGION, TEAM_ID, ENDPOINT
}

data class CredentialField(
    val id: String,
    val label: String,
    val kind: CredentialKind,
    val required: Boolean = true,
    val secret: Boolean = true,
    val help: String = ""
)

data class ProviderDescriptor(
    val id: ProviderId,
    val displayName: String,
    val capabilities: Set<ProviderCapability>,
    val credentials: List<CredentialField>,
    val testUrl: String?,
    val limitation: String? = null,
    val supportsCustomBaseUrl: Boolean = false,
    val officialDocs: String? = null,
    val legacyIds: Set<String> = emptySet()
)

enum class CustomConnectorMode { SIMPLE, ADVANCED }
enum class HttpMethod { GET, POST }
enum class PaginationMode { NONE, CURSOR, PAGE }

data class JsonFieldMapping(
    val listPath: String = "",
    val dateField: String = "",
    val modelField: String = "",
    val valueField: String = "",
    val costField: String = "",
    val currencyField: String = "",
    val requestsField: String = "",
    val inputTokensField: String = "",
    val outputTokensField: String = "",
    val cachedTokensField: String = "",
    val totalTokensField: String = "",
    val cursorField: String = ""
)

data class CustomEndpointConfig(
    val url: String = "",
    val method: HttpMethod = HttpMethod.GET,
    val queryTemplate: String = "",
    val bodyTemplate: String = "",
    val mapping: JsonFieldMapping = JsonFieldMapping(),
    val pagination: PaginationMode = PaginationMode.NONE,
    val cursorParameter: String = "cursor",
    val pageParameter: String = "page"
)

/** Script-free multi-endpoint connector configuration. */
data class CustomConnectorConfig(
    val mode: CustomConnectorMode = CustomConnectorMode.SIMPLE,
    val baseUrl: String = "",
    val authHeader: String = "Authorization",
    val authPrefix: String = "Bearer ",
    val organizationHeader: String? = null,
    val projectHeader: String? = null,
    val timezone: String = "UTC",
    val connection: CustomEndpointConfig = CustomEndpointConfig(url = "/models"),
    val balance: CustomEndpointConfig? = null,
    val dailyUsage: CustomEndpointConfig? = null,
    val modelUsage: CustomEndpointConfig? = null,
    val actualCost: CustomEndpointConfig? = null
)

enum class MetricProvenance { EXACT_API, EXACT_IMPORT, LOCAL_CAPTURE, BALANCE_DELTA_ESTIMATE }
enum class SyncErrorType { AUTH, PERMISSION, RATE_LIMIT, TIMEOUT, INVALID_RESPONSE, NETWORK, UNKNOWN }

data class ProviderBalance(
    val currency: String,
    val amount: BigDecimal,
    val accountFingerprint: String = "",
    val cloudAccount: Boolean = false,
    val provenance: MetricProvenance = MetricProvenance.EXACT_API
)

data class DailyUsagePoint(
    val date: LocalDate,
    val model: String = "__all__",
    val currency: String = "",
    val cost: BigDecimal = BigDecimal.ZERO,
    val requests: Long? = null,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val cachedTokens: Long? = null,
    val totalTokens: Long? = null,
    val provenance: MetricProvenance = MetricProvenance.EXACT_API,
    val sourceId: String = "api"
)

data class ModelUsagePoint(
    val model: String,
    val currency: String = "",
    val cost: BigDecimal = BigDecimal.ZERO,
    val requests: Long? = null,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val cachedTokens: Long? = null,
    val totalTokens: Long? = null,
    val provenance: MetricProvenance = MetricProvenance.EXACT_API,
    val sourceId: String = "api"
)

data class ActualCostPoint(
    val date: LocalDate,
    val currency: String,
    val amount: BigDecimal,
    val lineItem: String = "",
    val provenance: MetricProvenance = MetricProvenance.EXACT_API,
    val sourceId: String = "billing"
)

data class SyncPage<T>(
    val items: List<T>,
    val nextCursor: String? = null,
    val refreshedAt: Long = System.currentTimeMillis()
)

data class BillImportPayload(val fileName: String, val bytes: ByteArray, val sha256: String)

data class BillImportPreview(
    val providerId: ProviderId,
    val currency: Set<String>,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val records: List<DailyUsagePoint>,
    val warnings: List<String> = emptyList(),
    val duplicate: Boolean = false
)

sealed interface ProviderResult<out T> {
    data class Supported<T>(val value: T) : ProviderResult<T>
    data class Unsupported(val capability: ProviderCapability, val reason: String) : ProviderResult<Nothing>
    data class PermissionRequired(val capability: ProviderCapability, val reason: String) : ProviderResult<Nothing>
    data class PartialFailure<T>(val value: T, val message: String, val errorType: SyncErrorType = SyncErrorType.UNKNOWN) : ProviderResult<T>
    data class Failure(
        val message: String,
        val httpCode: Int? = null,
        val retryable: Boolean = false,
        val errorType: SyncErrorType = SyncErrorType.UNKNOWN
    ) : ProviderResult<Nothing>
}

interface ProviderConnector {
    val descriptor: ProviderDescriptor

    suspend fun testConnection(credentials: Map<String, String>, config: CustomConnectorConfig? = null): ProviderResult<Unit>

    suspend fun syncBalance(credentials: Map<String, String>): ProviderResult<List<ProviderBalance>> =
        ProviderResult.Unsupported(ProviderCapability.BALANCE, "官方接口未提供余额")

    suspend fun syncDailyUsage(
        startDate: LocalDate,
        endDate: LocalDate,
        credentials: Map<String, String>,
        cursor: String? = null
    ): ProviderResult<SyncPage<DailyUsagePoint>> =
        ProviderResult.Unsupported(ProviderCapability.HISTORICAL_USAGE, "官方接口未提供历史用量")

    suspend fun syncModelUsage(
        startDate: LocalDate,
        endDate: LocalDate,
        credentials: Map<String, String>,
        cursor: String? = null
    ): ProviderResult<SyncPage<ModelUsagePoint>> =
        ProviderResult.Unsupported(ProviderCapability.MODELS, "官方接口未提供模型历史用量")

    suspend fun syncActualCost(
        startDate: LocalDate,
        endDate: LocalDate,
        credentials: Map<String, String>,
        cursor: String? = null
    ): ProviderResult<SyncPage<ActualCostPoint>> =
        ProviderResult.Unsupported(ProviderCapability.ACTUAL_COST, "官方接口未提供历史实扣")

    suspend fun importOfficialBill(payload: BillImportPayload): ProviderResult<BillImportPreview> =
        ProviderResult.Unsupported(ProviderCapability.BILL_IMPORT, "该来源暂无受支持的官方账单格式")
}
