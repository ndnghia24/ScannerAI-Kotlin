package com.example.scannerai.AnalyzeFeatures.fragments

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.scannerai.R
import com.example.scannerai.AnalyzeFeatures.adapter.ImageAdapter

class StorageImagesFragment : Fragment() {
    private var recyclerView: RecyclerView? = null
    private var imageAdapter: ImageAdapter? = null
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.rcv_all_images)
        val layoutManager = GridLayoutManager(context, 4)
        recyclerView?.setLayoutManager(layoutManager)

        recyclerView?.setHasFixedSize(true);
        recyclerView?.setItemViewCacheSize(30);

        if (hasReadExternalStoragePermission()) {
            imageAdapter = ImageAdapter(view.context, allImages)
            recyclerView?.setAdapter(imageAdapter)
        } else {
            requestReadExternalStoragePermission()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_storage_images, container, false)
    }

    private val allImages: List<String>
        private get() {
            val imagePaths: MutableList<String> = ArrayList()
            val projection = arrayOf(MediaStore.Images.Media.DATA)
            val cursor = activity?.contentResolver?.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                MediaStore.Images.Media.DATE_ADDED + " DESC"
            )
            if (cursor != null) {
                val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                while (cursor.moveToNext()) {
                    val imagePath = cursor.getString(columnIndex)
                    imagePaths.add(imagePath)
                }
                cursor.close()
            }
            return imagePaths
        }

    private fun hasReadExternalStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestReadExternalStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                // Show an explanation to the user as to why your app needs the permission
            } else {
                requestPermissions(
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    READ_EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE
                )
            }
        } else {
            if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                // Show an explanation to the user as to why your app needs the permission
            } else {
                requestPermissions(
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_MEDIA_LOCATION),
                    READ_EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE
                )
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            val uri = Uri.fromParts("package", requireActivity().packageName, null)
            intent.data = uri
            startActivity(intent)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == READ_EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE) {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                imageAdapter = ImageAdapter(requireContext(), allImages)
                recyclerView!!.adapter = imageAdapter
            } else {
                Toast.makeText(
                    requireContext(),
                    "Permission denied",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    companion object {
        private const val READ_EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE = 123
    }
}