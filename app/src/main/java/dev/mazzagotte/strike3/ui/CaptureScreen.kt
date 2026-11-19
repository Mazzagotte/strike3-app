package dev.mazzagotte.strike3.ui

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.mazzagotte.strike3.render.FrameCrop
import dev.mazzagotte.strike3.util.Media
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

@Composable
fun CaptureScreen(onPhoto: (Uri, FrameCrop?) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var hasCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPhoto(uri, null)
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
private fun CameraWithGuide(onPhoto: (Uri, FrameCrop?) -> Unit) {
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
    var camera by remember { mutableStateOf<Camera?>(null) }
    var focusAt by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(focusAt) {
        if (focusAt != null) {
            delay(1200)
            focusAt = null
        }
    }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            provider.unbindAll()
            try {
                camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            } catch (e: Exception) {
                error = "Camera unavailable: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            if (future.isDone) future.get().unbindAll()
        }
    }

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            // Tap to focus (and set exposure) on that spot
            detectTapGestures { pos ->
                val point = previewView.meteringPointFactory.createPoint(pos.x, pos.y)
                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                    .setAutoCancelDuration(3, TimeUnit.SECONDS)
                    .build()
                camera?.cameraControl?.startFocusAndMetering(action)
                focusAt = pos
            }
        },
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        CardGuide()
        focusAt?.let { pos ->
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color.White, radius = 34.dp.toPx(), center = pos, style = Stroke(width = 2.dp.toPx()))
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().systemBarsPadding().padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                error ?: "Line the card up inside the frame. Tap to focus.",
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
                                    val uri = result.savedUri
                                    if (uri == null) {
                                        error = "Photo wasn't saved, try again."
                                    } else {
                                        val w = previewView.width
                                        val h = previewView.height
                                        val g = guideRect(w.toFloat(), h.toFloat())
                                        onPhoto(uri, FrameCrop(w, h, g.left, g.top, g.right, g.bottom))
                                    }
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

/** The card-in-holder shaped guide frame, shared by the overlay and the crop. */
private fun guideRect(width: Float, height: Float): Rect {
    val w = width * 0.72f
    val h = w / 0.68f
    val left = (width - w) / 2f
    val top = (height - h) / 2f - height * 0.04f
    return Rect(left, top, left + w, top + h)
}

/** Dims everything outside a card-in-holder shaped frame. */
@Composable
private fun CardGuide() {
    Canvas(Modifier.fillMaxSize()) {
        val g = guideRect(size.width, size.height)
        val w = g.width
        val h = g.height
        val left = g.left
        val top = g.top
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
