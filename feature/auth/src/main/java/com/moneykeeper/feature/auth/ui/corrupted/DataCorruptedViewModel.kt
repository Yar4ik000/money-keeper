package com.moneykeeper.feature.auth.ui.corrupted

import androidx.lifecycle.ViewModel
import com.moneykeeper.core.database.DatabaseProvider
import com.moneykeeper.core.database.security.DatabaseKeyStorage
import com.moneykeeper.feature.auth.domain.MasterKeyHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class DataCorruptedViewModel @Inject constructor(
    private val keyStorage: DatabaseKeyStorage,
    private val masterKeyHolder: MasterKeyHolder,
    private val databaseProvider: DatabaseProvider,
) : ViewModel() {

    private val _wiped = MutableStateFlow(false)
    val wiped = _wiped.asStateFlow()

    fun wipeAndReset() {
        databaseProvider.close()
        masterKeyHolder.clear()
        keyStorage.wipe()
        _wiped.update { true }
    }
}
