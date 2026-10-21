package pl.twojanazwa.aapsremote.ui.commands

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.session.SessionState
import javax.inject.Inject
import javax.inject.Singleton

/** Publikuje ostatnio uruchomioną sesję do czasu zamknięcia wyniku (CMDUI-16, CMDUI-21). */
@Singleton
class CurrentRun @Inject constructor() {
    private val current = MutableStateFlow<CurrentRunData?>(null)

    /** null = brak przebiegu ani wyniku do pokazania. */
    val state: StateFlow<CurrentRunData?> = current.asStateFlow()

    /** Zastępuje poprzednie uruchomienie. */
    fun publish(run: CurrentRunData) {
        current.value = run
    }

    /** Zamknięcie wyniku (CMDUI-21). */
    fun clear() {
        current.value = null
    }
}

/** Polecenie potrzebne do nagłówka i kroków; stan sesji bez `CommandSession`. */
class CurrentRunData(val command: Command, val state: StateFlow<SessionState>) {
    override fun toString(): String = "CurrentRunData(command=$command)"
}
