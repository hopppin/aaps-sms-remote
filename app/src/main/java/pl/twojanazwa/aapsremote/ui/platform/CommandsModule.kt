package pl.twojanazwa.aapsremote.ui.platform

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import pl.twojanazwa.aapsremote.ui.commands.CommandRunScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CommandsModule {
    /** SupervisorJob: zakończenie jednej sesji wyjątkiem nie anuluje zakresu; nigdy nie anulowany przez ui. */
    @Provides
    @Singleton
    @CommandRunScope
    fun commandRunScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
