package com.deepseek.widget.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "provider_balance_snapshots",
    primaryKeys = ["providerId", "credentialId", "capturedAt", "currency"]
)
data class ProviderBalanceSnapshotEntity(
    val providerId: String,
    val credentialId: String,
    val capturedAt: Long,
    val currency: String,
    val amount: String,
    val isEstimated: Boolean,
    val provenance: String = if (isEstimated) "BALANCE_DELTA_ESTIMATE" else "EXACT_API",
    val accountFingerprint: String = "",
    val isCloudAccount: Boolean = false
)
