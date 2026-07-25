package com.example.testapp.camera

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.testapp.data.CameraSettings
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

class FaceAnalyzer(
    private val settings: CameraSettings,
    private val onAnalysisResult: (FaceResult) -> Unit
) : ImageAnalysis.Analyzer {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .build()
    )

    data class FaceResult(
        val faceCount: Int,
        val guidanceMsg: String,
        val isCentered: Boolean,
        val faceRatio: Float,
        val distanceToCenter: Float
    )

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)
            detector.process(image)
                .addOnSuccessListener { faces ->
                    processFaces(faces, imageProxy.width, imageProxy.height, rotationDegrees)
                }
                .addOnFailureListener {
                    onAnalysisResult(FaceResult(0, "Analysis failed.", false, 0f, 1000f))
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun processFaces(faces: List<Face>, width: Int, height: Int, rotationDegrees: Int) {
        val faceCount = faces.size
        if (faceCount == 0) {
            onAnalysisResult(FaceResult(0, "No face detected.", false, 0f, 1000f))
            return
        }

        val face = faces[0]
        val rect = face.boundingBox
        val fx = rect.exactCenterX()
        val fy = rect.exactCenterY()

        val cx = width / 2.0f
        val cy = height / 2.0f

        val dx = fx - cx
        val dy = fy - cy

        // Map to screen coordinates based on rotation and camera facing direction
        val screenX: Float
        val screenY: Float

        val isFront = settings.isFrontCamera

        if (rotationDegrees == 90 || rotationDegrees == 270) {
            // Portrait mode: sensor coordinates are rotated 90 or 270 degrees
            if (isFront) {
                screenX = -dy
                screenY = -dx
            } else {
                screenX = -dy
                screenY = dx
            }
        } else {
            // Landscape mode
            if (isFront) {
                screenX = dx
                screenY = dy
            } else {
                screenX = dx
                screenY = dy
            }
        }

        // Normalize coordinates relative to short edge (for device scaling)
        val shortEdge = if (rotationDegrees % 180 == 90) height.toFloat() else width.toFloat()
        
        // Map to a range corresponding to Lua settings (-1000 to 1000)
        // Lua normalized with 2000.0, so: screenNormalized = screenVal / imageShortEdge * 2000.0
        val scaleFactor = 2000.0f / shortEdge
        val mappedX = screenX * scaleFactor
        val mappedY = screenY * scaleFactor

        // Sensitivity threshold in Lua: Strict = 70, Normal = 100, Loose = 150
        val thresh = when (settings.sensitivity) {
            70 -> 70f
            150 -> 150f
            else -> 100f
        }

        val isCenteredX = mappedX >= -thresh && mappedX <= thresh
        val isCenteredY = mappedY >= -thresh && mappedY <= thresh

        // Face ratio: width of face relative to sensor width
        val faceWidth = rect.width().toFloat()
        val faceRatio = faceWidth / shortEdge

        var isFramingPerfect = true
        var framingMsg = ""

        when (settings.framingMode) {
            "Close-up Selfie" -> {
                if (faceRatio < 0.35f) {
                    isFramingPerfect = false
                    framingMsg = "Move closer. "
                }
            }
            "Portrait" -> {
                if (faceRatio < 0.20f) {
                    isFramingPerfect = false
                    framingMsg = "Move closer. "
                } else if (faceRatio > 0.45f) {
                    isFramingPerfect = false
                    framingMsg = "Move further back. "
                }
            }
            "Chest-Up Shot" -> {
                if (faceRatio < 0.12f) {
                    isFramingPerfect = false
                    framingMsg = "Move closer. "
                } else if (faceRatio > 0.25f) {
                    isFramingPerfect = false
                    framingMsg = "Move further back. "
                }
            }
            "Half Body" -> {
                if (faceRatio < 0.08f) {
                    isFramingPerfect = false
                    framingMsg = "Move closer. "
                } else if (faceRatio > 0.15f) {
                    isFramingPerfect = false
                    framingMsg = "Move further back. "
                }
            }
            "Full Body" -> {
                if (faceRatio > 0.10f) {
                    isFramingPerfect = false
                    framingMsg = "Move further back. "
                }
            }
        }

        val isCentered = isCenteredX && isCenteredY && isFramingPerfect

        val distance = Math.sqrt((mappedX * mappedX + mappedY * mappedY).toDouble()).toFloat()

        var vertMsg = ""
        var horizMsg = ""

        val prefixVert = if (settings.intensity == "High") "Face is off center vertically. " else ""
        val prefixHoriz = if (settings.intensity == "High") "Face is off center horizontally. " else ""

        if (mappedY < -thresh) {
            vertMsg = "${prefixVert}Tilt up. "
        } else if (mappedY > thresh) {
            vertMsg = "${prefixVert}Tilt down. "
        }

        if (mappedX < -thresh) {
            horizMsg = "${prefixHoriz}Move left. "
        } else if (mappedX > thresh) {
            horizMsg = "${prefixHoriz}Move right. "
        }

        var finalMessage = "$framingMsg$vertMsg$horizMsg"

        if (settings.intensity == "Low") {
            finalMessage = when {
                framingMsg.isNotEmpty() -> framingMsg
                vertMsg.isNotEmpty() -> "Up or down."
                else -> "Left or right."
            }
        }

        if (isCentered) {
            finalMessage = if (settings.autoCapture == "Off") {
                "Perfect! Tap screen to capture."
            } else {
                "Perfect!"
            }
        }

        onAnalysisResult(
            FaceResult(
                faceCount = faceCount,
                guidanceMsg = finalMessage.trim(),
                isCentered = isCentered,
                faceRatio = faceRatio,
                distanceToCenter = distance
            )
        )
    }
}
