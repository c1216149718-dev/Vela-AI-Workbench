package com.deepseek.widget.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * 全局唯一的 Preferences DataStore 实例（落盘文件 deepseek_widget_prefs.preferences_pb）。
 *
 * AppPreferences 与 ApiKeyFunProfileStore 必须共用这一个委托，绝不可各自再声明
 * `preferencesDataStore(name = "deepseek_widget_prefs")`。`preferencesDataStore` 委托
 * 按"委托实例"缓存 DataStore 对象，两个独立声明会产生两个指向同一文件的 DataStore 实例，
 * 运行时同时活跃会抛：
 *   IllegalStateException: There are multiple DataStores active for the same file:
 *   .../deepseek_widget_prefs.preferences_pb
 * 合并为单一顶层扩展后，相同 Context 永远拿到同一个实例，彻底消除该冲突。
 */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "deepseek_widget_prefs")
