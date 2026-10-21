package pl.twojanazwa.aapsremote.ui.permissions

class FakeCameraPermission(var granted: Boolean = false) : CameraPermission {
    override fun isGranted(): Boolean = granted
}
