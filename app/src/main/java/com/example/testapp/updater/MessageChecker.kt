package com.example.testapp.updater

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object MessageChecker {

    private const val MESSAGE_JSON_URL = "https://raw.githubusercontent.com/ghayas4066/test-app/main/server_message.json"
    private const val PREFS_NAME = "ServerMessagePrefs"
    private const val LAST_MESSAGE_ID = "LastMessageId"

    fun checkForMessage(context: Context, onNewMessage: (String, String, Int) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(MESSAGE_JSON_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    
                    val id = json.getInt("id")
                    val title = json.getString("title")
                    val message = json.getString("message")

                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val lastId = prefs.getInt(LAST_MESSAGE_ID, 0)

                    if (id > lastId) {
                        withContext(Dispatchers.Main) {
                            onNewMessage(title, message, id)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("MessageChecker", "Error checking for messages", e)
            }
        }
    }

    fun markMessageAsRead(context: Context, id: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(LAST_MESSAGE_ID, id).apply()
    }
}
