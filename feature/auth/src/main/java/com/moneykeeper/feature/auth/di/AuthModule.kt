package com.moneykeeper.feature.auth.di

import com.moneykeeper.core.domain.repository.KeyDerivation
import com.moneykeeper.core.domain.repository.MasterKeyProvider
import com.moneykeeper.feature.auth.domain.MasterKeyDerivation
import com.moneykeeper.feature.auth.domain.MasterKeyHolder
import com.moneykeeper.feature.auth.domain.PostUnlockCallback
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthBindingsModule {
    @Multibinds
    abstract fun bindPostUnlockCallbacks(): Set<@JvmSuppressWildcards PostUnlockCallback>

    @Binds @Singleton
    abstract fun bindMasterKeyProvider(holder: MasterKeyHolder): MasterKeyProvider

    @Binds @Singleton
    abstract fun bindKeyDerivation(derivation: MasterKeyDerivation): KeyDerivation
}

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideMasterKeyDerivation(): MasterKeyDerivation = MasterKeyDerivation()

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
