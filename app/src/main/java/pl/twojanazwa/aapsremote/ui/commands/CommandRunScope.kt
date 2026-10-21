package pl.twojanazwa.aapsremote.ui.commands

import javax.inject.Qualifier

/** Zakres korutyn aplikacji, w którym biegną sesje (CMDUI-13). RUNTIME, by CMDUI-27 dało się sprawdzić refleksją. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class CommandRunScope
