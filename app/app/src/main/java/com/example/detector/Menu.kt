package com.example.detector

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
@Composable
fun ModeSelection(onModeSelected: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F2F5))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Select Mode",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1C1C1E),
            modifier = Modifier.padding(vertical = 16.dp)
        )

        // Grid layout
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                ModeCard(
                    title = "Live",
                    description = "Use the main camera",
                    iconRes = android.R.drawable.presence_online,
                    onClick = { onModeSelected("Live") }
                )
            }
            item {
                ModeCard(
                    title = "Demo Video",
                    description = "Simulated video mode",
                    iconRes = android.R.drawable.presence_invisible,
                    onClick = { onModeSelected("DemoVid") }
                )
            }
            item {
                ModeCard(
                    title = "Demo Image",
                    description = "Simulated image mode",
                    iconRes = android.R.drawable.presence_invisible,
                    onClick = { onModeSelected("DemoImg") }
                )
            }
            }
        }

}
@Composable
fun ModeCard(title: String, description: String, iconRes: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                tint = Color(0xFF3F51B5),
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.Black
            )
            Text(
                text = description,
                fontSize = 13.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PModeSelectionScreen(navController: NavController) {
    ModeSelection(onModeSelected = { mode ->
        if (mode == "Live") {
            println("live mode")
        }
        when(mode){
            "Live" -> navController.navigate("Live")
            "DemoVid" -> navController.navigate("DemoVid")
            "DemoImg" -> navController.navigate("DemoImg")
            else -> println("Error")
        }
    })
}
