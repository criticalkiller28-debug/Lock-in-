package com.rat.focuslock

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.util.Calendar

class PhotoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Camera() } }
    }

    @Composable
    fun Camera() {
        val ctx = LocalContext.current
        val owner = LocalLifecycleOwner.current
        val prefs = ctx.getSharedPreferences("focus", Context.MODE_PRIVATE)
        val capture = remember { ImageCapture.Builder().build() }
        var status by remember { mutableStateOf("Point at your task, then tap the button.") }

        // Which block are we proving? The active one, or the one awaiting its photo.
        val now = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
        val awaiting = prefs.getString("awaiting_photo", "") ?: ""
        val block = Schedule.activeBlock(now)?.takeIf { it.photoTarget.isNotEmpty() }
            ?: Schedule.blocks.firstOrNull { it.name == awaiting }

        Column(Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { c ->
                    val view = PreviewView(c)
                    val future = ProcessCameraProvider.getInstance(c)
                    future.addListener({
                        val provider = future.get()
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                        provider.unbindAll()
                        provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                    }, ContextCompat.getMainExecutor(c))
                    view
                }
            )
            Text(status, Modifier.padding(16.dp))
            Button(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                onClick = {
                    if (block == null) { status = "No task needs a photo right now."; return@Button }
                    capture.takePicture(
                        ContextCompat.getMainExecutor(ctx),
                        object : ImageCapture.OnImageCapturedCallback() {
                            @androidx.camera.core.ExperimentalGetImage
                            override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                                val media = image.image
                                if (media == null) { image.close(); status = "Camera error, try again."; return }
                                val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
                                val labeler = ImageLabeling.getClient(
                                    ImageLabelerOptions.Builder().setConfidenceThreshold(0.55f).build()
                                )
                                labeler.process(input)
                                    .addOnSuccessListener { labels ->
                                        val found = labels.map { it.text.lowercase() }
                                        val wanted = block.photoTarget.map { it.lowercase() }
                                        val hit = found.any { f -> wanted.any { w -> f.contains(w) || w.contains(f) } }
                                        if (hit) {
                                            prefs.edit()
                                                .putString("photo_done_block", block.name)
                                                .putString("awaiting_photo", "")
                                                .apply()
                                            Toast.makeText(ctx, "Verified. Nice work.", Toast.LENGTH_SHORT).show()
                                            // If the block already ended, stop the watcher
                                            if (Schedule.activeBlock(now) == null) {
                                                ctx.stopService(android.content.Intent(ctx, WatcherService::class.java))
                                            }
                                            finish()
                                        } else {
                                            status = "Saw: ${found.take(4).joinToString()}. Try the ${block.photoTarget.first().lowercase()} again, closer and brighter."
                                        }
                                        image.close()
                                    }
                                    .addOnFailureListener { status = "Check failed, try again."; image.close() }
                            }
                            override fun onError(e: ImageCaptureException) { status = "Camera error, try again." }
                        }
                    )
                }
            ) { Text("Take photo") }
        }
    }
}
