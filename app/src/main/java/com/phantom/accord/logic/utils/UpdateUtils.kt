package com.phantom.accord.logic.utils

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import com.phantom.accord.BuildConfig
import com.phantom.accord.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object UpdateUtils {

    private const val GITHUB_API_URL = "https://api.github.com/repos/PhantomCodeGhost/Accord/releases/latest"

    fun checkForUpdates(context: Context, showToast: Boolean = false) {
        if (showToast) {
            Toast.makeText(context, "Checking for updates...", Toast.LENGTH_SHORT).show()
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(GITHUB_API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()

                    val jsonObject = JSONObject(response.toString())
                    val tagName = jsonObject.getString("tag_name")
                    val assets = jsonObject.getJSONArray("assets")
                    
                    var downloadUrl = ""
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.getString("name")
                        if (name.endsWith(".apk")) {
                            downloadUrl = asset.getString("browser_download_url")
                            break
                        }
                    }

                    withContext(Dispatchers.Main) {
                        // Very simple version check logic for demonstration
                        val currentVersion = BuildConfig.VERSION_NAME
                        val currentVersionClean = currentVersion.replace(Regex("[^0-9.]"), "")
                        val newVersionClean = tagName.replace(Regex("[^0-9.]"), "")
                        
                        if (newVersionClean != currentVersionClean && downloadUrl.isNotEmpty()) {
                            showUpdateDialog(context, tagName, downloadUrl)
                        } else {
                            if (showToast) {
                                Toast.makeText(context, "You are on the latest version.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        if (showToast) {
                            Toast.makeText(context, "Failed to check for updates.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    if (showToast) {
                        Toast.makeText(context, "Error checking updates.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showUpdateDialog(context: Context, newVersion: String, downloadUrl: String) {
        AlertDialog.Builder(context)
            .setTitle("Update Available")
            .setMessage("Version $newVersion is available. Do you want to download and install it?")
            .setPositiveButton("Download") { _, _ ->
                downloadAndInstallUpdate(context, downloadUrl)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun downloadAndInstallUpdate(context: Context, downloadUrl: String) {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val uri = Uri.parse(downloadUrl)
            val request = DownloadManager.Request(uri)
            request.setTitle("Accord Update")
            request.setDescription("Downloading latest APK")
            request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "Accord_update.apk")
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            
            val downloadId = downloadManager.enqueue(request)
            Toast.makeText(context, "Downloading update...", Toast.LENGTH_SHORT).show()
            
            val onComplete = object : android.content.BroadcastReceiver() {
                override fun onReceive(ctxt: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        try {
                            val downloadUri = downloadManager.getUriForDownloadedFile(downloadId)
                            if (downloadUri != null) {
                                val installIntent = Intent(Intent.ACTION_VIEW)
                                installIntent.setDataAndType(downloadUri, "application/vnd.android.package-archive")
                                installIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                ctxt.startActivity(installIntent)
                            } else {
                                Toast.makeText(ctxt, "Download failed.", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(ctxt, "Failed to start installation.", Toast.LENGTH_SHORT).show()
                        }
                        ctxt.unregisterReceiver(this)
                    }
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.applicationContext.registerReceiver(
                    onComplete,
                    android.content.IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    android.content.Context.RECEIVER_EXPORTED
                )
            } else {
                context.applicationContext.registerReceiver(
                    onComplete,
                    android.content.IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Download failed to start.", Toast.LENGTH_SHORT).show()
        }
    }
}
