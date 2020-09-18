package com.dhimandasgupta.profilepic

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.core.content.PermissionChecker
import coil.load
import coil.transform.CircleCropTransformation
import com.dhimandasgupta.profilepic.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import com.yalantis.ucrop.UCrop
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {
    companion object {
        const val READ_EXTERNAL_STORAGE_PERMISSION = 100
        const val CAMERA_PERMISSION = 102
        const val LOCATION_PERMISSION = 104

        const val PICK_IMAGE_REQUEST_CODE = 1000
        const val TAKE_PICTURE_REQUEST_CODE = 1001

        const val FOLDER_CAMERA = "Camera"
        const val FOLDER_CROP = "Crop"

        const val IMAGE_PATH = "image_path"
    }

    private var binding: ActivityMainBinding? = null

    private var cameraFileUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        retrieveImage()

        binding?.camera?.setOnClickListener {
            onCameraClicked()
        }

        binding?.gallery?.setOnClickListener {
            onGalleryClicked()
        }

        binding?.materialSwitch?.setOnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView.isPressed) {
                onLocationChangedRequested(isChecked)
                binding?.materialSwitch?.isChecked = !isChecked
            }
        }
    }

    override fun onResume() {
        super.onResume()

        binding?.materialSwitch?.isChecked = isLocationPermissionGranted()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        when {
            requestCode.isRequestPermissionCodeCamera() && grantResults.isPermissionGrantedBySystem() -> requestTakeImage()
            requestCode.isRequestPermissionCodeCamera() -> showSnackBar(
                text = "Need your permission to take picture",
                actionText = "Open Settings",
                actionClick = { openSettings() })
            requestCode.isRequestPermissionCodeStorage() && grantResults.isPermissionGrantedBySystem() -> requestPickImage()
            requestCode.isRequestPermissionCodeStorage() -> showSnackBar(
                text = "Need your permission to pick image",
                actionText = "Open Settings",
                actionClick = { openSettings() })
            requestCode.isRequestPermissionCodeLocation() && grantResults.isPermissionGrantedBySystem() -> binding?.materialSwitch?.isChecked =
                true
            requestCode.isRequestPermissionCodeLocation() -> showSnackBar(
                text = "Need location permission",
                actionText = "Open Settings",
                actionClick = { openSettings() })
            else -> showSnackBar(text = "Invalid Permissions")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when {
            requestCode.isTakePictureActivityResultRequestCode() && resultCode.isResultOk() -> resolveTakeImage()
            requestCode.isTakePictureActivityResultRequestCode() -> showSnackBar("Looks like you did not take any photo")
            requestCode.isPickImageActivityResultRequestCode() && resultCode.isResultOk() -> data?.let {
                resolvePickedImage(
                    data
                )
            }
            requestCode.isPickImageActivityResultRequestCode() -> showSnackBar("Looks like you did not selected any picture")
            requestCode.isCropActivityResultRequestCode() && resultCode.isResultOk() -> data?.let {
                resolveCroppedImage(
                    UCrop.getOutput(it)
                )
            }
            requestCode.isCropActivityResultRequestCode() -> data?.let {
                showSnackBar(
                    "Something went wrong while cropping : ${
                        UCrop.getError(
                            it
                        )?.localizedMessage
                    }"
                )
            }
            else -> showSnackBar(text = "Invalid activity result")
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun onGalleryClicked() {
        when {
            isStoragePermissionGranted() -> requestPickImage()
            else -> requestStoragePermission()
        }
    }

    private fun onCameraClicked() {
        when {
            isCameraPermissionGranted() -> requestTakeImage()
            else -> requestCameraPermission()
        }
    }

    private fun onLocationChangedRequested(checked: Boolean) {
        when {
            !checked -> showSnackBar(
                text = "Want to turn off location?",
                actionText = "Open Settings",
                actionClick = { openSettings() })
            else -> requestLocationPermission()
        }
    }

    private fun requestPickImage() {
        val mimeTypes = arrayOf("image/jpeg", "image/png", "image/jpg")
        val photoPickerIntent = Intent(Intent.ACTION_PICK).also {
            it.type = "image/*"
            it.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        }
        startActivityForResult(photoPickerIntent, PICK_IMAGE_REQUEST_CODE)
    }

    private fun resolvePickedImage(data: Intent) {
        data.data?.let {
            requestCrop(it)
        }
    }

    private fun requestTakeImage() {
        val destinationDirectory = File(cacheDir, FOLDER_CAMERA)
        if (!destinationDirectory.exists()) {
            destinationDirectory.mkdir()
        }
        deleteOldFiles(destinationDirectory)

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val outputMediaFile = getOutputMediaFile()

        outputMediaFile?.let { mediaFile ->
            cameraFileUri = FileProvider.getUriForFile(
                this,
                "$packageName.fileProvider", mediaFile
            )
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraFileUri)

            intent.resolveActivity(packageManager)?.let {
                startActivityForResult(intent, TAKE_PICTURE_REQUEST_CODE)
            } ?: showSnackBar("No Camera Application found....")
        }
    }

    private fun resolveTakeImage() {
        cameraFileUri?.let {
            requestCrop(it)
        }
    }

    private fun requestCrop(sourceUri: Uri) {
        val destinationDirectory = File(cacheDir, FOLDER_CROP)
        if (!destinationDirectory.exists()) {
            destinationDirectory.mkdir()
        }
        deleteOldFiles(destinationDirectory)
        val lastPath = sourceUri.lastPathSegment

        lastPath?.let {
            val destinationFile = File(destinationDirectory, lastPath)
            UCrop.of(sourceUri, Uri.fromFile(destinationFile))
                .withAspectRatio(1f, 1f)
                .withMaxResultSize(800, 800)
                .start(this)
        }
    }

    private fun resolveCroppedImage(uri: Uri?) {
        uri?.let {
            saveImage(uri)
        }
    }

    private fun deleteOldFiles(directory: File) {
        directory.listFiles()?.forEach { file ->
            if (!file.isDirectory) file.delete()
        }
    }

    private fun getOutputMediaFile(): File? {
        val mediaStorageDir = File(cacheDir, FOLDER_CAMERA)
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null
            }
        }
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        return File(
            mediaStorageDir.path + File.separator.toString() +
                "IMG_" + timeStamp + ".jpg"
        )
    }

    private fun saveImage(uri: Uri?) {
        uri?.let {
            val pref = getPreferences(MODE_PRIVATE)
            pref.edit().putString(IMAGE_PATH, it.path).apply()
            retrieveImage()
        }
    }

    private fun retrieveImage() {
        val pref = getPreferences(MODE_PRIVATE)
        val path = pref.getString(IMAGE_PATH, null)

        path?.let {
            binding?.profile?.load(File(it)) {
                crossfade(true)
                placeholder(R.drawable.ic_profile_place_holder)
                transformations(CircleCropTransformation())
            }
        }
    }

    private fun showSnackBar(
        text: String,
        actionText: String? = null,
        actionClick: (() -> Unit)? = null
    ) {
        binding?.root?.let { view ->
            Snackbar.make(
                view,
                text,
                Snackbar.LENGTH_SHORT
            ).setAction(actionText ?: "") { actionClick?.invoke() }.show()
        }
    }

    private fun Int.isRequestPermissionCodeCamera() = this == CAMERA_PERMISSION
    private fun Int.isRequestPermissionCodeStorage() = this == READ_EXTERNAL_STORAGE_PERMISSION
    private fun Int.isRequestPermissionCodeLocation() = this == LOCATION_PERMISSION

    private fun Int.isResultOk() = this == RESULT_OK

    private fun Int.isTakePictureActivityResultRequestCode() = this == TAKE_PICTURE_REQUEST_CODE
    private fun Int.isPickImageActivityResultRequestCode() = this == PICK_IMAGE_REQUEST_CODE
    private fun Int.isCropActivityResultRequestCode() = this == UCrop.REQUEST_CROP

    private fun IntArray.isPermissionGrantedBySystem() =
        this.isNotEmpty() && this[0] == PackageManager.PERMISSION_GRANTED

    private fun isCameraPermissionGranted() = PermissionChecker.checkSelfPermission(
        this,
        Manifest.permission.CAMERA
    ) == PermissionChecker.PERMISSION_GRANTED

    private fun isStoragePermissionGranted() = PermissionChecker.checkSelfPermission(
        this,
        Manifest.permission_group.STORAGE
    ) == PermissionChecker.PERMISSION_GRANTED

    private fun isLocationPermissionGranted() = PermissionChecker.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PermissionChecker.PERMISSION_GRANTED &&
        PermissionChecker.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PermissionChecker.PERMISSION_GRANTED

    private fun requestCameraPermission() = ActivityCompat.requestPermissions(
        this, arrayOf(
            Manifest.permission.CAMERA
        ), CAMERA_PERMISSION
    )

    private fun requestStoragePermission() = ActivityCompat.requestPermissions(
        this, arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE
        ), READ_EXTERNAL_STORAGE_PERMISSION
    )

    private fun requestLocationPermission() = ActivityCompat.requestPermissions(
        this, arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION
        ), LOCATION_PERMISSION
    )

    private fun openSettings() = startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse(
                "package:$packageName"
            )
        ).also {
            it.addCategory(Intent.CATEGORY_DEFAULT)
            it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
}