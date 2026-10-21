package pl.twojanazwa.aapsremote.ui.platform

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import pl.twojanazwa.aapsremote.ui.permissions.CameraPermission

class AndroidCameraPermission @Inject constructor(
    @ApplicationContext private val context: Context,
) : CameraPermission {
    override fun isGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, CameraPermission.CAMERA) == PackageManager.PERMISSION_GRANTED
}
