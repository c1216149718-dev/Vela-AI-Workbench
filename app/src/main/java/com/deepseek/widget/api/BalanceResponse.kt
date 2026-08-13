package com.deepseek.widget.api

import kotlinx.serialization.Serializable

/**
 * Response from GET /user/balance
 */
@Serializable
data class BalanceResponse(
    val is_available: Boolean,
    val balance_infos: List<BalanceInfo>
)

@Serializable
data class BalanceInfo(
    val currency: String,
    val total_balance: String,
    val granted_balance: String,
    val topped_up_balance: String
)
