package com.dhimandasgupta.profilepic

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.load
import coil.transform.CircleCropTransformation
import com.dhimandasgupta.profilepic.databinding.ActivityMainBinding
import com.yalantis.ucrop.UCrop
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {
    companion object {
        const val READ_EXTERNAL_STORAGE_PERMISSION = 100
        const val CAMERA_PERMISSION = 102

        const val PICK_IMAGE_REQUEST_CODE = 1000
        const val TAKE_PICTURE_REQUEST_CODE = 1001

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
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            READ_EXTERNAL_STORAGE_PERMISSION -> if (grantResults.isNotEmpty()
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                requestPickImage()
            } else {
                Toast.makeText(applicationContext, "Storage Permission denied", Toast.LENGTH_SHORT)
                    .show()
            }
            CAMERA_PERMISSION -> if (grantResults.isNotEmpty()
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                requestTakeImage()
            } else {
                Toast.makeText(applicationContext, "Camera Permission denied", Toast.LENGTH_SHORT)
                    .show()
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            PICK_IMAGE_REQUEST_CODE -> if (resultCode == RESULT_OK) {
                data?.let {
                    resolvePickedImage(data)
                }
            } else {
                Toast.makeText(applicationContext, "Could not found Image", Toast.LENGTH_SHORT)
                    .show()
            }
            TAKE_PICTURE_REQUEST_CODE -> if (resultCode == RESULT_OK) {
                resolveTakeImage()
            } else {
                Toast.makeText(applicationContext, "Could not found Image", Toast.LENGTH_SHORT)
                    .show()
            }
            UCrop.REQUEST_CROP -> if (resultCode == RESULT_OK) {
                data?.let {
                    resolveCroppedImage(UCrop.getOutput(it))
                }
            } else if (resultCode == UCrop.RESULT_ERROR) {
                data?.let {
                    val throwable = UCrop.getError(it)
                    Toast.makeText(
                        applicationContext,
                        "Something went wrong while cropping : ${throwable?.localizedMessage}",
                        Toast.LENGTH_SHORT
                    )
                        .show()
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun onGalleryClicked() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // Should we show an explanation, if Permission id denied previously
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            ) {
                Toast.makeText(
                    applicationContext,
                    "Need your permission to pick image",
                    Toast.LENGTH_SHORT
                ).show()
            }
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                READ_EXTERNAL_STORAGE_PERMISSION
            )
        } else {
            requestPickImage()
        }
    }

    private fun onCameraClicked() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // Should we show an explanation, if Permission id denied previously
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.CAMERA
                )
            ) {
                Toast.makeText(
                    applicationContext,
                    "Need your permission to take image",
                    Toast.LENGTH_SHORT
                ).show()
            }
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                CAMERA_PERMISSION
            )
        } else {
            requestTakeImage()
        }
    }

    private fun requestPickImage() {
        val photoPickerIntent = Intent(Intent.ACTION_PICK)
        photoPickerIntent.type = "image/*"
        startActivityForResult(photoPickerIntent, PICK_IMAGE_REQUEST_CODE)
    }

    private fun resolvePickedImage(data: Intent) {
        data.data?.let {
            requestCrop(it)
        }
    }

    private fun requestTakeImage() {
        val destinationDirectory = File(cacheDir, "Camera")
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
            } ?: Toast.makeText(
                applicationContext,
                "No Camera Application found....",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun resolveTakeImage() {
        cameraFileUri?.let {
            requestCrop(it)
        }
    }

    private fun requestCrop(sourceUri: Uri) {
        val destinationDirectory = File(cacheDir, "Crop")
        if (!destinationDirectory.exists()) {
            destinationDirectory.mkdir()
        }
        deleteOldFiles(destinationDirectory)
        val lastPath = sourceUri.lastPathSegment
        val destinationFile = File(destinationDirectory, lastPath)
        UCrop.of(sourceUri, Uri.fromFile(destinationFile))
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(800, 800)
            .start(this)
    }

    private fun resolveCroppedImage(uri: Uri?) {
        uri?.let {
            saveImage(uri)
        }
    }

    private fun deleteOldFiles(directory: File) {
        for (file in directory.listFiles()) if (!file.isDirectory) file.delete()
    }

    private fun getOutputMediaFile(): File? {
        val mediaStorageDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File(cacheDir, "Camera")
        } else {
            File(
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES
                ), "Camera"
            )
        }
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
}