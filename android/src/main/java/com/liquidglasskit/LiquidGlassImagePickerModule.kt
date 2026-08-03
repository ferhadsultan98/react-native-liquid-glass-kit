package com.liquidglasskit

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import com.facebook.react.bridge.BaseActivityEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

/**
 * Small dependency-free bridge to Android's system photo picker.
 *
 * Android 13+ uses the privacy-preserving Photo Picker. Older supported
 * versions use ACTION_OPEN_DOCUMENT and retain read access to the chosen URI.
 */
class LiquidGlassImagePickerModule(
  private val reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {
  companion object {
    private const val REQUEST_PICK_IMAGE = 9041
  }

  private var pendingPromise: Promise? = null

  private val activityListener =
    object : BaseActivityEventListener() {
      override fun onActivityResult(
        activity: Activity,
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
      ) {
        if (requestCode != REQUEST_PICK_IMAGE) return

        val promise = pendingPromise ?: return
        pendingPromise = null

        if (resultCode != Activity.RESULT_OK) {
          promise.resolve(null)
          return
        }

        val uri = data?.data
        if (uri == null) {
          promise.reject("E_NO_IMAGE", "Android image picker returned no image.")
          return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
          try {
            reactContext.contentResolver.takePersistableUriPermission(
              uri,
              Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
          } catch (_: SecurityException) {
            // Some document providers grant session access but do not support
            // persistable permissions. The selected image still works now.
          }
        }

        promise.resolve(uri.toString())
      }
    }

  init {
    reactContext.addActivityEventListener(activityListener)
  }

  override fun getName(): String = "RNLiquidGlassImagePicker"

  @ReactMethod
  fun pickImage(promise: Promise) {
    if (pendingPromise != null) {
      promise.reject("E_PICKER_BUSY", "The Android image picker is already open.")
      return
    }

    val activity = reactContext.currentActivity
    if (activity == null) {
      promise.reject("E_NO_ACTIVITY", "No foreground Android activity is available.")
      return
    }

    val intent =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Intent(MediaStore.ACTION_PICK_IMAGES).apply {
          type = "image/*"
        }
      } else {
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
          addCategory(Intent.CATEGORY_OPENABLE)
          type = "image/*"
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
      }

    pendingPromise = promise
    try {
      activity.startActivityForResult(intent, REQUEST_PICK_IMAGE)
    } catch (error: Exception) {
      pendingPromise = null
      promise.reject("E_PICKER_START", "Unable to open Android's image picker.", error)
    }
  }

  override fun invalidate() {
    pendingPromise?.reject("E_MODULE_INVALIDATED", "Image picker module was invalidated.")
    pendingPromise = null
    reactContext.removeActivityEventListener(activityListener)
    super.invalidate()
  }
}
