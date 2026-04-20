package com.moneykeeper.feature.auth.di

import com.moneykeeper.feature.auth.domain.MasterKeyDerivation
import com.moneykeeper.feature.auth.domain.PostUnlockCallback
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
