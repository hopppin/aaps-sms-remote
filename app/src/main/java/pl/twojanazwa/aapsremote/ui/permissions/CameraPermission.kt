package pl.twojanazwa.aapsremote.ui.permissions

/** Stan CAMERA w chwili wywołania, bez zapamiętywania. Implementacja: ui.platform.AndroidCameraPermission. */
fun interface CameraPermission {
    fun isGranted(): Boolean

    companion object {
        const val CAMERA: String = "android.permission.CAMERA"
    }
}
