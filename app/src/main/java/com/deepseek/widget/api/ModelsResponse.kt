package com.deepseek.widget.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from GET /models
 */
@Serializable
data class ModelsResponse(
    val data: List<ModelInfo>
)

@Serializable
data class ModelInfo(
    val id: String,
    val `object`: String = "model",
    @SerialName("owned_by")
    val ownedBy: String = ""
)
