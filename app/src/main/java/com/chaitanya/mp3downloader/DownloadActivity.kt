package com.chaitanya.mp3downloader

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.arthenica.mobileffmpeg.Config
import com.arthenica.mobileffmpeg.ExecuteCallback
import com.arthenica.mobileffmpeg.FFmpeg
import com.chaitanya.mp3downloader.databinding.ActivityDownloadBinding
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlin.math.ln
import kotlin.math.pow


class DownloadActivity : AppCompatActivity() {

    lateinit var binding: ActivityDownloadBinding
    private var folderPath: Uri? = null
    private var checker = 1


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDownloadBinding.inflate(layoutInflater)
        setContentView(binding.root)


        val title = intent.getStringExtra("title")
        val image = intent.getStringExtra("image")
        val views = intent.getStringExtra("views")
        val likes = intent.getStringExtra("likes")
        val stream = intent.getStringExtra("stream")
        val folderPathCont = intent.getParcelableExtra<Uri>("folder")

        // Set the UI elements with video information
        binding.tvTitle.text = title
        binding.tvView.text = "${withSuffix(views!!.toLong())} Views"
        binding.tvLikes.text = "${withSuffix(likes!!.toLong())} Likes"
        binding.btnAnother.setOnClickListener {
            finish()
        }
        // Set the folder path and initiate the video download
        folderPath = folderPathCont
        if (stream != null) {
            downloadVideo(stream)
        }
        // Load the video thumbnail using Picasso library
        Picasso.get().load(image).into(binding.ivThumbnail)
    }
    // Add a suffix to the count (e.g., 1.5k, 2M) for better readability
    private fun withSuffix(count: Long): String {
        if (count < 1000) return "" + count
        val exp = (ln(count.toDouble()) / ln(1000.0)).toInt()
        return String.format(
            "%.1f %c",
            count / 1000.0.pow(exp.toDouble()),
            "kMPEG"[exp - 1]
        )
    }


    //
    //Downloading
    //
    // Download the video using the provided media stream URL
    private fun downloadVideo(mediaStream: String) {
        mediaStream.let {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(it)
                .header("Connection", "close")
                .build()

            val titleVid = binding.tvTitle.text.toString()
            val sanitizedNameVid = titleVid.replace(Regex("[^a-zA-Z0-9.-]"), "")
            val timestamp = System.currentTimeMillis()
            val fileName = "$sanitizedNameVid$timestamp.mp4"
            val file = File(filesDir, fileName)
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread { failedUi() }

                    // Handle download failure
                    Log.e("OKHTTP", "failure")
                }

                override fun onResponse(call: Call, response: Response) {
                    response.body?.let { responseBody ->
                        val inputStream = responseBody.byteStream()
                        val outputStream = FileOutputStream(file)

                        val buffer = ByteArray(4096)
                        var bytesRead: Int
                        var totalBytesRead: Long = 0
                        val totalBytes = responseBody.contentLength()
                        var progress: Int

                        try {
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead

                                progress = ((totalBytesRead.toDouble() / totalBytes) * 100).toInt()
                                Log.e("progress", progress.toString())
                                // Update the progress bar and display the downloaded and total data
                                runOnUiThread {
                                    binding.progressBar.progress = progress
                                    val downloadedData = formatDataSize(totalBytesRead)
                                    val totalData = formatDataSize(totalBytes)
                                    binding.tvprogress.text = "$downloadedData / $totalData"
                                }
                            }
                        } catch (e: IOException) {
                            checker = 0

                            e.printStackTrace()
                            runOnUiThread { failedUi() }
                            Log.e("progress", "failure")
                        } finally {
                            try {
                                outputStream.close()
                                inputStream.close()
                                checkDownload(file)
                            } catch (e: IOException) {
                                // Handle file close error
                                e.printStackTrace()
                                runOnUiThread { failedUi() }

                            }
                        }
                    }
                }
            })
        }
    }

    // Format the size of data in bytes to a human-readable format
    private fun formatDataSize(size: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var fileSize = size.toDouble()
        var unitIndex = 0
        while (fileSize > 1024 && unitIndex < units.size - 1) {
            fileSize /= 1024
            unitIndex++
        }
        return "%.2f %s".format(fileSize, units[unitIndex])
    }

    // Check if the download was successful and convert the video to MP3
    fun checkDownload(file: File) {
        if (checker == 0) {
            return
        } else {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    val pathMp3 = convertVideoToMp3(file)
                    saveVideoToFolder(folderPath,pathMp3)
                }
            }
        }
    }

    // Convert the video file to MP3 using FFmpeg Android
    private fun convertVideoToMp3(inputFile: File): String {
        val title = binding.tvTitle.text.toString()
        val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9.-]"), "")
        val timestamp = System.currentTimeMillis()
        val outputMp3Name = "$sanitizedTitle$timestamp.mp3"
        val outputMp3File = File(filesDir, outputMp3Name)

        val command =
            "-i ${inputFile.absolutePath} -vn -acodec libmp3lame ${outputMp3File.absolutePath}"

        val mediaPlayer = MediaPlayer.create(this, Uri.fromFile(inputFile))
        val totalDuration = mediaPlayer.duration.toLong()

        mediaPlayer.release()
        runOnUiThread {
            convertUi()
        }

        Config.enableStatisticsCallback { newStatistics ->
            val progress: Float =
                newStatistics.time.toFloat() / totalDuration
            val progressFinal = (progress * 100).toInt()

            runOnUiThread {
                binding.progressBar.progress = progressFinal
                val formattedTime = formatTime(newStatistics.time.toLong())
                val formattedTotalDuration = formatTime(totalDuration)
                val convertProgressText = "$formattedTime / $formattedTotalDuration"
                binding.tvConvertProgress.text = convertProgressText

            }
        }
        FFmpeg.execute(command)
        return outputMp3File.absolutePath
    }

    private fun formatTime(timeInMilliseconds: Long): String {
        val totalSeconds = timeInMilliseconds / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }


    // Save the MP3 to the selected folder
    private fun saveVideoToFolder(folderUri: Uri?, audioFilePath: String?) {
        runOnUiThread { savingUi() }
        val contentResolver = contentResolver
        val titleAud = binding.tvTitle.text.toString()
        val sanitizedNameAud = titleAud.replace(Regex("[^a-zA-Z0-9.-]"), "")
        val timestamp = System.currentTimeMillis()
        val fileName = "$sanitizedNameAud$timestamp.mp3"
        val mimeType = "audio/mpeg"
        val treeUri = DocumentsContract.buildDocumentUriUsingTree(
            folderUri,
            DocumentsContract.getTreeDocumentId(folderUri)
        )
        // Get the size of the video file
        val audioFile = audioFilePath?.let { File(it) }
        val audioSize = audioFile!!.length()

        val uri = DocumentsContract.createDocument(contentResolver, treeUri, mimeType, fileName)

        uri?.let {
            val outputStream: OutputStream? = contentResolver.openOutputStream(it)
            outputStream?.use { stream ->
                // Read the video file and write its content to the OutputStream
                val inputStream = FileInputStream(audioFile)
                inputStream.use { input ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    var totalBytesRead: Long = 0

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        stream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
//                        Thread.sleep(50)
                        // Calculate progress percentage
                        val progress = (totalBytesRead * 100 / audioSize).toInt()
                        // Update progress bar and percentage TextView
                        runOnUiThread {
                            binding.progressBar.progress = progress
                            binding.tvSaveProgress.text = "$progress %"
                        }
                    }
                    stream.close()
                }
            }
        }
        runOnUiThread { successUi() }
    }



    // Set UI state for converting the video to MP3
    private fun convertUi() {
        binding.tvSaveProgress.visibility = View.GONE
        binding.tvprogress.visibility = View.GONE
        binding.btnAnother.visibility = View.GONE
        binding.ivHelperInfo.visibility = View.GONE
        binding.tvHelperText.visibility = View.GONE
        binding.ivProgressComplete.visibility = View.GONE
        binding.tvConvertProgress.visibility = View.VISIBLE
        binding.progressBar.progressTintList = ColorStateList.valueOf(Color.parseColor("#FFBB0E"))
        binding.tvDownload.text = "Converting…"

    }

    // Set UI state for saving the mp3
    private fun savingUi() {
        binding.tvConvertProgress.visibility = View.GONE
        binding.progressBar.progressTintList = ColorStateList.valueOf(Color.parseColor("#28C6D0"))
        binding.btnAnother.visibility = View.GONE
        binding.ivHelperInfo.visibility = View.GONE
        binding.tvHelperText.visibility = View.GONE
        binding.ivProgressComplete.visibility = View.GONE
        binding.tvprogress.visibility = View.GONE
        binding.tvSaveProgress.visibility = View.VISIBLE
        binding.tvDownload.text = "Saving…"

    }

    // Set UI state for a failed download
    private fun failedUi() {
        binding.tvConvertProgress.visibility = View.GONE
        binding.tvprogress.visibility = View.GONE
        binding.tvSaveProgress.visibility = View.GONE
        binding.tvDownload.visibility = View.VISIBLE
        binding.tvprogress.visibility = View.VISIBLE
        binding.progressBar.progress = 100
        binding.progressBar.progressTintList = ColorStateList.valueOf(Color.parseColor("#DC4A4A"))
        binding.ivProgressComplete.visibility = View.VISIBLE
        binding.btnAnother.visibility = View.VISIBLE
        binding.ivHelperInfo.visibility = View.VISIBLE
        binding.tvHelperText.visibility = View.VISIBLE
        binding.tvprogress.visibility = View.GONE
        binding.ivProgressComplete.setImageResource(R.drawable.ic_red_cross)
        binding.tvDownload.text = "Failed"
        binding.ivHelperInfo.setBackgroundResource(R.drawable.ic_failed)
        binding.tvHelperText.text = "Please check your internet connection"
    }

    // Set UI state for a successful download
    private fun successUi() {
        binding.tvConvertProgress.visibility = View.GONE
        binding.tvprogress.visibility = View.GONE
        binding.tvSaveProgress.visibility = View.GONE
        binding.tvDownload.visibility = View.VISIBLE
        binding.tvprogress.visibility = View.VISIBLE
        binding.progressBar.progress = 100
        binding.progressBar.progressTintList = ColorStateList.valueOf(Color.parseColor("#72BD6C"))
        binding.ivProgressComplete.visibility = View.VISIBLE
        binding.btnAnother.visibility = View.VISIBLE
        binding.ivHelperInfo.visibility = View.VISIBLE
        binding.tvHelperText.visibility = View.VISIBLE
        binding.tvprogress.visibility = View.GONE
        binding.ivProgressComplete.setImageResource(R.drawable.ic_green_right)
        binding.tvDownload.text = "Success"
        binding.ivHelperInfo.setBackgroundResource(R.drawable.ic_star)
        binding.tvHelperText.text = "MP3 successfully saved into selected folder"
    }

}
