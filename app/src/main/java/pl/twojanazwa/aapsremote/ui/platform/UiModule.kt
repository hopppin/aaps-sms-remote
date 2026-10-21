package pl.twojanazwa.aapsremote.ui.platform

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pl.twojanazwa.aapsremote.ui.permissions.CameraPermission
import pl.twojanazwa.aapsremote.ui.settings.secret.SecretClipboard

/** Wiązania spec 008 (UI-10, UI-24). */
@Module
@InstallIn(SingletonComponent::class)
abstract class UiModule {
    @Binds
    abstract fun cameraPermission(impl: AndroidCameraPermission): CameraPermission

    @Binds
    abstract fun secretClipboard(impl: AndroidSecretClipboard): SecretClipboard
}
