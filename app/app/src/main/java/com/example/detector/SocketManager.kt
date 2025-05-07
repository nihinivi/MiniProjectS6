package com.example.detector

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import com.example.detector.Constants.SERVER_URL
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

object SocketManager {
    private var socket: Socket? = null
    var serverColor: MutableState<Color> = mutableStateOf(Color.White)
    var resString: MutableState<String> = mutableStateOf("")

    fun connect(token: String) {
        try {
            val options = IO.Options()
            options.query = "token=$token"
            options.transports = arrayOf("websocket")
            socket = IO.socket(SERVER_URL, options)


            socket?.connect()

            socket?.on(Socket.EVENT_CONNECT) {
                println("   Connected to Server!")
            }

            socket?.on("auth_success") { args ->
                println("  Authenticated: ${args[0]}")
            }

            socket?.on("server_response") { args ->
                if (args.isNotEmpty()) {
                    val newColor = if (args[0] == "1") Color.Red else Color.White

                    serverColor.value = newColor


                }

            }
            socket?.on("img_response") { args ->
                if (args.isNotEmpty()) {
                    resString.value = args[0].toString()
                        println(args[0])
                }
            }
            socket?.on(Socket.EVENT_DISCONNECT) {
                println("Disconnected from server")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

     fun authenticate(token: String) {
        val authData = JSONObject().apply {
            put("token", token)
        }
        socket?.emit("auth", authData)
    }

    fun sendMessage(message: String) {

        socket?.emit("data", message)
    }
    fun sendImg(img: String) {
        socket?.emit("image", img)
    }

    fun disconnect() {
        socket?.disconnect()
    }
}
