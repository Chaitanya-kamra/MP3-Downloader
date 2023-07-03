package com.chaitanya.mp3downloader


import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.chaitanya.mp3downloader.databinding.ActivityMainBinding
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class MainActivity : AppCompatActivity() {
    private var btnDownload: View? = null

    private lateinit var binding: ActivityMainBinding

    private var progressButton: ProgressButton? = null

    private var selectedFolder: Uri? = null

    // ActivityResultLauncher for folder selection
    private val folderSelectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data: Intent? = result.data
                val folderUri: Uri? = data?.data
                val folderDocument = folderUri?.let { DocumentFile.fromTreeUri(this, it) }
                val folderName = folderDocument?.name
                Log.e("path", folderDocument!!.uri.path.toString())
                selectedFolder = folderUri
                binding.etDestination.setText(folderName)
            }
        }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        btnDownload = findViewById(R.id.btn_Download)


        progressButton = ProgressButton(this@MainActivity, btnDownload!!)

        freeUi()
        // Click listener for folder selection
        binding.etDestination.setOnClickListener {
            openFolderSelection()
        }
        // Click listener for download button
        btnDownload!!.setOnClickListener {

            if (binding.etDestination.text.isNullOrEmpty()) {
                Toast.makeText(this, "Select Destination", Toast.LENGTH_SHORT).show()
            } else if (binding.etLink.text.isNullOrEmpty()) {

                Toast.makeText(this, "Enter Link", Toast.LENGTH_SHORT).show()
            } else {

                val videoUrl = binding.etLink.text.toString()
                Thread(Runnable {
                    runOnUiThread {
                        blockUi()
                    }
                    if (!Python.isStarted()) {
                        Python.start(AndroidPlatform(this))
                    }
                    try {
                        val python = Python.getInstance()
                        val pythonModule = python.getModule("myapp")
                        val videoInfo: PyObject = pythonModule.callAttr("get_video_info", videoUrl)
                        val result = videoInfo.asList()

                        val title = result[0].toString()
                        val likes = result[1].toString()
                        val views = result[2].toString()
                        val thumbnailUrl = result[3].toString()
                        val stream = result[4].toString()

                        val intent = Intent(this, DownloadActivity::class.java)

                        // Pass data to DownloadActivity

                        intent.putExtra("title", title)
                        intent.putExtra("image", thumbnailUrl)
                        intent.putExtra("views", views)
                        intent.putExtra("likes", likes)
                        intent.putExtra("stream", stream)
                        intent.putExtra("folder", selectedFolder)

                        startActivity(intent)
                        runOnUiThread {
                            freeUi()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            freeUi()
                            Toast.makeText(this, "Enter Valid Link", Toast.LENGTH_SHORT).show()
                        }
                    }
                }).start()
            }
        }
    }


    // Open folder selection
    private fun openFolderSelection() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        folderSelectionLauncher.launch(intent)
    }

    // Block UI elements
    private fun blockUi() {
        binding.tilYtLink.setStartIconTintList(ColorStateList.valueOf(Color.parseColor("#8B8585")))
        binding.tilDestination.setStartIconTintList(ColorStateList.valueOf(Color.parseColor("#8B8585")))
        progressButton!!.buttonActivated()
        binding.etLink.isEnabled = false
        binding.etDestination.isEnabled = false
        binding.etLink.setTextColor(Color.parseColor("#8B8585"))
        binding.etDestination.setTextColor(Color.parseColor("#8B8585"))
    }

    // Free UI elements
    private fun freeUi() {
        binding.tilYtLink.setStartIconTintList(ColorStateList.valueOf(Color.parseColor("#1A0D0D")))
        binding.tilDestination.setStartIconTintList(ColorStateList.valueOf(Color.parseColor("#1A0D0D")))
        progressButton!!.buttonDownload()
        binding.etLink.isEnabled = true
        binding.etDestination.isEnabled = true
        binding.etLink.setTextColor(Color.parseColor("#1A0D0D"))
        binding.etDestination.setTextColor(Color.parseColor("#1A0D0D"))
    }
    override fun onResume() {
        super.onResume()

        // Check if the app was opened by sharing from another app
        val intent = intent
        val action = intent.action
        val type = intent.type
        if (Intent.ACTION_SEND == action && type != null) {
            if (type.startsWith("text/plain")) {
                handleSharedText(intent)
            }
        }
    }

    private fun handleSharedText(intent: Intent) {
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (sharedText != null && sharedText.contains("youtu")) {
            binding.etLink.setText(sharedText)
        }
    }

}


