package pl.twojanazwa.aapsremote.data

import javax.inject.Qualifier

/** Zakres korutyn aplikacji (SupervisorJob + Dispatchers.IO): wczytanie ustawień, rejestrator. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DataScope
