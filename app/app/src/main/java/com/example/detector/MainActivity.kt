package com.example.detector

import LoginScreen
import android.annotation.SuppressLint
import android.os.Bundle
import android.os.StrictMode
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    @SuppressLint("CoroutineCreationDuringComposition")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .penaltyLog()
                .build()
        )
        setContent {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "main",
                builder = {
                    composable("main") {
                        LoginScreen(navController)
                    }
                    composable("Menu") {
                                PModeSelectionScreen(navController)
                            }
                    composable("DemoVid") {
                        DemoVMode()
                    }
                    composable("DemoImg") {
                        DemoIMode()
                    }
                    composable("Live") {
                        Live()
                    }
                }
            )
        }
    }
}
