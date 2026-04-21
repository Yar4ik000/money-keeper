package com.moneykeeper.feature.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneykeeper.core.database.security.DatabaseKeyStorage
import com.moneykeeper.core.database.security.KeystoreMasterKeyWrapper
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for KeystoreMasterKeyWrapper — requires Android Keystore hardware.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreMasterKeyWrapperTest {

    private lateinit var keyStorage: DatabaseKeyStorage
    private lateinit var wrapper: KeystoreMasterKeyWrapper

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        keyStorage = DatabaseKeyStorage(context)
        keyStorage.wipe()
        wrapper = KeystoreMasterKeyWrapper(keyStorage)
        wrapper.deleteKey()
    }

    @After
    fun tearDown() {
        runCatching { wrapper.deleteKey() }
        keyStorage.wipe()
    }

    @Test
    fun wrap_then_unwrap_returns_original_key() {
        val masterKey = ByteArray(32) { (it + 1).toByte() }
        wrapper.wrap(masterKey)
        assertArrayEquals(masterKey, wrapper.unwrap())
    }

    @Test
    fun isInitialized_false_before_wrap() {
        assertFalse(wrapper.isInitialized())
    }

    @Test
    fun isInitialized_true_after_wrap() {
        wrapper.wrap(ByteArray(32) { 0x42 })
        assertTrue(wrapper.isInitialized())
    }

    @Test
    fun wrap_twice_latest_key_survives_unwrap() {
        val first  = ByteArray(32) { 0x01 }
        val second = ByteArray(32) { 0x02 }
        wrapper.wrap(first)
        wrapper.wrap(second)
        assertArrayEquals(second, wrapper.unwrap())
    }
}
