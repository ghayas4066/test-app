package com.example.testapp.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import androidx.core.content.ContextCompat

object UpdateChecker {

    // IMPORTANT: Change these to your actual GitHub username and repository name!
    private const val GITHUB_USERNAME = "ghayas4066"
    private const val GITHUB_REPO = "test-app"
    
    private const val GITHUB_API_URL = "https://api.github.com/repos/$GITHUB_USERNAME/$GITHUB_REPO/releases/latest"

    fun checkForUpdate(context: Context, currentVersionCode: Int, showToastIfUpToDate: Boolean = false) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(GITHUB_API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    
                    val tagStr = json.getString("tag_name").replace(Regex("[^0-9]"), "")
                    val latestVersionCode = if (tagStr.isNotEmpty()) tagStr.toInt() else 0
                    
                    val assets = json.getJSONArray("assets")
                    if (assets.length() > 0) {
                        val asset = assets.getJSONObject(0)
                        val downloadUrl = asset.getString("browser_download_url")
                        
                        withContext(Dispatchers.Main) {
                            if (latestVersionCode > currentVersionCode || (showToastIfUpToDate && latestVersionCode > 0)) {
                                Toast.makeText(context, "New update found! Downloading...", Toast.LENGTH_LONG).show()
                                downloadAndInstallUpdate(context, downloadUrl)
                            } else if (showToastIfUpToDate) {
                                Toast.makeText(context, "App is already up to date.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else if (showToastIfUpToDate) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "No APK found in the latest release.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else if (showToastIfUpToDate) {
                    withContext(Dispatchers.Main) {
                        if (connection.responseCode == 404) {
                            Toast.makeText(context, "Update Server not setup yet (404). Please push code to GitHub first!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Failed to check for updates. Server response: ${connection.responseCode}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("UpdateChecker", "Error checking for updates", e)
                if (showToastIfUpToDate) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error checking for updates: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun downloadAndInstallUpdate(context: Context, downloadUrl: String) {
        val destination = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "update.apk")
        if (destination.exists()) {
            destination.delete()
        }

        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("Downloading Update")
            .setDescription("Please wait while the new version is downloaded")
            .setDestinationUri(Uri.fromFile(destination))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    installApk(context, destination)
                    context.unregisterReceiver(this)
                }
            }
        }
        
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to launch installer", Toast.LENGTH_LONG).show()
        }
    }
}
