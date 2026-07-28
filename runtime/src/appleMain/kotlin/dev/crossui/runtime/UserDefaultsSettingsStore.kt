package dev.crossui.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSUserDefaults

/**
 * Stores shared KMP preferences in UserDefaults without coupling business
 * logic to SwiftUI property wrappers.
 */
class UserDefaultsSettingsStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : SettingsStore {
    private val settings = mutableMapOf<String, UserDefaultsEntry>()

    override fun boolean(key: SettingKey<Boolean>): Setting<Boolean> =
        userDefaultsSetting(
            key = key,
            type = "Boolean",
            read = { defaults.boolForKey(key.name) },
            write = { defaults.setBool(it, key.name) },
        )

    override fun string(key: SettingKey<String>): Setting<String> =
        userDefaultsSetting(
            key = key,
            type = "String",
            read = { defaults.stringForKey(key.name) ?: key.default },
            write = { defaults.setObject(it, key.name) },
        )

    override fun int(key: SettingKey<Int>): Setting<Int> =
        userDefaultsSetting(
            key = key,
            type = "Int",
            read = { defaults.integerForKey(key.name).toInt() },
            write = { defaults.setInteger(it.toLong(), key.name) },
        )

    override fun double(key: SettingKey<Double>): Setting<Double> =
        userDefaultsSetting(
            key = key,
            type = "Double",
            read = { defaults.doubleForKey(key.name) },
            write = { defaults.setDouble(it, key.name) },
        )

    private fun <Value> userDefaultsSetting(
        key: SettingKey<Value>,
        type: String,
        read: () -> Value,
        write: (Value) -> Unit,
    ): Setting<Value> {
        require(key.storage == SettingStorage.Preferences) {
            "UserDefaults only supports Preferences settings: ${key.name}"
        }
        require(key.ownership == SettingOwnership.SharedState) {
            "UserDefaultsSettingsStore only accepts SharedState settings: ${key.name}"
        }
        settings[key.name]?.let { existing ->
            require(existing.type == type) {
                "Setting ${key.name} was already opened as ${existing.type}, not $type"
            }
            @Suppress("UNCHECKED_CAST")
            return existing.setting as Setting<Value>
        }
        val initial = if (defaults.objectForKey(key.name) == null) {
            key.default
        } else {
            read()
        }
        return UserDefaultsSetting(initial, write).also {
            settings[key.name] = UserDefaultsEntry(type, it)
        }
    }
}

private data class UserDefaultsEntry(
    val type: String,
    val setting: Setting<*>,
)

private class UserDefaultsSetting<Value>(
    initialValue: Value,
    private val write: (Value) -> Unit,
) : Setting<Value> {
    private val mutableValue = MutableStateFlow(initialValue)

    override val value: StateFlow<Value> = mutableValue.asStateFlow()

    override suspend fun set(value: Value) {
        write(value)
        mutableValue.value = value
    }
}
