package com.example.testapp.data

import android.content.Context

data class CameraSettings(
    var isFrontCamera: Boolean = true,
    var mode: String = "Photo", // "Photo" or "Video"
    var framingMode: String = "Portrait", // "Portrait", "Close-up Selfie", "Chest-Up Shot", "Half Body", "Full Body"
    var sensitivity: Int = 100, // 70 = strict, 100 = normal, 150 = loose
    var intensity: String = "Medium", // "Low", "Medium", "High"
    var promptDelay: Long = 1500L,
    var timerDelay: Long = 0L, // 0 = Instant, 3000 = 3s, 5000 = 5s
    var quality: String = "High", // "High", "Medium", "Low"
    var ratio: String = "4:3", // "4:3", "16:9"
    var autoSave: String = "Off", // "On", "Off"
    var autoCapture: String = "On", // "On", "Off"
    var lightGuide: String = "Off", // "On", "Off"
    var voiceGuide: String = "On", // "On", "Off"
    var saveLocation: String = "Off", // "On", "Off"
    var vibration: String = "Medium", // "Off", "Low", "Medium", "High"
    var speechRate: Float = 1.0f,
    var speechPitch: Float = 1.0f
) {
    companion object {
        private const val PREFS_NAME = "camera_settings"

        fun load(context: Context): CameraSettings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return CameraSettings(
                isFrontCamera = prefs.getBoolean("isFrontCamera", true),
                mode = prefs.getString("mode", "Photo") ?: "Photo",
                framingMode = prefs.getString("framingMode", "Portrait") ?: "Portrait",
                sensitivity = prefs.getInt("sensitivity", 100),
                intensity = prefs.getString("intensity", "Medium") ?: "Medium",
                promptDelay = prefs.getLong("promptDelay", 1500L),
                timerDelay = prefs.getLong("timerDelay", 0L),
                quality = prefs.getString("quality", "High") ?: "High",
                ratio = prefs.getString("ratio", "4:3") ?: "4:3",
                autoSave = prefs.getString("autoSave", "Off") ?: "Off",
                autoCapture = prefs.getString("autoCapture", "On") ?: "On",
                lightGuide = prefs.getString("lightGuide", "Off") ?: "Off",
                voiceGuide = prefs.getString("voiceGuide", "On") ?: "On",
                saveLocation = prefs.getString("saveLocation", "Off") ?: "Off",
                vibration = prefs.getString("vibration", "Medium") ?: "Medium",
                speechRate = prefs.getFloat("speechRate", 1.0f),
                speechPitch = prefs.getFloat("speechPitch", 1.0f)
            )
        }
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("isFrontCamera", isFrontCamera)
            putString("mode", mode)
            putString("framingMode", framingMode)
            putInt("sensitivity", sensitivity)
            putString("intensity", intensity)
            putLong("promptDelay", promptDelay)
            putLong("timerDelay", timerDelay)
            putString("quality", quality)
            putString("ratio", ratio)
            putString("autoSave", autoSave)
            putString("autoCapture", autoCapture)
            putString("lightGuide", lightGuide)
            putString("voiceGuide", voiceGuide)
            putString("saveLocation", saveLocation)
            putString("vibration", vibration)
            putFloat("speechRate", speechRate)
            putFloat("speechPitch", speechPitch)
            apply()
        }
    }
}
