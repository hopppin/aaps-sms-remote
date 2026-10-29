package pl.twojanazwa.aapsremote.data.platform

import android.content.SharedPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.data.platform.SharedPreferencesCommandLimitsStorage.Companion.writeWithRollback

class CommandLimitsRollbackTest {

    private class FakePrefs(val map: MutableMap<String, Any?>, var failFirstCommit: Boolean) : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = HashMap(map)
        override fun contains(key: String) = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor()
        override fun getInt(key: String, d: Int) = map[key] as? Int ?: d
        override fun getString(key: String, d: String?) = map[key] as? String ?: d
        override fun getStringSet(key: String, d: MutableSet<String>?) = d
        override fun getLong(key: String, d: Long) = d
        override fun getFloat(key: String, d: Float) = d
        override fun getBoolean(key: String, d: Boolean) = d
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        inner class FakeEditor : SharedPreferences.Editor {
            private val ops = mutableListOf<() -> Unit>()
            private fun put(k: String, v: Any?) = apply { ops += { map[k] = v } }
            override fun putInt(k: String, v: Int) = put(k, v)
            override fun putString(k: String, v: String?) = put(k, v)
            override fun putLong(k: String, v: Long) = put(k, v)
            override fun putFloat(k: String, v: Float) = put(k, v)
            override fun putBoolean(k: String, v: Boolean) = put(k, v)
            override fun putStringSet(k: String, v: MutableSet<String>?) = put(k, v)
            override fun remove(k: String) = apply { ops += { map.remove(k) } }
            override fun clear() = apply { ops += { map.clear() } }
            override fun apply() { ops.forEach { it() } }
            override fun commit(): Boolean {
                ops.forEach { it() } // Android zmienia pamięć nawet gdy zapis na dysk się nie uda
                if (failFirstCommit) {
                    failFirstCommit = false
                    return false
                }
                return true
            }
        }
    }

    private val new = mapOf("a" to 1, "b" to 2, "c" to 3)

    @Test
    fun `LIM-04 nieudany commit przywraca dawne wartosci i usuwa nieistniejace klucze`() {
        val prefs = FakePrefs(mutableMapOf("a" to 10, "b" to "tekst"), failFirstCommit = true)
        assertFalse(writeWithRollback(prefs, new))
        assertEquals(mapOf<String, Any?>("a" to 10, "b" to "tekst"), prefs.map)
    }

    @Test
    fun `LIM-04 udany commit zapisuje wartosci`() {
        val prefs = FakePrefs(mutableMapOf("a" to 10), failFirstCommit = false)
        assertTrue(writeWithRollback(prefs, new))
        assertEquals(mapOf<String, Any?>("a" to 1, "b" to 2, "c" to 3), prefs.map)
    }
}
