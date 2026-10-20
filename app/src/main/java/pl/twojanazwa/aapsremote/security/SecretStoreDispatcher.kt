package pl.twojanazwa.aapsremote.security

import javax.inject.Qualifier

/** Dyspozytor I/O magazynu (SECRET-13); w aplikacji Dispatchers.IO. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SecretStoreDispatcher
