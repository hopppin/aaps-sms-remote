package pl.twojanazwa.aapsremote.security.platform

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import pl.twojanazwa.aapsremote.security.AesGcmSecretCipher
import pl.twojanazwa.aapsremote.security.KeystoreEnvironment
import pl.twojanazwa.aapsremote.security.KeystoreTotpSecretStore
import pl.twojanazwa.aapsremote.security.SecretCipher
import pl.twojanazwa.aapsremote.security.SecretKeySource
import pl.twojanazwa.aapsremote.security.SecretRecordStorage
import pl.twojanazwa.aapsremote.security.SecretStoreDispatcher
import pl.twojanazwa.aapsremote.security.TotpSecretProvider
import pl.twojanazwa.aapsremote.security.TotpSecretStore

/** Wiązania spec 006. [KeystoreTotpSecretStore] jest `@Singleton`, więc store i provider to jedna instancja. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {
    @Binds
    abstract fun store(impl: KeystoreTotpSecretStore): TotpSecretStore

    @Binds
    abstract fun provider(impl: KeystoreTotpSecretStore): TotpSecretProvider

    @Binds
    abstract fun cipher(impl: AesGcmSecretCipher): SecretCipher

    @Binds
    abstract fun keySource(impl: AndroidKeystoreKeySource): SecretKeySource

    @Binds
    abstract fun environment(impl: AndroidKeystoreEnvironment): KeystoreEnvironment

    @Binds
    abstract fun storage(impl: SharedPreferencesSecretRecordStorage): SecretRecordStorage

    companion object {
        @Provides
        @SecretStoreDispatcher
        fun dispatcher(): CoroutineDispatcher = Dispatchers.IO
    }
}
