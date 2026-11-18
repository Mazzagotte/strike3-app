package dev.mazzagotte.strike3.ui

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.mazzagotte.strike3.util.Media

@Composable
fun CaptureScreen(onPhoto: (Uri) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var hasCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPhoto(uri)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCamera) {
            CameraWithGuide(onPhoto = onPhoto)
        } else {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("The camera is used to shoot your cards.", textAlign = TextAlign.Center)
                Spacer(Modifier.size(16.dp))
                Button(onClick = { askCamera.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
            }
        }

        Row(
            Modifier.fillMaxWidth().systemBarsPadding().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                border = BorderStroke(1.dp, Color.White.copy(alpha = .4f)),
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = Color.White)
                Spacer(Modifier.size(8.dp))
                Text("Gallery", color = Color.White)
            }
        }
    }
}

@Composable
private fun CameraWithGuide(onPhoto: (Uri) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setFlashMode(ImageCapture.FLASH_MODE_OFF)
            .build()
    }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            provider.unbindAll()
            try {
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            } catch (e: Exception) {
                error = "Camera unavailable: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            if (future.isDone) future.get().unbindAll()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        CardGuide()

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().systemBarsPadding().padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                error ?: "Line the card up inside the frame. Black backdrop, flash off.",
                color = Color.White, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
            )
            Box(
                Modifier
                    .size(78.dp)
                    .border(4.dp, Gold, CircleShape)
                    .padding(8.dp)
                    .background(if (busy) Color.Gray else Color.White, CircleShape),
            ) {
                IconButton(
                    onClick = {
                        if (busy) return@IconButton
                        busy = true
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, "card-${Media.stamp()}.jpg")
                            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            put(MediaStore.Images.Media.RELATIVE_PATH, Media.ORIGINALS_DIR)
                        }
                        val output = ImageCapture.OutputFileOptions.Builder(
                            context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values,
                        ).build()
                        imageCapture.takePicture(
                            output, ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                    busy = false
                                    result.savedUri?.let(onPhoto) ?: run { error = "Photo wasn't saved, try again." }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    busy = false
                                    error = "Couldn't take the photo: ${exception.message}"
                                }
                            },
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {}
            }
        }
    }
}

/** Dims everything outside a card-in-holder shaped frame. */
@Composable
private fun CardGuide() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width * 0.72f
        val h = w / 0.68f
        val left = (size.width - w) / 2f
        val top = (size.height - h) / 2f - size.height * 0.04f
        val radius = CornerRadius(w * 0.05f)
        val frame = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(left, top, left + w, top + h, radius))
        }
        clipPath(frame, clipOp = ClipOp.Difference) {
            drawRect(Color.Black.copy(alpha = 0.55f))
        }
        drawRoundRect(Gold, Offset(left, top), Size(w, h), radius, style = Stroke(width = 3.dp.toPx()))
        // Center tick marks to help keep the card straight
        val cx = size.width / 2f
        drawLine(Gold.copy(alpha = .7f), Offset(cx, top - 14.dp.toPx()), Offset(cx, top + 10.dp.toPx()), 2.dp.toPx())
        drawLine(Gold.copy(alpha = .7f), Offset(cx, top + h - 10.dp.toPx()), Offset(cx, top + h + 14.dp.toPx()), 2.dp.toPx())
    }
}
