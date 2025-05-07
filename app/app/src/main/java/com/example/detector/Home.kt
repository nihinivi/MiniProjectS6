
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.detector.Constants
import com.example.detector.Constants.SERVER_URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject

@Composable
fun LoginScreen(navController: NavController){
  Greeting(navController)
}
@Composable
fun Greeting( navController: NavController,modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(color = MaterialTheme.colorScheme.background)
    ) {
        InputFields(navController)
        WavyBackground()
    }
}


@Composable
fun WavyBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawWave(
            color = Color(0xFF6200EE),
            startY = size.height * 0.7f,
            waveHeight = 200f
        )
    }
}

fun DrawScope.drawWave(color: Color, startY: Float, waveHeight: Float) {
    val path = Path().apply {
        moveTo(0f, startY)
        quadraticBezierTo(
            size.width * 0.25f, startY - waveHeight,
            size.width * 0.5f, startY
        )
        quadraticBezierTo(
            size.width * 0.75f, startY + waveHeight,
            size.width, startY
        )
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(path = path, color = color)
}


fun login(uname: String,pass: String): String? {
    val client = OkHttpClient()
    val serverUrl = SERVER_URL
    val json = JSONObject().apply {
        put("username", uname)
        put("password", pass)
    }
    val mediaType = "application/json; charset=utf-8".toMediaType()
    val body = RequestBody.create(mediaType, json.toString())

    val request = Request.Builder()
        .url("$serverUrl/login")
        .post(body)
        .build()

    client.newCall(request).execute().use { response ->
        return if (response.isSuccessful) {
            val responseBody = response.body?.string() ?: return null
            val token = JSONObject(responseBody).getString("token")
            println("  Received $token")
            token
        } else {
            println("  Authentication failed ${response.code}")
            null
        }
    }
}

@Composable
fun InputFields(navController: NavController) {
    var uname by remember{mutableStateOf("")}
    var pass by remember{ mutableStateOf ("") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = uname,
            onValueChange = { uname=it},
            label = { Text("Username") },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(fontSize = 16.sp),
            )
        OutlinedTextField(
            value = pass,
            onValueChange = {pass=it},
            label = { Text("Password") },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(fontSize = 16.sp),
            visualTransformation = PasswordVisualTransformation(),

        )

        Button(
            onClick = {
                var loading = true
                CoroutineScope(Dispatchers.IO).launch {
                    val token =  login(uname, pass)

                    if (token != null) {
                        withContext(Dispatchers.Main) {
                            Constants.token=token
                            navController.navigate("Menu")
                        }

                    } else {
                        loading = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Login", color = Color.White)
        }
    }
}