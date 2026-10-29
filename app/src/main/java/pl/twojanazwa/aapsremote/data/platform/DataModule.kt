package pl.twojanazwa.aapsremote.data.platform

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import pl.twojanazwa.aapsremote.data.AapsNumberSettingsStore
import pl.twojanazwa.aapsremote.data.AapsNumberSettings
import pl.twojanazwa.aapsremote.data.CommandHistory
import pl.twojanazwa.aapsremote.data.CommandLimitsSettings
import pl.twojanazwa.aapsremote.data.CommandLimitsStorage
import pl.twojanazwa.aapsremote.data.CommandLimitsStore
import pl.twojanazwa.aapsremote.data.DataDispatcher
import pl.twojanazwa.aapsremote.data.DataScope
import pl.twojanazwa.aapsremote.data.HistoryRepository
import pl.twojanazwa.aapsremote.data.HistoryStorage
import pl.twojanazwa.aapsremote.data.SettingsStorage
import pl.twojanazwa.aapsremote.sms.AapsNumberProvider

/**
 * Wiązania spec 007 (DATA-05, DATA-06, DATA-13, DATA-14). Jedyne wiązanie [AapsNumberProvider]
 * (celowo nie ma go w `SmsModule`); [AapsNumberSettings] jest `@Singleton`, więc provider
 * i [AapsNumberSettingsStore] to jedna instancja.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun aapsNumberProvider(impl: AapsNumberSettings): AapsNumberProvider

    @Binds
    abstract fun aapsNumberSettingsStore(impl: AapsNumberSettings): AapsNumberSettingsStore

    @Binds
    abstract fun settingsStorage(impl: SharedPreferencesSettingsStorage): SettingsStorage

    @Binds
    abstract fun commandLimitsStore(impl: CommandLimitsSettings): CommandLimitsStore

    @Binds
    abstract fun commandLimitsStorage(impl: SharedPreferencesCommandLimitsStorage): CommandLimitsStorage

    @Binds
    abstract fun commandHistory(impl: HistoryRepository): CommandHistory

    @Binds
    abstract fun historyStorage(impl: RoomHistoryStorage): HistoryStorage

    companion object {
        @Provides
        @DataDispatcher
        fun dispatcher(): CoroutineDispatcher = Dispatchers.IO

        @Provides
        @Singleton
        @DataScope
        fun scope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Bez destrukcyjnych migracji zastępczych i bez zapytań na wątku głównym; budowa nie otwiera pliku. */
        @Provides
        @Singleton
        fun database(@ApplicationContext context: Context): AapsRemoteDatabase =
            Room.databaseBuilder(context, AapsRemoteDatabase::class.java, AapsRemoteDatabase.FILE_NAME)
                .addMigrations(AapsRemoteDatabase.MIGRATION_1_2, AapsRemoteDatabase.MIGRATION_2_3)
                .build()

        @Provides
        fun historyDao(database: AapsRemoteDatabase): HistoryDao = database.historyDao()
    }
}
