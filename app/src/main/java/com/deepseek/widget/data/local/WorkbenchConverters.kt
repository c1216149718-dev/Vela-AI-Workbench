package com.deepseek.widget.data.local

import androidx.room.TypeConverter

/**
 * Room 类型转换器。阶段 1 所有字段为 Room 原生支持类型，无需自定义转换。
 * 为 v2（AiUsageDailyEntity 的 BigDecimal-as-String）和 v3（ResourceItemEntity）预留。
 */
class WorkbenchConverters
