package com.example.detector



import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.ToneGenerator
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

@Composable
fun FrameExtractorUI() {
    val context = LocalContext.current
    var output by remember { mutableStateOf("") }
    val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
    val bgColor by SocketManager.serverColor
    LaunchedEffect(bgColor) {
        if (bgColor == Color.Red) {

            toneGenerator.startTone(ToneGenerator.TONE_DTMF_0, 3000)

        }
    }
    if (bgColor == Color.White) {

        toneGenerator.release()

    }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            output = "Processing video..."
            extractFramesAndEncode(context, it) { result ->
                output = result
            }
        } ?: run {
            output = "No file selected"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    )

    {
        if (bgColor == Color.Red) {
            Text(
                text = "Alert: Distraction Detected!!!",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(onClick = { filePickerLauncher.launch("video/*") }) {
            Text("Select Video")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = output, modifier = Modifier.fillMaxWidth())
    }
}


fun extractFramesAndEncode(context: Context, videoUri: Uri, callback: (String) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, videoUri)

        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val durationUs = durationMs * 1000L

        val frameRate = 30
        val frameIntervalUs = 1_000_000L / frameRate

        val base64Frames = mutableListOf<String>()
        var frameCount = 0

        for (timeUs in 0 until durationUs step frameIntervalUs) {
            val bitmap: Bitmap? = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            if (bitmap != null) {
                val encoded = bitmapToBase64(bitmap)
                base64Frames.add(encoded)
                SocketManager.sendMessage(encoded)
                frameCount++
            }
        }
        retriever.release()

        withContext(Dispatchers.Main) {
            callback("Extracted $frameCount frames and sent them")

        }
    }
}



@Composable
fun CameraPreviewWithCapture() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    var bitmap1 by remember { mutableStateOf<Bitmap?>(null) }
    var bitmap2 by remember { mutableStateOf<Bitmap?>(null) }
    val bgColor by SocketManager.serverColor
    val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 50)

    Box(modifier = Modifier
        .fillMaxSize()
        .background(bgColor)) {

        AndroidView({ previewView })

        LaunchedEffect(bgColor) {
            if (bgColor == Color.Red) {
                toneGenerator.startTone(ToneGenerator.TONE_DTMF_0, 2000)
            }
        }

        if (bgColor == Color.White) {
            toneGenerator.release()
        }

        LaunchedEffect(Unit) {
            val cameraProvider = ProcessCameraProvider.getInstance(context).get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )

            while (true) {
                delay(500)

                repeat(2) { index ->
                    val file = File.createTempFile("temp", ".jpg")
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
                    imageCapture.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                                val resized = Bitmap.createScaledBitmap(bitmap, 640, 640, true)
                                val base64 = bitmapToBase64(resized)
                                SocketManager.sendMessage(base64)

                                if (index == 0) bitmap1 = resized
                                else bitmap2 = resized
                            }

                            override fun onError(exception: ImageCaptureException) {
                            }
                        }
                    )
                    delay(500)
                }
            }
        }

        Column {
            bitmap1?.let {
                Image(bitmap = it.asImageBitmap(), contentDescription = "Photo 1")
            }
            bitmap2?.let {
                Image(bitmap = it.asImageBitmap(), contentDescription = "Photo 2")
            }
        }
    }
}


fun bitmapToBase64(bitmap: Bitmap): String {
    val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
    val outputStream = ByteArrayOutputStream()
    resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
    val byteArray = outputStream.toByteArray()
    return Base64.encodeToString(byteArray, Base64.DEFAULT)
}


@Composable
fun ImagePickerBase64Demo() {
    val context = LocalContext.current
    var base64Image by remember { mutableStateOf<String?>(null) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val resp by SocketManager.resString

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            imageUri = selectedUri
            SocketManager.resString.value = "Processing "
            base64Image = null

            try {
                val inputStream = context.contentResolver.openInputStream(selectedUri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                if (bytes != null) {
                    base64Image = Base64.encodeToString(bytes, Base64.DEFAULT)
                } else {
                    SocketManager.resString.value = "Failed to read"
                }
            } catch (e: Exception) {
                SocketManager.resString.value = "Error  ${e.message}"
            }
        } ?: run {
            //todo
        }
    }

    LaunchedEffect(base64Image) {
        base64Image?.let { validBase64 ->
            SocketManager.resString.value = "Sending To Server..."
            SocketManager.sendImg(validBase64)
        }
    }

    Column(
        modifier = Modifier
            .padding(20.dp)
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = {
            launcher.launch("image/*")
        }) {
            Text("Select Image")
        }

        Text(resp)

        imageUri?.let {
            Image(
                painter = rememberAsyncImagePainter(it),
                contentDescription = "Selected Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        }
    }
}


@Composable
fun DemoVMode(){
    FrameExtractorUI()
    SocketManager.connect(Constants.token.toString())
    SocketManager.authenticate(Constants.token.toString())


}

@Composable
fun DemoIMode(){

    ImagePickerBase64Demo()
    SocketManager.resString.value="";
    SocketManager.connect(Constants.token.toString())
    SocketManager.authenticate(Constants.token.toString())

}



@Composable
fun Live(){
    CameraPreviewWithCapture()
    SocketManager.connect(Constants.token.toString())
    SocketManager.authenticate(Constants.token.toString())
}